const { BrowserWindow, ipcMain } = require('electron');
const path = require('node:path');
const fs = require('node:fs');

module.exports = async function testUI() {
  const windows = [0, 1].map((index) => new BrowserWindow({ show: false, width: 1200, height: 800, webPreferences: { offscreen: true, partition: `persist:peer-${index}`, preload: path.join(__dirname, 'preload.cjs') } }));
  windows.forEach((window, index) => window.webContents.on('console-message', (_event, level, message) => { if (level >= 2) console.log(`UI P${index + 1}: ${message}`); }));
  const participants = new Map();
  let profileWasSignaled = false;
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
      if (payload.data.type === 'profile' || JSON.stringify(payload.data).includes('"photo":')) profileWasSignaled = true;
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
    for (let i = 0; i < 2; i++) {
      await evaluate(i, `document.getElementById('edit-profile').click(); document.getElementById('profile-name').value = '${i ? 'Bia' : 'Alex'}'; document.getElementById('profile-name').dispatchEvent(new Event('input'));`);
      await evaluate(i, `(async () => {
        const canvas = document.createElement('canvas'); canvas.width = 480; canvas.height = 320;
        const context = canvas.getContext('2d'); context.fillStyle = '${i ? '#8dd5b8' : '#c4b4f7'}'; context.fillRect(0, 0, 480, 320);
        const blob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/png'));
        const transfer = new DataTransfer(); transfer.items.add(new File([blob], 'avatar.png', { type: 'image/png' }));
        document.getElementById('profile-photo').files = transfer.files;
        document.getElementById('profile-photo').dispatchEvent(new Event('change'));
      })()`);
      await until(i, `!document.getElementById('profile-save').disabled`);
      await evaluate(i, `document.getElementById('profile-form').requestSubmit()`);
      await until(i, `document.getElementById('profile-modal').hidden`);
    }
    assert(await evaluate(0, `document.getElementById('lobby-profile-name').textContent === 'Alex' && !!document.querySelector('#lobby-profile-avatar img')`), 'Lobby profile editor saves the name and selected photo');
    assert(await evaluate(0, `UserProfile.load().photo.startsWith('data:image/jpeg;base64,') && UserProfile.load().photo.length < 60000`), 'Avatar is resized and compressed before storage and transmission');
    await evaluate(0, `document.getElementById('edit-profile').click(); document.getElementById('profile-name').value = 'Discarded'; document.getElementById('profile-close').click()`);
    assert(await evaluate(0, `ProfileUI.getProfile().name === 'Alex'`), 'Closing the profile editor discards unsaved changes');
    await evaluate(0, `document.getElementById('edit-profile').click()`);
    await wait(100);
    fs.writeFileSync(path.join(artifacts, 'profile.png'), (await windows[0].webContents.capturePage()).toPNG());
    await evaluate(0, `document.getElementById('profile-close').click()`);
    fs.writeFileSync(path.join(artifacts, 'lobby.png'), (await windows[0].webContents.capturePage()).toPNG());
    for (let i = 0; i < 2; i++) await evaluate(i, `
      window.APP_CONFIG.ICE_SERVERS = [];
      window.__microphoneCaptures = [];
      window.__outgoingMicrophoneTracks = [];
      window.__remoteMixers = [];
      const OriginalMixer = CallMedia.RemoteAudioMixer;
      CallMedia.RemoteAudioMixer = class extends OriginalMixer {
        constructor(element) { super(element); this.assignments = []; window.__remoteMixers.push(this); }
        addTrack(track, channel) { super.addTrack(track, channel); this.assignments.push(channel); }
      };
      const originalReplaceTrack = RTCRtpSender.prototype.replaceTrack;
      RTCRtpSender.prototype.replaceTrack = async function(track) {
        await originalReplaceTrack.call(this, track);
        if (track?.kind === 'audio') window.__outgoingMicrophoneTracks.push(track);
      };
      const originalCapture = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);
      navigator.mediaDevices.getUserMedia = async (options) => {
        if (!options.video?.mandatory) {
          if (options.audio && window.__failNextMicrophone) {
            window.__failNextMicrophone = false;
            throw new DOMException('Simulated unsupported microphone option', 'OverconstrainedError');
          }
          const stream = await originalCapture(options);
          if (options.audio) window.__microphoneCaptures.push({ track: stream.getAudioTracks()[0], constraints: options.audio });
          return stream;
        }
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
    assert(await evaluate(0, `(() => { const stage = document.querySelector('.stage').getBoundingClientRect(); const remote = document.getElementById('remote-tile').getBoundingClientRect(); const local = document.getElementById('local-tile').getBoundingClientRect(); window.__emptyTileSize = { width: remote.width, height: remote.height }; return remote.height > stage.height * .8 && remote.width > stage.width * .4 && Math.abs(local.height - remote.height) < 1; })()`), 'Empty tiles fill the same call stage as video tiles');
    await evaluate(0, `document.getElementById('btn-deafen').click()`);
    const room = await evaluate(0, `document.getElementById('active-room-code').textContent`);
    await evaluate(1, `document.getElementById('show-join-button').click(); document.getElementById('room-input').value = ${JSON.stringify(room)}; document.getElementById('join-form').requestSubmit();`);
    for (let i = 0; i < 2; i++) await until(i, `document.getElementById('connection-dot').classList.contains('conn-connected')`);
    checks.push('Production renderer connects two peers without cameras');
    assert(await evaluate(0, `window.__remoteMixers.at(-1).assignments.includes('voice') && window.__remoteMixers.at(-1).assignments.includes('screen')`), 'Negotiated microphone and transmission tracks reach separate mixer channels');
    assert(await evaluate(0, `document.getElementById('remote-audio').muted && window.__remoteMixers.at(-1).gain.gain.value === 0`), 'Deafen selected before joining the partner also silences newly received tracks');
    await evaluate(0, `document.getElementById('btn-deafen').click()`);
    await until(0, `document.getElementById('remote-tile-label').textContent === 'Bia' && !!document.querySelector('#remote-avatar img')`);
    await until(1, `document.getElementById('remote-tile-label').textContent === 'Alex' && !!document.querySelector('#remote-avatar img')`);
    checks.push('Names and photos reach both partners over a real WebRTC data channel');
    await wait(100);
    fs.writeFileSync(path.join(artifacts, 'audio-only.png'), (await windows[0].webContents.capturePage()).toPNG());
    assert(!profileWasSignaled, 'Profile payloads are never forwarded through the signaling server');
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
    assert(await evaluate(0, `(() => { const bounds = document.getElementById('remote-tile').getBoundingClientRect(); return Math.abs(bounds.width - window.__emptyTileSize.width) < 1 && Math.abs(bounds.height - window.__emptyTileSize.height) < 1; })()`), 'Starting video does not change the empty tile dimensions');
    await evaluate(0, `document.getElementById('btn-fullscreen').click()`);
    await until(0, `document.fullscreenElement === document.getElementById('remote-tile')`);
    await until(0, `document.getElementById('btn-fullscreen').textContent === 'Sair da tela cheia'`);
    assert(await evaluate(0, `document.getElementById('btn-fullscreen').textContent === 'Sair da tela cheia'`), 'Fullscreen button changes to an explicit exit action');
    await evaluate(0, `document.getElementById('btn-fullscreen').click()`);
    await until(0, `!document.fullscreenElement`);
    checks.push('Same button exits real Chromium fullscreen');
    await evaluate(0, `document.getElementById('btn-fullscreen').click()`);
    await until(0, `!!document.fullscreenElement`);
    await evaluate(0, `document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))`);
    await until(0, `!document.fullscreenElement`);
    checks.push('Escape also exits fullscreen');
    await evaluate(1, `document.getElementById('btn-share').click()`);
    await until(1, `!document.getElementById('source-modal').hidden`);
    await evaluate(1, `document.getElementById('stop-screen-share').click()`);
    await until(0, `!document.getElementById('remote-tile').classList.contains('has-video')`);
    await evaluate(1, `document.getElementById('btn-share').click()`);
    await until(1, `!document.getElementById('source-modal').hidden`);
    await evaluate(1, `document.querySelector('.source-option').click()`);
    await until(0, `document.getElementById('remote-tile').classList.contains('has-video')`);
    checks.push('Stopping sharing restores the disabled camera and sharing can restart');
    assert(await evaluate(0, `document.getElementById('remote-context-menu').hidden && document.getElementById('remote-volume').getBoundingClientRect().width === 0`), 'Received audio controls are hidden until the right-click menu opens');
    await evaluate(0, `(() => { const tile = document.getElementById('remote-tile'); const bounds = tile.getBoundingClientRect(); tile.dispatchEvent(new MouseEvent('contextmenu', { bubbles: true, clientX: bounds.right - 4, clientY: bounds.bottom - 4 })); })()`);
    assert(await evaluate(0, `!document.getElementById('remote-context-menu').hidden && document.getElementById('screen-volume').getBoundingClientRect().width > 0`), 'Right click reveals separate voice and transmission sliders');
    await evaluate(0, `document.getElementById('remote-volume').value = '200'; document.getElementById('remote-volume').dispatchEvent(new Event('input')); document.getElementById('screen-volume').value = '35'; document.getElementById('screen-volume').dispatchEvent(new Event('input'));`);
    await wait(100);
    assert(await evaluate(0, `document.getElementById('settings-output-volume').value === '100' && document.getElementById('remote-volume-value').textContent === '200%' && document.getElementById('screen-volume-value').textContent === '35%' && window.__remoteMixers.at(-1).channelGains.voice.gain.value === 2 && Math.abs(window.__remoteMixers.at(-1).channelGains.screen.gain.value - .35) < .001`), 'Voice and transmission levels change independently of the master slider');
    assert(await evaluate(0, `const tile = document.getElementById('remote-tile'); const before = tile.className; document.getElementById('remote-volume').click(); before === tile.className;`), 'Clicking the slider does not resize the screen');
    await evaluate(0, `document.getElementById('btn-deafen').click()`);
    assert(await evaluate(0, `document.getElementById('remote-audio').muted && window.__remoteMixers.at(-1).gain.gain.value === 0 && document.getElementById('btn-deafen').getAttribute('aria-pressed') === 'true' && window.__microphoneCaptures.at(-1).track.enabled`), 'Dock deafen silences both channels without muting the microphone');
    await evaluate(0, `document.getElementById('screen-volume').value = '65'; document.getElementById('screen-volume').dispatchEvent(new Event('input'));`);
    assert(await evaluate(0, `window.__remoteMixers.at(-1).gain.gain.value === 0`), 'Adjusting a channel while deafened keeps the output silent');
    await evaluate(0, `document.getElementById('btn-deafen').click()`);
    assert(await evaluate(0, `!document.getElementById('remote-audio').muted && window.__remoteMixers.at(-1).gain.gain.value === 1 && window.__remoteMixers.at(-1).channelGains.voice.gain.value === 2 && Math.abs(window.__remoteMixers.at(-1).channelGains.screen.gain.value - .65) < .001`), 'Dock undeafen restores output without resetting voice and transmission levels');
    await evaluate(0, `(() => { const tile = document.getElementById('remote-tile'); const bounds = tile.getBoundingClientRect(); tile.dispatchEvent(new MouseEvent('contextmenu', { bubbles: true, clientX: bounds.right - 4, clientY: bounds.bottom - 4 })); })()`);
    await wait(100);
    fs.writeFileSync(path.join(artifacts, 'call.png'), (await windows[0].webContents.capturePage()).toPNG());
    windows[0].setSize(820, 560);
    await wait(200);
    await evaluate(0, `(() => { const tile = document.getElementById('remote-tile'); const bounds = tile.getBoundingClientRect(); tile.dispatchEvent(new MouseEvent('contextmenu', { bubbles: true, clientX: bounds.right - 4, clientY: bounds.bottom - 4 })); })()`);
    assert(await evaluate(0, `(() => { const range = document.getElementById('remote-volume').getBoundingClientRect(); const tile = document.getElementById('remote-tile').getBoundingClientRect(); return range.left >= tile.left && range.right <= tile.right && range.bottom <= tile.bottom; })()`), 'Slider stays inside the tile at the minimum window size');
    await evaluate(0, `document.getElementById('remote-tile').click()`);
    await wait(100);
    fs.writeFileSync(path.join(artifacts, 'compact.png'), (await windows[0].webContents.capturePage()).toPNG());
    await evaluate(0, `document.getElementById('open-settings').click()`);
    await until(0, `!document.getElementById('settings-modal').hidden`);
    await evaluate(0, `document.getElementById('btn-mute').click()`);
    for (const [key, id] of [
      ['noiseSuppression', 'settings-noise-suppression'],
      ['echoCancellation', 'settings-echo-cancellation'],
      ['autoGainControl', 'settings-auto-gain'],
    ]) {
      for (const enabled of [false, true]) {
        await evaluate(0, `document.getElementById('${id}').click()`);
        await until(0, `!document.getElementById('${id}').disabled && document.getElementById('audio-processing-status').textContent === 'Opções aplicadas ao microfone e à voz enviada.'`);
        assert(await evaluate(0, `(() => { const capture = window.__microphoneCaptures.at(-1); return capture.track.getSettings().${key} === ${enabled} && capture.constraints.${key}.exact === ${enabled}; })()`), `${key}=${enabled} is confirmed on the new captured microphone`);
        assert(await evaluate(0, `!window.__microphoneCaptures.at(-1).track.enabled && window.__outgoingMicrophoneTracks.at(-1).readyState === 'live'`), 'Processing changes preserve mute and feed a live outbound audio track');
      }
    }
    fs.writeFileSync(path.join(artifacts, 'audio-settings.png'), (await windows[0].webContents.capturePage()).toPNG());
    await evaluate(0, `window.__previousMicrophone = window.__microphoneCaptures.at(-1).track; window.__previousOutput = window.__outgoingMicrophoneTracks.at(-1); window.__failNextMicrophone = true; document.getElementById('settings-noise-suppression').click()`);
    await until(0, `document.getElementById('audio-processing-status').dataset.tone === 'error'`);
    assert(await evaluate(0, `document.getElementById('settings-noise-suppression').checked && window.__previousMicrophone.readyState === 'live' && window.__previousOutput === window.__outgoingMicrophoneTracks.at(-1)`), 'Rejected processing option restores the checkbox and preserves the previous microphone pipeline');
    await evaluate(0, `document.getElementById('btn-mute').click()`);
    assert(await evaluate(0, `(() => { const card = document.querySelector('.settings-card'); return card.scrollHeight >= card.clientHeight && getComputedStyle(card).overflowY === 'auto'; })()`), 'Settings can scroll at the minimum window size');
    await evaluate(0, `document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))`);
    assert(await evaluate(0, `document.getElementById('settings-modal').hidden`), 'Escape closes settings');
    await evaluate(0, `document.getElementById('btn-leave').click()`);
    await until(0, `!document.getElementById('lobby-screen').hidden`);
    await evaluate(0, `document.getElementById('edit-profile').click(); document.getElementById('profile-name').value = 'Alex Novo'; document.getElementById('profile-form').requestSubmit();`);
    await wait(150);
    await evaluate(0, `document.getElementById('show-join-button').click(); document.getElementById('room-input').value = ${JSON.stringify(room)}; document.getElementById('join-form').requestSubmit();`);
    for (let i = 0; i < 2; i++) await until(i, `document.getElementById('connection-dot').classList.contains('conn-connected')`);
    checks.push('Leaving and rejoining promotes the remaining peer and reconnects');
    await until(0, `document.getElementById('remote-video').getVideoPlaybackQuality().totalVideoFrames > 0 && document.getElementById('remote-video').videoWidth > 0`);
    checks.push('Active screen sharing survives the other participant rejoining');
    await until(1, `document.getElementById('remote-tile-label').textContent === 'Alex Novo'`);
    checks.push('An edited profile is shared again after rejoining');
    await evaluate(0, `document.getElementById('btn-leave').click()`);
    await windows[0].loadFile(path.join(__dirname, '../index.html'));
    assert(await evaluate(0, `ProfileUI.getProfile().name === 'Alex Novo' && !!document.querySelector('#lobby-profile-avatar img')`), 'Profile choices persist after recreating the page');
    return checks;
  } finally {
    ipcMain.removeListener('test:socket', relay);
    windows.forEach((window) => window.destroy());
  }
};
