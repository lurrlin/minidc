const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '../..');
const socketIoClientPath = require.resolve('socket.io-client', {
  paths: [path.join(repoRoot, 'signaling-server'), path.join(repoRoot, 'electron-app')],
});
const { io: client } = require(socketIoClientPath);
const { server, io, validarPayloadSinal } = require(path.join(repoRoot, 'signaling-server', 'server'));

const constants = JSON.parse(
  fs.readFileSync(path.join(repoRoot, 'protocol', 'constants.json'), 'utf8'),
);
const signalFixtures = JSON.parse(
  fs.readFileSync(path.join(repoRoot, 'protocol', 'fixtures', 'signaling-messages.json'), 'utf8'),
);

function once(socket, event) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`Timeout aguardando evento "${event}"`)), 4000);
    socket.once(event, (value) => {
      clearTimeout(timer);
      resolve(value);
    });
  });
}

function emitAndWait(socket, event, payload, expectedResponse) {
  const promise = once(socket, expectedResponse);
  socket.emit(event, payload);
  return promise;
}

async function connectClients(url, count) {
  const sockets = Array.from({ length: count }, () => (
    client(url, { transports: ['websocket'], autoConnect: false, reconnection: false })
  ));
  await Promise.all(sockets.map((socket) => {
    const ready = once(socket, 'connect');
    socket.connect();
    return ready;
  }));
  return sockets;
}

