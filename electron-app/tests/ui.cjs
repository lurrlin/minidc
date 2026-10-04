const { BrowserWindow, ipcMain } = require('electron');
const path = require('node:path');
const fs = require('node:fs');

module.exports = async function testUI() {
  const windows = [0, 1].map(() => new BrowserWindow({ show: false, width: 1200, height: 800, webPreferences: { offscreen: true, preload: path.join(__dirname, 'preload.cjs') } }));
  windows.forEach((window, index) => window.webContents.on('console-message', (_event, level, message) => { if (level >= 2) console.log(`UI P${index + 1}: ${message}`); }));
  const participants = new Map();
  const checks = [];
  const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
  const evaluate = (index, code) => windows[index].webContents.executeJavaScript(code, true);
  const send = (contents, event, payload) => contents.send('test:event', event, payload);
  const relay = ({ sender }, event, payload) => {
    if (event === 'join-room') {
      const peers = [...participants].filter(([, room]) => room === payload);
      participants.set(sender, payload);
      send(sender, 'joined-room', { roomName: payload, isFirstPeer: !peers.length });
      peers.forEach(([peer]) => send(peer, 'peer-joined'));
    } else if (event === 'signal') {
      for (const [peer, room] of participants) if (peer !== sender && room === payload.roomName) send(peer, 'signal', payload.data);
    } else if (event === 'check-room') {
      const size = [...participants.values()].filter((room) => room === payload).length;
      send(sender, 'room-check-result', { exists: size > 0, available: size < 2 });
    } else if (event === 'leave-room') {
      const room = participants.get(sender);
      participants.delete(sender);
      for (const [peer, name] of participants) if (name === room) send(peer, 'peer-left');
    }
  };
  ipcMain.on('test:socket', relay);
  const assert = (value, message) => { if (!value) throw new Error(message); checks.push(message); };
  async function until(index, code) {
    for (let attempt = 0; attempt < 70; attempt++) {
      if (await evaluate(index, code)) return;
      await wait(100);
    }
    throw new Error('UI condition timed out: ' + code);
  }
  const artifacts = path.join(__dirname, 'artifacts');
  fs.mkdirSync(artifacts, { recursive: true });
  try {
    for (const window of windows) await window.loadFile(path.join(__dirname, '../index.html'));
    fs.writeFileSync(path.join(artifacts, 'lobby.png'), (await windows[0].webContents.capturePage()).toPNG());
    for (let i = 0; i < 2; i++) await evaluate(i, `
      window.APP_CONFIG.ICE_SERVERS = [];
      const originalCapture = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);
      navigator.mediaDevices.getUserMedia = async (options) => {
        if (!options.video?.mandatory) return originalCapture(options);
        const canvas = document.createElement('canvas');
        canvas.width = 640; canvas.height = 360;
        const context = canvas.getContext('2d');
        let frame = 0;
        const timer = setInterval(() => {
          context.fillStyle = '${i ? '#284239' : '#3d3455'}'; context.fillRect(0,0,640,360);
          context.fillStyle = 'white'; context.font = '24px sans-serif'; context.fillText('Tela P${i + 1} / ' + frame++, 40, 80);
        }, 60);
        const stream = canvas.captureStream(15);
        stream.getVideoTracks()[0].addEventListener('ended', () => clearInterval(timer));
        return stream;
      };
      void 0;
    `);
    await evaluate(0, `document.getElementById('create-room-button').click()`);
    await until(0, `!document.getElementById('call-screen').hidden`);
    const room = await evaluate(0, `document.getElementById('active-room-code').textContent`);
    await evaluate(1, `document.getElementById('show-join-button').click(); document.getElementById('room-input').value = ${JSON.stringify(room)}; document.getElementById('join-form').requestSubmit();`);
    for (let i = 0; i < 2; i++) await until(i, `document.getElementById('connection-dot').classList.contains('conn-connected')`);
    checks.push('Production renderer connects two peers without cameras');
    assert(await evaluate(0, `!document.getElementById('remote-tile').classList.contains('has-video')`), 'Audio-only call displays an avatar instead of a black video');
    await evaluate(1, `document.getElementById('btn-camera').click()`);
    await until(0, `document.getElementById('remote-tile').classList.contains('has-video')`);
    await until(1, `document.getElementById('btn-camera').getAttribute('aria-pressed') === 'true'`);
    await evaluate(1, `document.getElementById('btn-camera').click()`);
    await until(0, `!document.getElementById('remote-tile').classList.contains('has-video')`);
    checks.push('Camera can be enabled and disabled after an audio-only join');
    for (let i = 0; i < 2; i++) {
      await evaluate(i, `document.getElementById('btn-share').click()`);
      await until(i, `!document.getElementById('source-modal').hidden`);
      await evaluate(i, `document.querySelector('.source-option').click()`);
    }
    for (let i = 0; i < 2; i++) await until(i, `document.getElementById('remote-video').videoWidth > 0 && document.getElementById('remote-video').getVideoPlaybackQuality().totalVideoFrames > 0`);
    checks.push('Production renderer displays P1 and P2 screens simultaneously');
    await evaluate(1, `document.getElementById('btn-share').click()`);
    await until(1, `!document.getElementById('source-modal').hidden`);
    await evaluate(1, `document.getElementById('stop-screen-share').click()`);
    await until(0, `!document.getElementById('remote-tile').classList.contains('has-video')`);
    await evaluate(1, `document.getElementById('btn-share').click()`);
    await until(1, `!document.getElementById('source-modal').hidden`);
    await evaluate(1, `document.querySelector('.source-option').click()`);
    await until(0, `document.getElementById('remote-tile').classList.contains('has-video')`);
    checks.push('Stopping sharing restores the disabled camera and sharing can restart');
    await evaluate(0, `document.getElementById('remote-volume').value = '200'; document.getElementById('remote-volume').dispatchEvent(new Event('input'));`);
    await wait(100);
    assert(await evaluate(0, `document.getElementById('settings-output-volume').value === '200' && document.getElementById('remote-volume-value').textContent === '200%'`), 'Volume controls remain synchronized');
    assert(await evaluate(0, `const tile = document.getElementById('remote-tile'); const before = tile.className; document.getElementById('remote-volume').click(); before === tile.className;`), 'Clicking the slider does not resize the screen');
    fs.writeFileSync(path.join(artifacts, 'call.png'), (await windows[0].webContents.capturePage()).toPNG());
    windows[0].setSize(820, 560);
    await wait(200);
    assert(await evaluate(0, `(() => { const range = document.getElementById('remote-volume').getBoundingClientRect(); const tile = document.getElementById('remote-tile').getBoundingClientRect(); return range.left >= tile.left && range.right <= tile.right && range.bottom <= tile.bottom; })()`), 'Slider stays inside the tile at the minimum window size');
    await evaluate(0, `document.getElementById('remote-tile').click()`);
    await wait(100);
    fs.writeFileSync(path.join(artifacts, 'compact.png'), (await windows[0].webContents.capturePage()).toPNG());
    await evaluate(0, `document.getElementById('open-settings').click()`);
    await until(0, `!document.getElementById('settings-modal').hidden`);
    assert(await evaluate(0, `(() => { const card = document.querySelector('.settings-card'); return card.scrollHeight >= card.clientHeight && getComputedStyle(card).overflowY === 'auto'; })()`), 'Settings can scroll at the minimum window size');
    await evaluate(0, `document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))`);
    assert(await evaluate(0, `document.getElementById('settings-modal').hidden`), 'Escape closes settings');
    await evaluate(0, `document.getElementById('btn-leave').click()`);
    await until(0, `!document.getElementById('lobby-screen').hidden`);
    await wait(150);
    await evaluate(0, `document.getElementById('show-join-button').click(); document.getElementById('room-input').value = ${JSON.stringify(room)}; document.getElementById('join-form').requestSubmit();`);
    for (let i = 0; i < 2; i++) await until(i, `document.getElementById('connection-dot').classList.contains('conn-connected')`);
    checks.push('Leaving and rejoining promotes the remaining peer and reconnects');
    await until(0, `document.getElementById('remote-video').getVideoPlaybackQuality().totalVideoFrames > 0 && document.getElementById('remote-video').videoWidth > 0`);
    checks.push('Active screen sharing survives the other participant rejoining');
    return checks;
  } finally {
    ipcMain.removeListener('test:socket', relay);
    windows.forEach((window) => window.destroy());
  }
};
