/**
 * main.js - Main Process do Electron
 * -----------------------------------
 * Regras de seguranca seguidas aqui (obrigatorias a partir do Electron 20+):
 *   - contextIsolation: true
 *   - nodeIntegration: false
 *   - sandbox: true
 *   - Nenhum modulo Node (fs, desktopCapturer, etc.) e exposto direto ao
 *     renderer. Tudo passa por IPC + preload.js com contextBridge.
 *
 * desktopCapturer.getSources() SO PODE ser chamado no Main process
 * (nao existe mais no renderer desde o Electron 20). Por isso ele mora
 * aqui, atras de um ipcMain.handle, e o resultado (com os thumbnails ja
 * convertidos para dataURL) e devolvido ao renderer via preload.
 */

const { app, BrowserWindow, ipcMain, desktopCapturer, systemPreferences } = require('electron');
const path = require('path');

let mainWindow;

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1080,
    height: 720,
    minWidth: 820,
    minHeight: 560,
    backgroundColor: '#16131d',
    autoHideMenuBar: true,
    title: 'Mini Discord',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });

  mainWindow.loadFile('index.html');

  // Descomente para depurar o renderer durante o desenvolvimento:
  // mainWindow.webContents.openDevTools({ mode: 'detach' });
}

// ---- IPC: listar telas/janelas disponiveis para compartilhamento ----
ipcMain.handle('get-screen-sources', async () => {
  const sources = await desktopCapturer.getSources({
    types: ['screen', 'window'],
    thumbnailSize: { width: 320, height: 180 },
    fetchWindowIcons: true,
  });

  // Convertemos os thumbnails (NativeImage) para dataURL aqui mesmo,
  // porque NativeImage nao pode atravessar o IPC para o renderer.
  return sources.map((source) => ({
    id: source.id,
    name: source.name,
    thumbnailDataUrl: source.thumbnail.toDataURL(),
    appIconDataUrl: source.appIcon && !source.appIcon.isEmpty() ? source.appIcon.toDataURL() : null,
  }));
});

// ---- IPC: pedir permissao de camera/microfone no macOS ----
// No Windows isso e tratado pelo proprio prompt do Chromium/getUserMedia,
// mas no macOS o app precisa pedir explicitamente via API nativa.
ipcMain.handle('check-media-permissions', async () => {
  if (process.platform !== 'darwin') {
    return { camera: 'granted', microphone: 'granted' };
  }

  const cameraGranted = await systemPreferences.askForMediaAccess('camera');
  const microphoneGranted = await systemPreferences.askForMediaAccess('microphone');

  return {
    camera: cameraGranted ? 'granted' : 'denied',
    microphone: microphoneGranted ? 'granted' : 'denied',
  };
});

app.whenReady().then(() => {
  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