test('interoperabilidade multiplataforma Desktop <-> Android no signaling server', async (t) => {
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const url = `http://127.0.0.1:${server.address().port}`;
  const activeSockets = [];

  try {
    await t.test('fixtures compartilhadas signaling-messages.json alinham com validarPayloadSinal', () => {
      for (const signal of signalFixtures.validSignals) {
        assert.equal(validarPayloadSinal(signal), true, `Esperado valido: ${JSON.stringify(signal)}`);
      }
      for (const signal of signalFixtures.invalidSignals) {
        assert.equal(validarPayloadSinal(signal), false, `Esperado invalido: ${JSON.stringify(signal)}`);
      }
    });

    await t.test('handshake completo Desktop v1.1 (iniciador) <-> Android v1.1 (convidado) e sinais de tela', async () => {
      const sockets = await connectClients(url, 3);
      activeSockets.push(...sockets);
      const [desktop, android, intruder] = sockets;
      const roomName = 'interop-hand-1';

      try {
        const joinedDesktop = await emitAndWait(
          desktop,
          'join-room',
          { roomName, protocolVersion: constants.protocolVersion.target, platform: 'desktop' },
          'joined-room',
        );
        assert.deepEqual(joinedDesktop, {
          roomName,
          isFirstPeer: true,
          protocolVersion: constants.protocolVersion.target,
        });

        const check = await emitAndWait(android, 'check-room', roomName, 'room-check-result');
        assert.deepEqual(check, { roomName, exists: true, available: true });

        const peerJoinedPromise = once(desktop, 'peer-joined');
        const joinedAndroid = await emitAndWait(
          android,
          'join-room',
          { roomName, protocolVersion: constants.protocolVersion.target, platform: 'android' },
          'joined-room',
        );
        assert.deepEqual(joinedAndroid, {
          roomName,
          isFirstPeer: false,
          protocolVersion: constants.protocolVersion.target,
        });
        await peerJoinedPromise;

        const fullRoom = await emitAndWait(intruder, 'join-room', roomName, 'room-full');
        assert.equal(fullRoom, roomName);

        const readyOnDesktop = once(desktop, 'signal');
        android.emit('signal', {
          roomName,
          data: {
            type: 'ready',
            protocolVersion: '1.1',
            capabilities: { screenAudioDataChannel: true, iceRestartRequest: true },
          },
        });
        assert.equal((await readyOnDesktop).type, 'ready');

        const offerOnAndroid = once(android, 'signal');
        desktop.emit('signal', {
          roomName,
          data: { type: 'offer', sdp: { type: 'offer', sdp: 'v=0\r\nm=video\r\nm=audio\r\nm=audio\r\n' } },
        });
        assert.equal((await offerOnAndroid).sdp.type, 'offer');

        const answerOnDesktop = once(desktop, 'signal');
        android.emit('signal', {
          roomName,
          data: { type: 'answer', sdp: { type: 'answer', sdp: 'v=0\r\na=mid:0\r\n' } },
        });
        assert.equal((await answerOnDesktop).sdp.type, 'answer');

        const iceOnDesktop = once(desktop, 'signal');
        android.emit('signal', {
          roomName,
          data: {
            type: 'candidate',
            candidate: {
              candidate: 'candidate:1 1 UDP 2122252543 192.168.1.20 50000 typ host',
              sdpMid: '0',
              sdpMLineIndex: 0,
            },
          },
        });
        assert.equal((await iceOnDesktop).candidate.sdpMid, '0');

        const shareStartOnDesktop = once(desktop, 'signal');
        android.emit('signal', { roomName, data: { type: 'screen-share-start' } });
        assert.equal((await shareStartOnDesktop).type, 'screen-share-start');

        const videoStateOnDesktop = once(desktop, 'signal');
        android.emit('signal', { roomName, data: { type: 'video-state', enabled: true } });
        assert.deepEqual(await videoStateOnDesktop, { type: 'video-state', enabled: true });

        const shareStopOnDesktop = once(desktop, 'signal');
        android.emit('signal', { roomName, data: { type: 'screen-share-stop' } });
        assert.equal((await shareStopOnDesktop).type, 'screen-share-stop');
      } finally {
        sockets.forEach((s) => s.disconnect());
      }
    });

    await t.test('pedido de iceRestart pelo convidado e promocao apos peer-left', async () => {
      const sockets = await connectClients(url, 3);
      activeSockets.push(...sockets);
      const [criadorV1, convidadoAndroid, reentrante] = sockets;
      const roomName = 'interop-reconn-1';

      try {
        const joinedCriador = await emitAndWait(criadorV1, 'join-room', roomName, 'joined-room');
        assert.deepEqual(joinedCriador, { roomName, isFirstPeer: true });

        const peerJoined = once(criadorV1, 'peer-joined');
        const joinedAndroid = await emitAndWait(
          convidadoAndroid,
          'join-room',
          { roomName, protocolVersion: '1.1', platform: 'android' },
          'joined-room',
        );
        assert.deepEqual(joinedAndroid, { roomName, isFirstPeer: false, protocolVersion: '1.1' });
        await peerJoined;

        const renegotiateOnCreator = once(criadorV1, 'signal');
        convidadoAndroid.emit('signal', {
          roomName,
          data: { type: 'renegotiate', iceRestart: true },
        });
        assert.deepEqual(await renegotiateOnCreator, { type: 'renegotiate', iceRestart: true });

        const restartOfferOnAndroid = once(convidadoAndroid, 'signal');
        criadorV1.emit('signal', {
          roomName,
          data: { type: 'offer', sdp: { type: 'offer', sdp: 'v=0\r\na=ice-pwd:novo\r\n' } },
        });
        assert.match((await restartOfferOnAndroid).sdp.sdp, /ice-pwd:novo/);

        const peerLeftOnAndroid = once(convidadoAndroid, 'peer-left');
        criadorV1.emit('leave-room');
        await peerLeftOnAndroid;

        const peerJoinedOnPromoted = once(convidadoAndroid, 'peer-joined');
        const joinedReentrante = await emitAndWait(
          reentrante,
          'join-room',
          { roomName, protocolVersion: '1.1', platform: 'desktop' },
          'joined-room',
        );
        assert.equal(joinedReentrante.isFirstPeer, false);
        await peerJoinedOnPromoted;
      } finally {
        sockets.forEach((s) => s.disconnect());
      }
    });
  } finally {
    activeSockets.forEach((s) => s.disconnect());
    await new Promise((resolve) => io.close(resolve));
  }
});
