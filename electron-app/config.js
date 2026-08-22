/**
 * config.js
 * ----------
 * Unico lugar que voce precisa editar depois de hospedar o signaling
 * server (Render/Railway/etc). Fica separado de renderer.js de proposito,
 * para nao mexer na logica do WebRTC so para trocar uma URL.
 */

window.APP_CONFIG = {
  // Troque pela URL publica do seu signaling server depois do deploy.
  // Durante o desenvolvimento local, deixe como esta (server.js na porta 3000).
  SIGNALING_SERVER_URL: 'http://localhost:3000',

  // Configuracao ICE do WebRTC. STUN publico do Google resolve a maioria
  // das redes domesticas. Se, depois de testar, a chamada nunca conectar
  // entre as duas casas (comum em redes com CGNAT / NAT simetrico - bem
  // frequente no Brasil), adicione um servidor TURN aqui. Sem TURN, o STUN
  // sozinho as vezes nao é suficiente para atravessar certos roteadores.
  ICE_SERVERS: [
    { urls: 'stun:stun.l.google.com:19302' },
    { urls: 'stun:stun1.l.google.com:19302' },
    // Exemplo de como ficaria um TURN (opcional, veja o README):
    // { urls: 'turn:seu-turn-server.com:3478', username: 'user', credential: 'senha' },
  ],
};
