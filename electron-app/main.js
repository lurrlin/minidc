const { app, BrowserWindow, ipcMain, desktopCapturer, systemPreferences } = require('electron');
const nativeScreenAudio = require('electron-native-screenshare');
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
    title: 'unme',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
    },
  });

  mainWindow.loadFile('index.html');

}

ipcMain.handle('get-screen-sources', async () => {
  const sources = await desktopCapturer.getSources({
    types: ['screen', 'window'],
    thumbnailSize: { width: 320, height: 180 },
    fetchWindowIcons: true,
  });


  return sources.map((source) => ({
    id: source.id,
    name: source.name,
    nativeAudioPid: getNativeAudioPid(source.id),
    thumbnailDataUrl: source.thumbnail.toDataURL(),
    appIconDataUrl: source.appIcon && !source.appIcon.isEmpty() ? source.appIcon.toDataURL() : null,
  }));
});

function getNativeAudioPid(sourceId) {
  if (!nativeScreenAudio.isAvailable() || !sourceId.startsWith('window:')) return null;
  const windowHandle = Number(sourceId.split(':')[1]);
  if (!Number.isInteger(windowHandle)) return null;
  try {
    return nativeScreenAudio.getPidFromWindowHandle(windowHandle) || null;
  } catch (error) {
    console.warn('Nao foi possivel obter o PID da janela:', error);
    return null;
  }
}

ipcMain.handle('start-native-audio', (_event, pid) => {
  if (!nativeScreenAudio.isAvailable()) {
    throw new Error(nativeScreenAudio.getLoadError() || 'WASAPI indisponivel.');
  }
  if (!Number.isInteger(pid) || pid <= 0) throw new Error('PID de audio invalido.');

  nativeScreenAudio.stopCapture();
  nativeScreenAudio.startCapture(pid, true, (data, meta) => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('native-audio-data', {
        data: new Uint8Array(data),
        meta,
      });
    }
  });
  return true;
});

ipcMain.handle('stop-native-audio', () => {
  if (!nativeScreenAudio.isAvailable()) return false;
  return nativeScreenAudio.stopCapture();
});


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
