const { app, BrowserWindow, ipcMain, desktopCapturer, systemPreferences } = require('electron');
const { io } = require('socket.io-client');
let nativeScreenAudio = null;
try {
  nativeScreenAudio = require('electron-native-screenshare');
} catch (error) {
  console.warn('Áudio por janela indisponível; captura de sistema continua disponível:', error.message);
}
const path = require('path');

const SOCKET_FORWARDED_EVENTS = [
  'connect',
  'disconnect',
  'join-error',
  'room-full',
  'joined-room',
  'peer-joined',
  'peer-left',
  'room-check-result',
  'signal',
];

let mainWindow;
let signalingSocket = null;

function sendSocketEventToRenderer(eventName, ...args) {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send('socket-event', eventName, ...args);
  }
}

function disconnectSignalingSocket(notifyPeer = false) {
  if (!signalingSocket) return;
  const socket = signalingSocket;
  signalingSocket = null;
  if (notifyPeer && socket.connected) {
    try {
      socket.emit('leave-room');
    } catch {}
  }
  socket.removeAllListeners();
  socket.disconnect();
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    minWidth: 820,
    minHeight: 560,
    backgroundColor: '#10121b',
    autoHideMenuBar: true,
    title: 'unme',
    icon: path.join(__dirname, 'assets', process.platform === 'win32' ? 'unme.ico' : 'unme.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });

  mainWindow.loadFile(path.join(__dirname, 'index.html'));
  mainWindow.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  mainWindow.webContents.on('will-navigate', (event) => event.preventDefault());
  mainWindow.on('closed', () => {
    disconnectSignalingSocket(true);
    if (nativeScreenAudio?.isAvailable()) nativeScreenAudio.stopCapture();
    mainWindow = null;
  });
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
  if (!nativeScreenAudio?.isAvailable() || !sourceId.startsWith('window:')) return null;
  const windowHandle = Number(sourceId.split(':')[1]);
  if (!Number.isInteger(windowHandle)) return null;
  try {
    return nativeScreenAudio.getPidFromWindowHandle(windowHandle) || null;
  } catch (error) {
    console.warn('Nao foi possivel obter o PID da janela:', error);
    return null;
  }
}

ipcMain.handle('start-native-audio', (_event, pid = null, includeMode = true) => {
  if (!nativeScreenAudio?.isAvailable()) {
    throw new Error(nativeScreenAudio?.getLoadError() || 'WASAPI indisponivel.');
  }
  const incluirSomenteAlvo = includeMode !== false;
  const pidAlvo = incluirSomenteAlvo ? pid : process.pid;
  if (!Number.isInteger(pidAlvo) || pidAlvo <= 0) throw new Error('PID de audio invalido.');

  nativeScreenAudio.stopCapture();
  const started = nativeScreenAudio.startCapture(pidAlvo, incluirSomenteAlvo, (data, meta) => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('native-audio-data', {
        data: new Uint8Array(data),
        meta,
      });
    }
  });
  if (!started) throw new Error('Não foi possível iniciar o áudio nativo da transmissão.');
  return started;
});

ipcMain.handle('stop-native-audio', () => {
  if (!nativeScreenAudio?.isAvailable()) return false;
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

ipcMain.on('socket-connect', (_event, url) => {
  disconnectSignalingSocket(false);
  if (typeof url !== 'string' || !/^https?:\/\//i.test(url)) return;

  signalingSocket = io(url, {
    transports: ['websocket', 'polling'],
    reconnection: true,
    reconnectionAttempts: Infinity,
    reconnectionDelay: 1000,
    reconnectionDelayMax: 5000,
  });

  SOCKET_FORWARDED_EVENTS.forEach((eventName) => {
    signalingSocket.on(eventName, (...args) => sendSocketEventToRenderer(eventName, ...args));
  });
  signalingSocket.on('connect_error', (err) => {
    sendSocketEventToRenderer('connect_error', { message: err?.message || String(err || '') });
  });
});

ipcMain.on('socket-emit', (_event, eventName, payload) => {
  if (!signalingSocket || typeof eventName !== 'string') return;
  signalingSocket.emit(eventName, payload);
});

ipcMain.on('socket-disconnect', () => {
  disconnectSignalingSocket(false);
});

app.on('before-quit', () => {
  disconnectSignalingSocket(true);
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
