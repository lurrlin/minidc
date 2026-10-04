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
      this.destination = this.context.createMediaStreamDestination();
      this.gain.connect(this.destination);
      this.sources = new Map();
      element.srcObject = this.destination.stream;
      element.volume = 1;
    }

    addTrack(track) {
      if (this.sources.has(track.id)) return;
      // A MediaStreamSource reads only one audio track: mix each track explicitly.
      const source = this.context.createMediaStreamSource(new MediaStream([track]));
      source.connect(this.gain);
      this.sources.set(track.id, source);
      track.addEventListener('ended', () => {
        source.disconnect();
        this.sources.delete(track.id);
      }, { once: true });
    }

    async setVolume(percent) {
      this.gain.gain.value = Math.max(0, Math.min(200, Number(percent))) / 100;
      await this.context.resume();
      await this.element.play();
    }

    close() {
      this.sources.forEach((source) => source.disconnect());
      this.sources.clear();
      this.element.srcObject = null;
      return this.context.close();
    }
  }

  window.CallMedia = { bindMediaChannels, RemoteAudioMixer };
})();
