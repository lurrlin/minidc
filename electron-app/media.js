(() => {
  const SCREEN_AUDIO_HEADER_BYTES = 12;
  const SCREEN_AUDIO_VERSION = 1;
  const SCREEN_AUDIO_CODEC_IMA = 1;
  const IMA_INDEX_TABLE = [-1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8];
  const IMA_STEP_TABLE = [
    7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45, 50, 55, 60, 66,
    73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190, 209, 230, 253, 279, 307, 337, 371, 408, 449,
    494, 544, 598, 658, 724, 796, 876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272,
    2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132, 7845, 8630, 9493, 10442,
    11487, 12635, 13899, 15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767,
  ];

  function decodeAdpcmScreenAudioPacket(rawData) {
    let view;
    if (rawData instanceof ArrayBuffer) {
      view = new DataView(rawData);
    } else if (ArrayBuffer.isView(rawData)) {
      view = new DataView(rawData.buffer, rawData.byteOffset, rawData.byteLength);
    } else {
      return null;
    }

    if (view.byteLength <= SCREEN_AUDIO_HEADER_BYTES) return null;
    const version = view.getUint8(0);
    const codecId = view.getUint8(1);
    const sampleRateDiv100 = view.getUint16(2, true);
    const sequence = view.getUint16(4, true);
    let predictor = view.getInt16(6, true);
    let stepIndex = Math.max(0, Math.min(88, view.getUint8(8)));
    const channels = view.getUint8(9);
    const sampleCount = view.getUint16(10, true);

    if (version !== SCREEN_AUDIO_VERSION || codecId !== SCREEN_AUDIO_CODEC_IMA || channels !== 1 || sampleCount <= 0) {
      return null;
    }
    const requiredBytes = SCREEN_AUDIO_HEADER_BYTES + ((sampleCount + 1) >> 1);
    if (view.byteLength < requiredBytes) return null;

    const sampleRate = Math.max(8000, Math.min(96000, sampleRateDiv100 * 100));
    const samples = new Float32Array(sampleCount);

    for (let i = 0; i < sampleCount; i += 1) {
      const encodedByte = view.getUint8(SCREEN_AUDIO_HEADER_BYTES + (i >> 1));
      const nibble = (i & 1) === 0 ? (encodedByte & 0x0f) : ((encodedByte >> 4) & 0x0f);
      const step = IMA_STEP_TABLE[stepIndex];
      let diff = step >> 3;
      if (nibble & 4) diff += step;
      if (nibble & 2) diff += step >> 1;
      if (nibble & 1) diff += step >> 2;

      if (nibble & 8) predictor -= diff;
      else predictor += diff;
      if (predictor > 32767) predictor = 32767;
      else if (predictor < -32768) predictor = -32768;

      stepIndex += IMA_INDEX_TABLE[nibble];
      if (stepIndex < 0) stepIndex = 0;
      else if (stepIndex > 88) stepIndex = 88;

      samples[i] = predictor / 32768;
    }

    return { sampleRate, sequence, samples };
  }

  async function bindMediaChannels(connection, { microphone, video = null, screenAudio = null }) {
    const transceivers = connection.getTransceivers();
    const audio = transceivers.filter((channel) => channel.receiver.track.kind === 'audio');
    const videoChannel = transceivers.find((channel) => channel.receiver.track.kind === 'video')
      || connection.addTransceiver('video', { direction: 'sendrecv' });
    const microphoneChannel = audio[0] || connection.addTransceiver('audio', { direction: 'sendrecv' });
    const screenAudioChannel = audio[1] || connection.addTransceiver('audio', { direction: 'sendrecv' });
    // The answerer must reuse the offered m-lines, even when the camera is off.
    for (const channel of [microphoneChannel, videoChannel, screenAudioChannel]) {
      channel.direction = 'sendrecv';
    }
    await Promise.all([
      microphoneChannel.sender.replaceTrack(microphone),
      videoChannel.sender.replaceTrack(video),
      screenAudioChannel.sender.replaceTrack(screenAudio),
    ]);
    return { microphoneSender: microphoneChannel.sender, videoTransceiver: videoChannel, screenAudioTransceiver: screenAudioChannel };
  }

  class RemoteAudioMixer {
    constructor(element) {
      this.element = element;
      this.context = new AudioContext();
      this.gain = this.context.createGain();
      this.channelGains = {
        voice: this.context.createGain(),
        screen: this.context.createGain(),
      };
      Object.values(this.channelGains).forEach((gain) => gain.connect(this.gain));
      this.volume = 100;
      this.deafened = false;
      this.destination = this.context.createMediaStreamDestination();
      this.gain.connect(this.destination);
      this.sources = new Map();
      this.inputs = new Map();
      this.screenAudioLastSeq = null;
      this.screenAudioNextTime = 0;
      this.screenAudioNodes = new Set();
      element.srcObject = this.destination.stream;
      element.volume = 1;
    }

    addTrack(track, channel = 'voice') {
      if (this.sources.has(track.id)) return;
      if (!this.channelGains[channel]) throw new Error('Canal de áudio inválido.');
      // A MediaStreamSource reads only one audio track: mix each track explicitly.
      const stream = new MediaStream([track]);
      // Chromium needs a playing media element to pull received WebRTC audio.
      // Keep the raw track silent; the mixer remains the only audible output.
      const input = document.createElement('audio');
      input.autoplay = true;
      input.muted = true;
      input.srcObject = stream;
      this.inputs.set(track.id, input);
      const source = this.context.createMediaStreamSource(stream);
      source.connect(this.channelGains[channel]);
      this.sources.set(track.id, source);
      track.addEventListener('ended', () => {
        source.disconnect();
        this.sources.delete(track.id);
        input.pause();
        input.srcObject = null;
        this.inputs.delete(track.id);
      }, { once: true });
    }

    async setVolume(percent) {
      this.volume = Math.max(0, Math.min(200, Number(percent)));
      this.gain.gain.value = this.deafened ? 0 : this.volume / 100;
      await this.context.resume();
      await Promise.all([...this.inputs.values()].map((input) => input.play()));
      await this.element.play();
    }

    setChannelVolume(channel, percent) {
      if (!this.channelGains[channel]) throw new Error('Canal de áudio inválido.');
      this.channelGains[channel].gain.value = Math.max(0, Math.min(200, Number(percent))) / 100;
    }

    setDeafened(value) {
      this.deafened = Boolean(value);
      this.element.muted = this.deafened;
      this.gain.gain.value = this.deafened ? 0 : this.volume / 100;
    }

    feedScreenAudioPacket(rawData) {
      if (this.context.state === 'closed') return false;
      const decoded = decodeAdpcmScreenAudioPacket(rawData);
      if (!decoded) return false;

      if (this.screenAudioLastSeq !== null) {
        const delta = (decoded.sequence - this.screenAudioLastSeq) & 0xffff;
        if (delta === 0 || delta > 0x8000) return false;
      }
      this.screenAudioLastSeq = decoded.sequence;

      const buffer = this.context.createBuffer(1, decoded.samples.length, decoded.sampleRate);
      buffer.copyToChannel(decoded.samples, 0);

      const now = this.context.currentTime;
      let startAt = this.screenAudioNextTime;
      if (startAt < now || startAt - now > 0.25) {
        startAt = now + 0.02;
      }

      const source = this.context.createBufferSource();
      source.buffer = buffer;
      source.connect(this.channelGains.screen);
      this.screenAudioNodes.add(source);
      source.onended = () => {
        source.disconnect();
        this.screenAudioNodes.delete(source);
      };
      source.start(startAt);
      this.screenAudioNextTime = startAt + buffer.duration;
      return true;
    }

    resetScreenAudio() {
      this.screenAudioNodes.forEach((source) => {
        try { source.stop(); } catch {}
        source.disconnect();
      });
      this.screenAudioNodes.clear();
      this.screenAudioLastSeq = null;
      this.screenAudioNextTime = 0;
    }

    close() {
      this.resetScreenAudio();
      this.sources.forEach((source) => source.disconnect());
      this.sources.clear();
      this.inputs.forEach((input) => { input.pause(); input.srcObject = null; });
      this.inputs.clear();
      Object.values(this.channelGains).forEach((gain) => gain.disconnect());
      this.gain.disconnect();
      this.element.srcObject = null;
      return this.context.close();
    }
  }

  window.CallMedia = { bindMediaChannels, RemoteAudioMixer, decodeAdpcmScreenAudioPacket };
})();
