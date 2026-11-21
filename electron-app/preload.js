

const { contextBridge, ipcRenderer } = require('electron');

const socketListeners = new Map();
let socketConnected = false;

ipcRenderer.on('socket-event', (_event, eventName, ...args) => {
  const callbacks = socketListeners.get(eventName);
  if (!callbacks) return;
  callbacks.forEach((callback) => callback(...args));
});

contextBridge.exposeInMainWorld('electronAPI', {
  getScreenSources: () => ipcRenderer.invoke('get-screen-sources'),
  startNativeAudio: (pid, includeMode = true) => ipcRenderer.invoke('start-native-audio', pid, includeMode),
  stopNativeAudio: () => ipcRenderer.invoke('stop-native-audio'),
  onNativeAudioData: (callback) => {
    const listener = (_event, packet) => callback(packet);
    ipcRenderer.on('native-audio-data', listener);
    return () => ipcRenderer.removeListener('native-audio-data', listener);
  },
  checkMediaPermissions: () => ipcRenderer.invoke('check-media-permissions'),
  platform: process.platform,

  socketConnect: (url) => {
    socketListeners.clear();
    socketConnected = true;
    ipcRenderer.send('socket-connect', url);
    return true;
  },
  socketEmit: (event, payload) => {
    if (!socketConnected) return;
    ipcRenderer.send('socket-emit', event, payload);
  },
  socketOn: (event, callback) => {
    if (!socketConnected) return;
    let callbacks = socketListeners.get(event);
    if (!callbacks) {
      callbacks = new Set();
      socketListeners.set(event, callbacks);
    }
    callbacks.add(callback);
  },
  socketDisconnect: () => {
    if (!socketConnected) return;
    socketConnected = false;
    socketListeners.clear();
    ipcRenderer.send('socket-disconnect');
  },
});
