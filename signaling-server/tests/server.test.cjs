const { test } = require('node:test');
const assert = require('node:assert/strict');
const { io: client } = require('socket.io-client');
const { server, io } = require('../server');

function once(socket, event) {
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error(`Timeout: ${event}`)), 3000);
    socket.once(event, (value) => { clearTimeout(timeout); resolve(value); });
  });
}

test('rooms, validation, relay isolation and cleanup', async () => {
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const url = `http://127.0.0.1:${server.address().port}`;
  const sockets = Array.from({ length: 3 }, () => client(url, { transports: ['websocket'], autoConnect: false }));
  const [first, second, outsider] = sockets;
  const emitAndWait = (socket, event, payload, response) => {
    const result = once(socket, response);
    socket.emit(event, payload);
    return result;
  };
  try {
    await Promise.all(sockets.map((socket) => { const ready = once(socket, 'connect'); socket.connect(); return ready; }));
    assert.match(await emitAndWait(first, 'join-room', {}, 'join-error'), /invalido/);
    assert.match(await emitAndWait(first, 'join-room', 'x'.repeat(41), 'join-error'), /invalido/);
    assert.equal((await emitAndWait(first, 'join-room', 'room-one', 'joined-room')).isFirstPeer, true);
    assert.deepEqual(await emitAndWait(second, 'check-room', 'room-one', 'room-check-result'), { roomName: 'room-one', exists: true, available: true });
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
    await new Promise((resolve) => setTimeout(resolve, 50));
    assert.equal((await emitAndWait(first, 'check-room', 'room-one', 'room-check-result')).exists, false);
    assert.equal((await fetch(`${url}/health`).then((response) => response.json())).status, 'ok');
  } finally {
    sockets.forEach((socket) => socket.disconnect());
    await new Promise((resolve) => io.close(resolve));
  }
});
