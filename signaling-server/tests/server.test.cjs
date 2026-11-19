const { test } = require('node:test');
const assert = require('node:assert/strict');
const { io: client } = require('socket.io-client');
const {
  server,
  io,
  idSalaParaLog,
  validarPayloadSinal,
  interpretarPedidoEntrada,
} = require('../server');

function once(socket, event) {
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error(`Timeout: ${event}`)), 3000);
    socket.once(event, (value) => { clearTimeout(timeout); resolve(value); });
  });
}

function emitAndWait(socket, event, payload, response) {
  const result = once(socket, response);
  socket.emit(event, payload);
  return result;
}

async function conectarSockets(url, quantidade) {
  const sockets = Array.from({ length: quantidade }, () => (
    client(url, { transports: ['websocket'], autoConnect: false })
  ));
  await Promise.all(sockets.map((socket) => {
    const ready = once(socket, 'connect');
    socket.connect();
    return ready;
  }));
  return sockets;
}

test('signaling server: salas, validacao, logs mascarados, v1.1 e rate limit', async (t) => {
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const url = `http://127.0.0.1:${server.address().port}`;
  const activeSockets = [];

  try {
    await t.test('rooms, validation, relay isolation, masked logs and cleanup', async () => {
      const logsCapturados = [];
      const logOriginal = console.log;
      console.log = (...args) => {
        logsCapturados.push(args.join(' '));
      };

      const sockets = await conectarSockets(url, 3);
      activeSockets.push(...sockets);
      const [first, second, outsider] = sockets;

      try {
        assert.match(await emitAndWait(first, 'join-room', {}, 'join-error'), /invalido/);
        assert.match(await emitAndWait(first, 'join-room', 'x'.repeat(41), 'join-error'), /invalido/);
        assert.equal((await emitAndWait(first, 'join-room', 'room-one', 'joined-room')).isFirstPeer, true);
        assert.deepEqual(
          await emitAndWait(second, 'check-room', 'room-one', 'room-check-result'),
          { roomName: 'room-one', exists: true, available: true },
        );
        const peerJoined = once(first, 'peer-joined');
        assert.equal((await emitAndWait(second, 'join-room', 'room-one', 'joined-room')).isFirstPeer, false);
        await peerJoined;
        await emitAndWait(outsider, 'join-room', 'room-one', 'room-full');
        // A room named after a socket must not receive that socket's private events.
        await emitAndWait(outsider, 'join-room', first.id, 'joined-room');
        let leaked = false;
        first.on('signal', () => { leaked = true; });
        outsider.emit('signal', { roomName: first.id, data: { type: 'ready' } });
        outsider.emit('signal', { roomName: 'room-one', data: { type: 'ready' } });
        outsider.emit('signal', null);
        outsider.emit('signal');
        outsider.emit('signal', { roomName: first.id, data: { type: 'unknown' } });
        await new Promise((resolve) => setTimeout(resolve, 100));
        assert.equal(leaked, false);
        const signal = once(second, 'signal');
        first.emit('signal', { roomName: 'room-one', data: { type: 'ready' } });
        assert.deepEqual(await signal, { type: 'ready' });
        const peerLeft = once(first, 'peer-left');
        second.emit('leave-room');
        await peerLeft;
        assert.equal((await emitAndWait(second, 'join-room', 'room-one', 'joined-room')).isFirstPeer, false);
        first.emit('leave-room');
        second.emit('leave-room');
        outsider.emit('leave-room');
        await new Promise((resolve) => setTimeout(resolve, 50));
        assert.equal((await emitAndWait(first, 'check-room', 'room-one', 'room-check-result')).exists, false);
        const health = await fetch(`${url}/health`).then((response) => response.json());
        assert.equal(health.status, 'ok');
        assert.equal(health.protocolVersion, '1.1');

        const textoLogs = logsCapturados.join('\n');
        assert.equal(textoLogs.includes('room-one'), false, 'Codigo da sala em texto claro nao pode aparecer nos logs');
        assert.equal(textoLogs.includes(`#${idSalaParaLog('room-one')}`), true);
      } finally {
        console.log = logOriginal;
        sockets.forEach((socket) => socket.disconnect());
      }
    });

    await t.test('compatibilidade v1.0 <-> v1.1 e validacao estrutural de signal', async () => {
      assert.equal(interpretarPedidoEntrada({ roomName: 'sala-x', platform: 'browser' }), null);
      assert.equal(interpretarPedidoEntrada({ roomName: 'sala-x', protocolVersion: 'abc' }), null);
      assert.equal(validarPayloadSinal({ type: 'offer' }), false);
      assert.equal(validarPayloadSinal({ type: 'offer', sdp: { type: 'answer', sdp: 'v=0' } }), false);
      assert.equal(validarPayloadSinal({ type: 'video-state', enabled: 'sim' }), false);
      assert.equal(validarPayloadSinal({ type: 'renegotiate', iceRestart: true }), true);

      const sockets = await conectarSockets(url, 2);
      activeSockets.push(...sockets);
      const [p1, p2] = sockets;

      try {
        const joined1 = await emitAndWait(
          p1,
          'join-room',
          { roomName: 'sala-v11', protocolVersion: '1.1', platform: 'android' },
          'joined-room',
        );
        assert.deepEqual(joined1, { roomName: 'sala-v11', isFirstPeer: true, protocolVersion: '1.1' });

        const joined2 = await emitAndWait(p2, 'join-room', 'sala-v11', 'joined-room');
        assert.deepEqual(joined2, { roomName: 'sala-v11', isFirstPeer: false });

        let recebidoInvalido = false;
        p2.on('signal', (msg) => {
          if (msg.type !== 'ready' && msg.type !== 'offer' && msg.type !== 'renegotiate') {
            recebidoInvalido = true;
          }
        });

        p1.emit('signal', { roomName: 'sala-v11', data: { type: 'offer' } });
        p1.emit('signal', { roomName: 'sala-v11', data: { type: 'offer', sdp: { type: 'answer', sdp: 'v=0' } } });
        p1.emit('signal', { roomName: 'sala-v11', data: { type: 'candidate', candidate: { candidate: 123 } } });
        p1.emit('signal', { roomName: 'sala-v11', data: { type: 'video-state', enabled: 'sim' } });
        p1.emit('signal', { roomName: 'sala-v11', data: { type: 'renegotiate', iceRestart: 'sim' } });
        p1.emit('signal', { roomName: 'sala-v11', data: { type: 'offer', sdp: { type: 'offer', sdp: 'a'.repeat(132000) } } });
        await new Promise((resolve) => setTimeout(resolve, 100));
        assert.equal(recebidoInvalido, false);

        const promessaReady = once(p2, 'signal');
        p1.emit('signal', {
          roomName: 'sala-v11',
          data: { type: 'ready', protocolVersion: '1.1', capabilities: { screenAudioDataChannel: true } },
        });
        assert.deepEqual(await promessaReady, {
          type: 'ready',
          protocolVersion: '1.1',
          capabilities: { screenAudioDataChannel: true },
        });

        const promessaRenegociar = once(p1, 'signal');
        p2.emit('signal', { roomName: 'sala-v11', data: { type: 'renegotiate', iceRestart: true } });
        assert.deepEqual(await promessaRenegociar, { type: 'renegotiate', iceRestart: true });
      } finally {
        sockets.forEach((socket) => socket.disconnect());
      }
    });

    await t.test('limite de taxa bloqueia check-room e join-room abusivos', async () => {
      const sockets = await conectarSockets(url, 2);
      activeSockets.push(...sockets);
      const [criador, curioso] = sockets;

      try {
        await emitAndWait(criador, 'join-room', 'sala-protegida', 'joined-room');

        for (let i = 0; i < 12; i += 1) {
          const check = await emitAndWait(curioso, 'check-room', 'sala-protegida', 'room-check-result');
          assert.equal(check.exists, true);
        }

        const bloqueado = await emitAndWait(curioso, 'check-room', 'sala-protegida', 'room-check-result');
        assert.deepEqual(bloqueado, { roomName: 'sala-protegida', exists: false, available: false });

        for (let i = 0; i < 10; i += 1) {
          await emitAndWait(curioso, 'join-room', '', 'join-error');
        }
        const erroTaxa = await emitAndWait(curioso, 'join-room', 'sala-protegida', 'join-error');
        assert.match(erroTaxa, /Muitas tentativas/);
      } finally {
        sockets.forEach((socket) => socket.disconnect());
      }
    });
  } finally {
    activeSockets.forEach((socket) => socket.disconnect());
    await new Promise((resolve) => io.close(resolve));
  }
});
