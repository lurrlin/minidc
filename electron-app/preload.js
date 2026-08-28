/**
 * preload.js - Ponte segura entre Main e Renderer
 * -------------------------------------------------
 * Roda em um contexto isolado (contextIsolation: true), com acesso aos
 * modulos do Node, mas SEM expor esse acesso diretamente ao renderer.
 * Publicamos apenas uma API minima e explicita via contextBridge.
 *
 * O renderer (renderer.js) NUNCA ve `require`, `ipcRenderer` cru, etc.
 * Ele so enxerga `window.electronAPI.*`.
 */

const { contextBridge, ipcRenderer } = require('electron');
const { io } = require('socket.io-client');

// socket.io-client precisa do Node (require) para ser importado, entao ele
// so pode viver aqui no preload. Expomos ao renderer apenas um wrapper
// fino (connect/emit/on/disconnect) em vez do objeto `socket` cru,
// mantendo o isolamento de contexto intacto.
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
