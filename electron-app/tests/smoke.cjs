const { app, BrowserWindow, nativeImage } = require('electron');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');

// Check the production main process and preload, including the packaged native addon.
const appDirectory = process.argv.includes('--packaged')
  ? path.join(__dirname, '../dist/win-unpacked/resources/app.asar')
  : path.join(__dirname, '..');
app.setPath('userData', fs.mkdtempSync(path.join(os.tmpdir(), 'unme-smoke-')));
const timeout = setTimeout(() => { console.error('Production smoke test timed out'); app.exit(1); }, 20000);
require(path.join(appDirectory, 'main.js'));
app.whenReady().then(async () => {
  try {
    const window = BrowserWindow.getAllWindows()[0];
    window.hide();
    if (window.webContents.isLoading()) await new Promise((resolve) => window.webContents.once('did-finish-load', resolve));
    const state = await window.webContents.executeJavaScript(`({
      title: document.title,
      mark: document.querySelector('.brand-symbol').textContent,
      profile: typeof window.ProfileUI.getProfile === 'function',
      bridge: ['getScreenSources', 'socketConnect', 'startNativeAudio', 'stopNativeAudio'].every((key) => typeof window.electronAPI[key] === 'function'),
      favicon: document.querySelector('link[rel="icon"]').getAttribute('href'),
    })`);
    assert.equal(state.title, 'Unme');
    assert.equal(state.mark, 'ü');
    assert(state.profile && state.bridge, 'Production profile scripts and preload must load');
    assert.equal(state.favicon, 'assets/unme.png');
    assert(!nativeImage.createFromPath(path.join(appDirectory, 'assets/unme.ico')).isEmpty());
    const nativeAudio = require(path.join(appDirectory, 'node_modules/electron-native-screenshare'));
    assert(nativeAudio.isAvailable(), nativeAudio.getLoadError());
    const metadata = JSON.parse(fs.readFileSync(path.join(appDirectory, 'package.json'), 'utf8'));
    assert.equal(metadata.version, '1.3.6');
    if (process.argv.includes('--packaged')) {
      assert(!fs.existsSync(path.join(appDirectory, 'tests')), 'Test files must not ship in the installer');
      assert(!fs.existsSync(path.join(appDirectory, '.env')), 'Local configuration must not ship in the installer');
    }
    console.log(JSON.stringify({ productionSmokePassed: true, packaged: process.argv.includes('--packaged'), version: metadata.version, electron: process.versions.electron, nativeAudioAvailable: true, state }, null, 2));
    clearTimeout(timeout);
    app.exit(0);
  } catch (error) { console.error(error); clearTimeout(timeout); app.exit(1); }
});
