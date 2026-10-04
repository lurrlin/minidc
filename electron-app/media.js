// Shared by the renderer and the real Chromium regression tests.
(() => {
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

    close() {
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

  window.CallMedia = { bindMediaChannels, RemoteAudioMixer };
})();
