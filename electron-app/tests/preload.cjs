const { contextBridge, ipcRenderer } = require('electron');
const listeners = new Map();
ipcRenderer.on('test:event', (_event, name, payload) => listeners.get(name)?.(payload));
contextBridge.exposeInMainWorld('electronAPI', {
  checkMediaPermissions: async () => ({ camera: 'granted', microphone: 'granted' }),
  getScreenSources: async () => [{ id: 'test-screen', name: 'Tela de teste', thumbnailDataUrl: 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" width="320" height="180"><rect width="320" height="180" fill="%23284339"/></svg>' }],
  socketConnect: () => { listeners.clear(); },
  socketOn: (event, callback) => { listeners.set(event, callback); },
  socketEmit: (event, payload) => ipcRenderer.send('test:socket', event, payload),
  socketDisconnect: () => { listeners.clear(); },
  stopNativeAudio: async () => {},
});
