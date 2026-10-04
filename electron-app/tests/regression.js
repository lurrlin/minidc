(async () => {
  const results = [];
  const assert = (value, message) => { if (!value) throw new Error(message); results.push(message); };
  const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
  const context = new AudioContext();
  await context.resume();
  const oscillator = context.createOscillator();
  const level = context.createGain();
  level.gain.value = 0.05;
  oscillator.connect(level);
  const microphone = context.createMediaStreamDestination();
  level.connect(microphone);
  oscillator.start();

  async function testPair() {
    const first = new RTCPeerConnection({ iceServers: [] });
    const second = new RTCPeerConnection({ iceServers: [] });
    const queued = [[], []];
    first.onicecandidate = ({ candidate }) => { if (candidate) queued[1].push(candidate); };
    second.onicecandidate = ({ candidate }) => { if (candidate) queued[0].push(candidate); };
    const options = { microphone: microphone.stream.getAudioTracks()[0] };
    const channelsA = await CallMedia.bindMediaChannels(first, options);
    await first.setLocalDescription(await first.createOffer());
    await second.setRemoteDescription(first.localDescription);
    const channelsB = await CallMedia.bindMediaChannels(second, options);
    await second.setLocalDescription(await second.createAnswer());
    await first.setRemoteDescription(second.localDescription);
    await wait(200);
    for (const candidate of queued[0]) await first.addIceCandidate(candidate);
    for (const candidate of queued[1]) await second.addIceCandidate(candidate);
    first.onicecandidate = ({ candidate }) => { if (candidate) second.addIceCandidate(candidate); };
    second.onicecandidate = ({ candidate }) => { if (candidate) first.addIceCandidate(candidate); };
    assert(first.getTransceivers().length === 3 && second.getTransceivers().length === 3, 'Both peers reuse exactly three media channels');
    assert([...first.getTransceivers(), ...second.getTransceivers()].every((t) => t.currentDirection === 'sendrecv'), 'All channels negotiate bidirectional media without a camera');
    const canvases = [document.createElement('canvas'), document.createElement('canvas')];
    const streams = canvases.map((canvas) => { canvas.width = 320; canvas.height = 180; return canvas.captureStream(15); });
    let frame = 0;
    const paint = setInterval(() => {
      canvases.forEach((canvas, i) => { const c = canvas.getContext('2d'); c.fillStyle = i ? '#5bc9b1' : '#7969ef'; c.fillRect(0, 0, 320, 180); c.fillStyle = 'white'; c.fillText(String(frame++), 40, 40); });
    }, 60);
    try {
      await channelsA.videoTransceiver.sender.replaceTrack(streams[0].getVideoTracks()[0]);
      await channelsB.videoTransceiver.sender.replaceTrack(streams[1].getVideoTracks()[0]);
      await channelsA.screenAudioTransceiver.sender.replaceTrack(options.microphone);
      await channelsB.screenAudioTransceiver.sender.replaceTrack(options.microphone);
      await wait(1800);
      for (const [index, peer] of [first, second].entries()) {
        const stats = [...(await peer.getStats()).values()];
        assert(stats.some((s) => s.type === 'inbound-rtp' && s.kind === 'video' && s.framesDecoded > 0), `P${index + 1} receives and decodes the other peer's screen`);
        assert(stats.filter((s) => s.type === 'inbound-rtp' && s.kind === 'audio' && s.bytesReceived > 0).length === 2, `P${index + 1} receives microphone and shared audio`);
      }
      await channelsB.videoTransceiver.sender.replaceTrack(null);
      await channelsB.videoTransceiver.sender.replaceTrack(streams[1].getVideoTracks()[0]);
      assert(second.signalingState === 'stable', 'Stop and restart sharing without renegotiation');
    } finally {
      clearInterval(paint);
      streams.forEach((stream) => stream.getTracks().forEach((track) => track.stop()));
      first.close(); second.close();
    }
  }

  await testPair();
  await testPair();
  results.push('A fresh call after teardown also passes');

  const audio = document.createElement('audio');
  document.body.append(audio);
  const mixer = new CallMedia.RemoteAudioMixer(audio);
  mixer.addTrack(microphone.stream.getAudioTracks()[0]);
  const analyser = mixer.context.createAnalyser();
  mixer.gain.connect(analyser);
  analyser.fftSize = 2048;
  const samples = new Float32Array(analyser.fftSize);
  async function rms(volume) {
    await mixer.setVolume(volume);
    const deadline = mixer.context.currentTime + 0.2;
    for (let attempt = 0; mixer.context.currentTime < deadline && attempt < 30; attempt++) await wait(30);
    let total = 0;
    for (let sample = 0; sample < 5; sample++) {
      await wait(35);
      analyser.getFloatTimeDomainData(samples);
      total += Math.sqrt(samples.reduce((sum, value) => sum + value * value, 0) / samples.length);
    }
    return total / 5;
  }
  const normal = await rms(100);
  const half = await rms(50);
  const amplified = await rms(200);
  const silent = await rms(0);
  assert(normal > 0.001 && half / normal > 0.4 && half / normal < 0.6, '50% actually attenuates the audio waveform');
  assert(amplified / normal > 1.8 && amplified / normal < 2.2, '200% actually doubles the audio waveform');
  assert(silent < 0.00001, '0% produces silence');
  const secondAudio = context.createMediaStreamDestination();
  const secondOscillator = context.createOscillator();
  secondOscillator.frequency.value = 880;
  const secondLevel = context.createGain();
  secondLevel.gain.value = 0.05;
  secondOscillator.connect(secondLevel);
  secondLevel.connect(secondAudio);
  secondOscillator.start();
  mixer.addTrack(secondAudio.stream.getAudioTracks()[0]);
  assert(mixer.sources.size === 2, 'Microphone and screen audio are mixed as separate sources');
  const mixed = await rms(100);
  assert(mixed > normal * 1.2, 'Both audio tracks contribute to the output waveform');
  await mixer.close();
  oscillator.stop();
  secondOscillator.stop();
  await context.close();
  return { passed: results.length, checks: results };
})()
