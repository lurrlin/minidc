const { app, BrowserWindow } = require('electron');
const fs = require('node:fs');
const path = require('node:path');

app.commandLine.appendSwitch('autoplay-policy', 'no-user-gesture-required');
app.commandLine.appendSwitch('use-fake-device-for-media-stream');
app.commandLine.appendSwitch('use-fake-ui-for-media-stream');
app.whenReady().then(async () => {
  const window = new BrowserWindow({ show: false, webPreferences: { offscreen: true, sandbox: true } });
  const timeout = setTimeout(() => { console.error('Regression tests timed out'); app.exit(1); }, 45000);
  try {
    await window.loadFile(path.join(__dirname, 'test.html'));
    const result = await window.webContents.executeJavaScript(fs.readFileSync(path.join(__dirname, 'regression.js'), 'utf8'));
    console.log(JSON.stringify(result, null, 2));
    const checks = await require('./ui.cjs')();
    console.log(JSON.stringify({ uiPassed: checks.length, checks }, null, 2));
    clearTimeout(timeout);
    app.exit(0);
  } catch (error) {
    console.error(error);
    clearTimeout(timeout);
    app.exit(1);
  }
});
