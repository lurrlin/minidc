
window.APP_CONFIG = {
  SIGNALING_SERVER_URL: 'https://minidc-vn9a.onrender.com',

  ICE_SERVERS: [
    { urls: 'stun:stun.l.google.com:19302' },
    { urls: 'stun:stun1.l.google.com:19302' },
    // Exemplo de como ficaria um TURN (opcional, veja o README):
    // { urls: 'turn:seu-turn-server.com:3478', username: 'user', credential: 'senha' },
  ],
};
