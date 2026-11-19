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
const crypto = require('crypto');
const express = require('express');
const http = require('http');
const { Server } = require('socket.io');

const app = express();
const server = http.createServer(app);
const segredoLogEfemero = crypto.randomBytes(32);

function idSalaParaLog(roomName) {
  return crypto
    .createHmac('sha256', segredoLogEfemero)
    .update(String(roomName || ''))
    .digest('hex')
    .slice(0, 8);
}

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

const MAX_SIGNAL_PAYLOAD_BYTES = 128 * 1024;
const roomKey = (name) => `call:${name}`;
const signalTypes = new Set(['offer', 'answer', 'candidate', 'ready', 'screen-share-start', 'screen-share-stop', 'video-state', 'renegotiate']);
const REGEX_VERSAO_PROTOCOLO = /^[0-9]+\.[0-9]+$/;

function ehObjetoPlano(valor) {
  return valor !== null && typeof valor === 'object' && !Array.isArray(valor);
}

function validarPayloadSinal(data) {
  if (!ehObjetoPlano(data) || typeof data.type !== 'string' || !signalTypes.has(data.type)) {
    return false;
  }

  let serializado;
  try {
    serializado = JSON.stringify(data);
  } catch {
    return false;
  }
  if (!serializado || Buffer.byteLength(serializado, 'utf8') > MAX_SIGNAL_PAYLOAD_BYTES) {
    return false;
  }

  switch (data.type) {
    case 'ready':
      if (
        data.protocolVersion !== undefined &&
        (typeof data.protocolVersion !== 'string' || !REGEX_VERSAO_PROTOCOLO.test(data.protocolVersion))
      ) {
        return false;
      }
      if (data.capabilities !== undefined && !ehObjetoPlano(data.capabilities)) {
        return false;
      }
      return true;
    case 'offer':
    case 'answer':
      return (
        ehObjetoPlano(data.sdp) &&
        data.sdp.type === data.type &&
        typeof data.sdp.sdp === 'string' &&
        data.sdp.sdp.length > 0
      );
    case 'candidate': {
      const candidato = data.candidate;
      if (!ehObjetoPlano(candidato) || typeof candidato.candidate !== 'string') {
        return false;
      }
      if (candidato.sdpMid !== undefined && candidato.sdpMid !== null && typeof candidato.sdpMid !== 'string') {
        return false;
      }
      if (
        candidato.sdpMLineIndex !== undefined &&
        candidato.sdpMLineIndex !== null &&
        (!Number.isInteger(candidato.sdpMLineIndex) || candidato.sdpMLineIndex < 0)
      ) {
        return false;
      }
      if (
        candidato.usernameFragment !== undefined &&
        candidato.usernameFragment !== null &&
        typeof candidato.usernameFragment !== 'string'
      ) {
        return false;
      }
      return true;
    }
    case 'video-state':
      return typeof data.enabled === 'boolean';
    case 'screen-share-start':
    case 'screen-share-stop':
      return true;
    case 'renegotiate':
      return data.iceRestart === undefined || typeof data.iceRestart === 'boolean';
    default:
      return false;
  }
}
const LIMITES_TAXA = {
  'check-room': { janelaMs: 30000, maxPorSocket: 12, maxPorIp: 60 },
  'join-room': { janelaMs: 30000, maxPorSocket: 10, maxPorIp: 40 },
  signal: { janelaMs: 10000, maxPorSocket: 200 },
};
const bucketsPorIp = new Map();

function obterIpSocket(socket) {
  const encaminhado = socket.handshake?.headers?.['x-forwarded-for'];
  if (typeof encaminhado === 'string' && encaminhado.trim()) {
    return encaminhado.split(',')[0].trim();
  }
  return socket.handshake?.address || 'desconhecido';
}

function consumirLimiteJanela(tabela, chave, janelaMs, maximo, agora) {
  let entrada = tabela.get(chave);
  if (!entrada || agora - entrada.inicioJanela >= janelaMs) {
    entrada = { inicioJanela: agora, contagem: 1 };
    tabela.set(chave, entrada);
    return true;
  }
  if (entrada.contagem >= maximo) {
    return false;
  }
  entrada.contagem += 1;
  return true;
}

function limparBucketsExpirados(agora) {
  if (bucketsPorIp.size <= 1000) return;
  for (const [chave, entrada] of bucketsPorIp.entries()) {
    if (agora - entrada.inicioJanela >= 60000) {
      bucketsPorIp.delete(chave);
    }
  }
}

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
  const bucketsSocket = new Map();
  const ipCliente = obterIpSocket(socket);

  function dentroDoLimite(evento) {
    const regra = LIMITES_TAXA[evento];
    if (!regra) return true;
    const agora = Date.now();
    if (!consumirLimiteJanela(bucketsSocket, evento, regra.janelaMs, regra.maxPorSocket, agora)) {
      return false;
    }
    if (regra.maxPorIp) {
      limparBucketsExpirados(agora);
      if (!consumirLimiteJanela(bucketsPorIp, `${evento}:${ipCliente}`, regra.janelaMs, regra.maxPorIp, agora)) {
        return false;
      }
    }
    return true;
  }

  socket.on('join-room', (roomNameRaw) => {
    if (!dentroDoLimite('join-room')) {
      socket.emit('join-error', 'Muitas tentativas em sequencia. Aguarde alguns segundos.');
      return;
    }
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
    console.log(`[join] ${socket.id} -> #${idSalaParaLog(roomName)} (${size}/2)`);

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
    if (!dentroDoLimite('check-room')) {
      socket.emit('room-check-result', {
        roomName,
        exists: false,
        available: false,
      });
      return;
    }
    const size = roomSize(roomName);
    socket.emit('room-check-result', {
      roomName,
      exists: size > 0,
      available: size < 2,
    });
  });

  // Repassa mensagens de sinalizacao (offer, answer, ICE candidate e controle)
  // apos validar tipo, tamanho e estrutura minima esperada para cada sinal.
  socket.on('signal', (payload) => {
    if (!dentroDoLimite('signal')) return;
    if (!ehObjetoPlano(payload)) return;
    const { roomName, data } = payload;
    if (!currentRoom || roomName !== currentRoom) return;
    if (!validarPayloadSinal(data)) return;
    socket.to(roomKey(roomName)).emit('signal', data);
  });

  socket.on('leave-room', () => cleanup('leave-room event'));
  socket.on('disconnect', () => {
    bucketsSocket.clear();
    cleanup('disconnect');
  });

  function cleanup(reason) {
    if (!currentRoom) return;
    const set = rooms.get(currentRoom);
    if (set) {
      set.delete(socket.id);
      socket.to(roomKey(currentRoom)).emit('peer-left');
      if (set.size === 0) rooms.delete(currentRoom);
    }
    console.log(`[leave] ${socket.id} <- #${idSalaParaLog(currentRoom)} (${reason})`);
    socket.leave(roomKey(currentRoom));
    currentRoom = null;
  }
});

if (require.main === module) {
  server.listen(PORT, () => {
    console.log(`Signaling server ouvindo na porta ${PORT}`);
  });
}

module.exports = { server, io, idSalaParaLog, validarPayloadSinal };
