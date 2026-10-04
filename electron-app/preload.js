

const { contextBridge, ipcRenderer } = require('electron');
const { io } = require('socket.io-client');

let socket = null;

contextBridge.exposeInMainWorld('electronAPI', {
  getScreenSources: () => ipcRenderer.invoke('get-screen-sources'),
  startNativeAudio: (pid) => ipcRenderer.invoke('start-native-audio', pid),
  stopNativeAudio: () => ipcRenderer.invoke('stop-native-audio'),
  onNativeAudioData: (callback) => {
    const listener = (_event, packet) => callback(packet);
    ipcRenderer.on('native-audio-data', listener);
    return () => ipcRenderer.removeListener('native-audio-data', listener);
  },
  checkMediaPermissions: () => ipcRenderer.invoke('check-media-permissions'),
  platform: process.platform,

  socketConnect: (url) => {
    if (socket) socket.disconnect();
    socket = io(url, {
      transports: ['websocket', 'polling'],
      reconnection: true,
      reconnectionAttempts: Infinity,
      reconnectionDelay: 1000,
      reconnectionDelayMax: 5000,
    });
    return true;
  },
  socketEmit: (event, payload) => {
    if (!socket) return;
    socket.emit(event, payload);
  },
  socketOn: (event, callback) => {
    if (!socket) return;
    socket.on(event, (...args) => callback(...args));
  },
  socketDisconnect: () => {
    if (!socket) return;
    socket.disconnect();
    socket = null;
  },
});
