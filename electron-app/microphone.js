(() => {
  const PROCESSING_KEYS = ['noiseSuppression', 'echoCancellation', 'autoGainControl'];

  function constraints(deviceId, requested, supported = navigator.mediaDevices.getSupportedConstraints()) {
    // "default" is a routing preference, not a stable hardware identifier.
    const audio = deviceId && deviceId !== 'default' ? { deviceId: { exact: deviceId } } : {};
    for (const key of PROCESSING_KEYS) {
      if (supported[key]) audio[key] = { exact: Boolean(requested[key]) };
    }
    return audio;
  }

  function verify(track, requested, supported = navigator.mediaDevices.getSupportedConstraints()) {
    const actual = track.getSettings();
    for (const key of PROCESSING_KEYS) {
      if (supported[key] && actual[key] !== Boolean(requested[key])) {
        throw new Error(`O microfone não confirmou a opção ${key}.`);
      }
    }
    return actual;
  }

  async function createGain(track, volume) {
    const context = new AudioContext();
    const source = context.createMediaStreamSource(new MediaStream([track]));
    const gain = context.createGain();
    const destination = context.createMediaStreamDestination();
    source.connect(gain);
    gain.connect(destination);
    gain.gain.value = Number(volume) / 100;
    await context.resume();
    return { context, gain, destination };
  }

  window.MicrophoneAudio = { PROCESSING_KEYS, constraints, verify, createGain };
})();
