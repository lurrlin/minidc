/**
 * Signaling Server - Mini Discord (2 usuarios)
 * ---------------------------------------------
 * Responsabilidade UNICA: colocar dois peers na mesma "sala" e repassar
 * SDP (offer/answer) e ICE candidates entre eles. NUNCA toca em audio/video
 * - isso e feito P2P direto entre os clientes via WebRTC.
 *
 * Deploy: Render / Railway / Fly.io / qualquer host Node.
 *   - Start command: npm start
 *   - Variavel de ambiente PORT e injetada automaticamente pela maioria dos hosts.
 */

require('dotenv').config();
const express = require('express');
const http = require('http');
const { Server } = require('socket.io');

const app = express();
const server = http.createServer(app);

const PORT = process.env.PORT || 3000;
// Em producao, restrinja isso ao dominio/app real se possivel.
// Como o cliente e um app Electron (sem "origin" de browser tradicional),
// deixamos "*" por padrao para simplicidade de um app de uso pessoal.
const ALLOWED_ORIGIN = process.env.ALLOWED_ORIGIN || '*';

const io = new Server(server, {
  cors: {
    origin: ALLOWED_ORIGIN,
    methods: ['GET', 'POST'],
  },
});

// roomName -> Set<socket.id>  (capacidade maxima: 2)
const rooms = new Map();

function roomSize(roomName) {
  return rooms.get(roomName)?.size ?? 0;
}

function validRoomName(value) {
  return typeof value === 'string' && value.trim().length > 0 && value.trim().length <= 40;
}

const roomKey = (name) => `call:${name}`;
const signalTypes = new Set(['offer', 'answer', 'candidate', 'ready', 'screen-share-start', 'screen-share-stop', 'video-state', 'renegotiate']);

app.get('/', (_req, res) => {
  res.send('Mini Discord signaling server - online.');
});

// Util para checar rapidamente se o server esta de pe (Render/Railway health check)
app.get('/health', (_req, res) => {
  res.json({
    status: 'ok',
    activeRooms: rooms.size,
    uptimeSeconds: Math.round(process.uptime()),
  });
});

io.on('connection', (socket) => {
  console.log(`[connect] ${socket.id}`);
  let currentRoom = null;

  socket.on('join-room', (roomNameRaw) => {
    if (!validRoomName(roomNameRaw)) {
      socket.emit('join-error', 'Nome de sala invalido.');
      return;
    }
    const roomName = roomNameRaw.trim();

    // Um socket so pode estar em uma sala por vez neste app.
    if (currentRoom) {
      socket.emit('join-error', 'Voce ja esta em uma sala.');
      return;
    }

    if (roomSize(roomName) >= 2) {
      socket.emit('room-full', roomName);
      return;
    }

    socket.join(roomKey(roomName));
    currentRoom = roomName;
    if (!rooms.has(roomName)) rooms.set(roomName, new Set());
    rooms.get(roomName).add(socket.id);

    const size = roomSize(roomName);
    console.log(`[join] ${socket.id} -> "${roomName}" (${size}/2)`);

    socket.emit('joined-room', { roomName, isFirstPeer: size === 1 });

    // Se ja havia alguem na sala, avisa esse peer que o segundo chegou.
    // Convencao: quem JA ESTAVA na sala cria a offer (evita "glare",
    // ou seja, os dois tentando ofertar ao mesmo tempo).
    if (size === 2) {
      socket.to(roomKey(roomName)).emit('peer-joined');
    }
  });

  socket.on('check-room', (roomNameRaw) => {
    const roomName = validRoomName(roomNameRaw) ? roomNameRaw.trim() : '';
    const size = roomSize(roomName);
    socket.emit('room-check-result', {
      roomName,
      exists: size > 0,
      available: size < 2,
    });
  });

  // Repassa qualquer mensagem de sinalizacao (offer, answer ou ICE candidate)
  // para o outro peer da mesma sala. O "shape" do payload e definido pelo
  // cliente (renderer.js); o servidor so encaminha, sem inspecionar.
  socket.on('signal', (payload) => {
    if (!payload || typeof payload !== 'object') return;
    const { roomName, data } = payload;
    if (!currentRoom || roomName !== currentRoom || !data || !signalTypes.has(data.type)) return;
    if (JSON.stringify(data).length > 128 * 1024) return;
    socket.to(roomKey(roomName)).emit('signal', data);
  });

  socket.on('leave-room', () => cleanup('leave-room event'));
  socket.on('disconnect', () => cleanup('disconnect'));

  function cleanup(reason) {
    if (!currentRoom) return;
    const set = rooms.get(currentRoom);
    if (set) {
      set.delete(socket.id);
      socket.to(roomKey(currentRoom)).emit('peer-left');
      if (set.size === 0) rooms.delete(currentRoom);
    }
    console.log(`[leave] ${socket.id} <- "${currentRoom}" (${reason})`);
    socket.leave(roomKey(currentRoom));
    currentRoom = null;
  }
});

if (require.main === module) {
  server.listen(PORT, () => {
    console.log(`Signaling server ouvindo na porta ${PORT}`);
  });
}

module.exports = { server, io };
