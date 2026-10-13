const { app, BrowserWindow } = require('electron');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');

app.setPath('userData', fs.mkdtempSync(path.join(os.tmpdir(), 'unme-icon-')));
app.whenReady().then(async () => {
  const window = new BrowserWindow({ show: false, width: 650, height: 850, webPreferences: { sandbox: true } });
  try {
    await window.loadFile(path.join(__dirname, '../../docs/icon.html'));
    const png = await window.webContents.executeJavaScript("document.getElementById('icon').toDataURL('image/png').split(',')[1]");
    fs.writeFileSync(path.join(__dirname, '../assets/unme.png'), Buffer.from(png, 'base64'));
    await new Promise((resolve) => setTimeout(resolve, 150));
    fs.mkdirSync(path.join(__dirname, 'artifacts'), { recursive: true });
    fs.writeFileSync(path.join(__dirname, 'artifacts/icon-page.png'), (await window.webContents.capturePage()).toPNG());
    console.log('Exported the icon from docs/icon.html to assets/unme.png');
    app.exit(0);
  } catch (error) { console.error(error); app.exit(1); }
});
