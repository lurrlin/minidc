const { app } = require('electron');
const fs = require('node:fs');
const path = require('node:path');

// A controlled local workload, not a claim about physical capture or internet latency.
module.exports = async ({ windows, evaluate, wait, assert, artifacts }) => {
  const snapshot = async (index) => evaluate(index, `(async () => {
    const connection = window.__connections.at(-1);
    const stats = [...(await connection.getStats()).values()];
    const video = stats.find((stat) => stat.type === 'inbound-rtp' && stat.kind === 'video');
    const audio = stats.filter((stat) => stat.type === 'inbound-rtp' && stat.kind === 'audio');
    const candidate = stats.find((stat) => stat.type === 'candidate-pair' && stat.state === 'succeeded' && stat.nominated);
    return {
      timestamp: video.timestamp, frames: video.framesDecoded, dropped: video.framesDropped || 0,
      bytes: video.bytesReceived, decodeTime: video.totalDecodeTime || 0,
      width: video.frameWidth, height: video.frameHeight,
      audioBytes: audio.reduce((sum, stat) => sum + stat.bytesReceived, 0),
      rttMs: (candidate?.currentRoundTripTime || 0) * 1000,
      mixerState: window.__remoteMixers.at(-1).context.state,
      connected: connection.connectionState === 'connected',
    };
  })()`);
  await wait(2000);
  const initial = await Promise.all([snapshot(0), snapshot(1)]);
  const measurements = [];
  const processSamples = [];
  console.log('Performance: two simultaneous 720p/30 fps screen streams for 30 seconds');
  for (let second = 0; second < 30; second++) {
    await wait(1000);
    const peers = await Promise.all([snapshot(0), snapshot(1)]);
    assert(peers.every((peer) => peer.connected && peer.mixerState === 'running'), `Performance sample ${second + 1}: peers stay connected and audio stays running`);
    measurements.push(peers);
    const metrics = app.getAppMetrics();
    processSamples.push({
      cpuPercent: metrics.reduce((sum, metric) => sum + metric.cpu.percentCPUUsage, 0),
      workingSetMiB: metrics.reduce((sum, metric) => sum + metric.memory.workingSetSize, 0) / 1024,
    });
  }
  const final = measurements.at(-1);
  const peers = final.map((last, index) => {
    const first = initial[index];
    const seconds = (last.timestamp - first.timestamp) / 1000;
    const frames = last.frames - first.frames;
    assert(last.width === 1280 && last.height === 720, `Performance P${index + 1}: receives 720p frames`);
    assert(frames / seconds >= 15, `Performance P${index + 1}: averages at least 15 decoded fps during the workload`);
    assert(last.audioBytes > first.audioBytes, `Performance P${index + 1}: receives voice throughout the workload`);
    return {
      peer: index + 1, fps: Number((frames / seconds).toFixed(1)),
      droppedFrames: last.dropped - first.dropped,
      meanDecodeMs: Number(((last.decodeTime - first.decodeTime) / frames * 1000).toFixed(2)),
      videoKbps: Number(((last.bytes - first.bytes) * 8 / seconds / 1000).toFixed(1)),
      localRttMs: Number(last.rttMs.toFixed(2)),
    };
  });
  const report = {
    workload: 'Two production renderer windows, synthetic moving 1280x720 canvas screens at 30 fps, fake microphones, local WebRTC',
    durationSeconds: 30, peers,
    meanHarnessCpuPercent: Number((processSamples.reduce((sum, sample) => sum + sample.cpuPercent, 0) / processSamples.length).toFixed(2)),
    peakHarnessWorkingSetMiB: Number(Math.max(...processSamples.map((sample) => sample.workingSetMiB)).toFixed(1)),
    workingSetChangeMiB: Number((processSamples.at(-1).workingSetMiB - processSamples[0].workingSetMiB).toFixed(1)),
    limitations: 'Includes the test harness and all Electron processes. Does not measure real screen capture, physical devices, WAN performance, or long-term memory leaks.',
  };
  fs.writeFileSync(path.join(artifacts, 'performance.json'), JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report, null, 2));
  assert(windows.every((window) => !window.isDestroyed()), 'Performance: no renderer crashed during the workload');
};
