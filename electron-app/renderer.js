/**
 * renderer.js - Toda a logica de chamada (roda no Renderer Process)
 * --------------------------------------------------------------------
 * Sem nodeIntegration: este arquivo so enxerga `window.electronAPI`
 * (exposto pelo preload.js) e `window.APP_CONFIG` (config.js).
 *
 * Fluxo resumido:
 *  1. Usuario digita o nome da sala -> conecta no signaling server (Socket.io)
 *     e emite "join-room".
 *  2. O servidor avisa quem foi o PRIMEIRO a entrar (isFirstPeer). Quando o
 *     segundo entra, o servidor manda "peer-joined" so pro primeiro -> ele
 *     cria a offer. Isso evita "glare" (os dois ofertando ao mesmo tempo).
 *  3. Offer/answer/ICE candidates trafegam via evento "signal", so texto,
 *     nunca midia.
 *  4. Depois que a RTCPeerConnection conecta, audio/video vao 100% P2P
 *     (o signaling server nunca mais é usado ate a proxima sala/reconexao).
 */

(() => {
  const CONFIG = window.APP_CONFIG;

  // ---------------------------------------------------------------------
  // DOM
  // ---------------------------------------------------------------------
  const lobbyScreen = document.getElementById('lobby-screen');
  const callScreen = document.getElementById('call-screen');
  const joinForm = document.getElementById('join-form');
  const roomInput = document.getElementById('room-input');
  const joinButton = document.getElementById('join-button');
  const lobbyStatus = document.getElementById('lobby-status');

  const connectionDot = document.getElementById('connection-dot');
  const roomLabel = document.getElementById('room-label');
  const callStatus = document.getElementById('call-status');

  const remoteVideo = document.getElementById('remote-video');
  const remoteTile = document.getElementById('remote-tile');
  const localVideo = document.getElementById('local-video');
  const localTile = document.getElementById('local-tile');
  const localTileLabel = document.getElementById('local-tile-label');

  const btnMute = document.getElementById('btn-mute');
  const btnCamera = document.getElementById('btn-camera');
  const btnShare = document.getElementById('btn-share');
  const btnLeave = document.getElementById('btn-leave');

  const sourceModal = document.getElementById('source-modal');
  const sourceModalClose = document.getElementById('source-modal-close');
  const sourceGrid = document.getElementById('source-grid');

  const toastEl = document.getElementById('toast');

  // ---------------------------------------------------------------------
  // Estado
  // ---------------------------------------------------------------------
  let localStream = null;
  let cameraTrack = null; // guardamos a track da webcam separada pra poder voltar dela apos o compartilhamento de tela
  let screenStream = null;
  let pc = null;
  let currentRoom = null;
  let isInitiator = false;
  let hasJoinedOnce = false;
  let pendingCandidates = [];
  let pendingSignals = [];
  let pendingPeerJoined = false;
  let hasAnnouncedReady = false;
  let remotePeerReady = false;
  let processingSignals = false;
  let videoTransceiver = null;
  let offerRetryTimer = null;
  let iceFailureTimer = null;
  let toastTimer = null;
  let reconnectTimer = null;

  // ---------------------------------------------------------------------
  // Utilitarios de UI
  // ---------------------------------------------------------------------
  function showToast(message, tone = 'info', duration = 4000) {
    clearTimeout(toastTimer);
    toastEl.textContent = message;
    toastEl.dataset.tone = tone;
    toastEl.hidden = false;
    toastTimer = setTimeout(() => { toastEl.hidden = true; }, duration);
  }

  function setLobbyStatus(message, tone = 'info') {
    lobbyStatus.textContent = message || '';
    lobbyStatus.dataset.tone = tone;
  }

  function setConnectionState(state) {
    // state: 'connecting' | 'connected' | 'lost'
    connectionDot.className = `conn-dot conn-${state === 'connected' ? 'connected' : state === 'lost' ? 'lost' : 'connecting'}`;
    callStatus.textContent = {
      connecting: 'conectando...',
      connected: 'conectado',
      lost: 'conexao instavel - tentando reconectar...',
    }[state] || '';
  }

  function goToCallScreen(roomName) {
    lobbyScreen.hidden = true;
    callScreen.hidden = false;
    roomLabel.textContent = roomName;
  }

  function goToLobbyScreen() {
    callScreen.hidden = true;
    lobbyScreen.hidden = false;
    joinButton.disabled = false;
    setLobbyStatus('');
  }

  // ---------------------------------------------------------------------
  // Midia local + indicador de "quem esta falando"
  // (Web Audio API - AnalyserNode - e a unica animacao com proposito do app)
  // ---------------------------------------------------------------------
  function attachSpeakingIndicator(stream, tileEl) {
    try {
      const audioCtx = new (window.AudioContext || window.webkitAudioContext)();
      const source = audioCtx.createMediaStreamSource(stream);
      const analyser = audioCtx.createAnalyser();
      analyser.fftSize = 512;
      analyser.smoothingTimeConstant = 0.8;
      source.connect(analyser);

      const data = new Uint8Array(analyser.frequencyBinCount);
      const SPEAKING_THRESHOLD = 14; // ajuste fino se estiver sensivel demais/de menos

      function tick() {
        analyser.getByteFrequencyData(data);
        const avg = data.reduce((sum, v) => sum + v, 0) / data.length;
        tileEl.classList.toggle('is-speaking', avg > SPEAKING_THRESHOLD);
        requestAnimationFrame(tick);
      }
      tick();
    } catch (err) {
      // Web Audio falhando nao pode derrubar a chamada - so perdemos o efeito visual.
      console.warn('Nao foi possivel iniciar o indicador de fala:', err);
    }
  }

  async function startLocalMedia() {
    const audioConstraints = {
      echoCancellation: true,
      noiseSuppression: true,
      autoGainControl: true,
    };

    try {
      localStream = await navigator.mediaDevices.getUserMedia({
        audio: audioConstraints,
        video: { width: { ideal: 1280 }, height: { ideal: 720 } },
      });
    } catch (videoError) {
      // Duas instancias no mesmo PC podem disputar a mesma camera.
      // Mantemos o audio para a chamada continuar mesmo sem video local.
      console.warn('Camera indisponivel; tentando iniciar somente o audio:', videoError);
      try {
        localStream = await navigator.mediaDevices.getUserMedia({ audio: audioConstraints, video: false });
        showToast('Camera ocupada. A chamada continuara somente com audio nesta janela.', 'info', 7000);
      } catch (audioError) {
        console.error('Nao foi possivel acessar camera/microfone:', audioError);
        throw audioError;
      }
    }

    cameraTrack = localStream.getVideoTracks()[0];
    localVideo.srcObject = localStream;
    attachSpeakingIndicator(localStream, localTile);
  }

  // ---------------------------------------------------------------------
  // Sinalizacao (Socket.io via wrapper exposto no preload)
  // ---------------------------------------------------------------------
  function sendSignal(data) {
    window.electronAPI.socketEmit('signal', { roomName: currentRoom, data });
  }

  function serializeDescription(description) {
    return description ? description.toJSON() : null;
  }

  async function renegotiateConnection() {
    if (!pc || !isInitiator || pc.signalingState !== 'stable') return;
    try {
      const offer = await pc.createOffer();
      await pc.setLocalDescription(offer);
      sendSignal({ type: 'offer', sdp: serializeDescription(pc.localDescription) });
    } catch (err) {
      console.error('Erro renegociando midia:', err);
      showToast(`Falha ao atualizar a midia: ${err.message || err}`, 'error', 9000);
    }
  }

  function wireSocketEvents() {
    window.electronAPI.socketOn('connect', () => {
      // Cobre tanto a primeira conexao quanto reconexoes automaticas do
      // socket.io depois de uma queda de rede (o server nao lembra do
      // socket.id antigo, entao precisamos re-anunciar a sala).
      if (currentRoom && hasJoinedOnce) {
        window.electronAPI.socketEmit('join-room', currentRoom);
      }
    });

    window.electronAPI.socketOn('disconnect', () => {
      if (hasJoinedOnce) setConnectionState('lost');
    });

    window.electronAPI.socketOn('connect_error', (err) => {
      setLobbyStatus('Nao foi possivel conectar ao servidor.', 'error');
      joinButton.disabled = false;
      showToast(`Erro de conexao: ${err?.message || 'verifique sua internet.'}`, 'error', 6000);
    });

    window.electronAPI.socketOn('join-error', (message) => {
      setLobbyStatus(message, 'error');
      joinButton.disabled = false;
    });

    window.electronAPI.socketOn('room-full', () => {
      setLobbyStatus('Essa sala ja tem 2 pessoas.', 'error');
      joinButton.disabled = false;
      window.electronAPI.socketDisconnect();
    });

    window.electronAPI.socketOn('joined-room', async ({ roomName, isFirstPeer }) => {
      currentRoom = roomName;
      isInitiator = isFirstPeer;
      hasJoinedOnce = true;

      if (!pc) {
        // Primeira vez entrando nesta sessao do app: pega a midia local
        // e cria a PeerConnection. Em reconexoes, so re-anunciamos a sala
        // (o pc so e recriado se realmente caiu, ver onconnectionstatechange).
        goToCallScreen(roomName);
        try {
          await startLocalMedia();
        } catch (err) {
          showToast('Nao foi possivel acessar camera/microfone. Verifique as permissoes.', 'error', 6000);
          console.error(err);
          return;
        }
        createPeerConnection();
        await drainSignals();

        if (pendingPeerJoined && isInitiator) {
          pendingPeerJoined = false;
          await createOffer();
        }

        if (!hasAnnouncedReady) {
          hasAnnouncedReady = true;
          sendSignal({ type: 'ready' });
        }

      }

      setConnectionState('connecting');
      setLobbyStatus(isFirstPeer ? 'Sala criada. Esperando a outra pessoa entrar...' : 'Entrando...', 'ok');
    });

    window.electronAPI.socketOn('peer-joined', async () => {
      pendingPeerJoined = true;
      if (pc && isInitiator) {
        pendingPeerJoined = false;
        await createOffer();
      }
    });

    async function createOffer() {
      if (!pc) return;
      if (pc.signalingState !== 'stable') return;
      try {
        clearTimeout(offerRetryTimer);
        const offer = await pc.createOffer();
        await pc.setLocalDescription(offer);
        sendSignal({ type: 'offer', sdp: serializeDescription(pc.localDescription) });
      } catch (err) {
        console.error('Erro criando offer:', err);
      }
    }

    window.electronAPI.socketOn('signal', async (data) => {
      pendingSignals.push(data);
      await drainSignals();
    });

    async function drainSignals() {
      if (processingSignals || !pc) return;
      processingSignals = true;
      try {
        while (pc && pendingSignals.length) {
          await processSignal(pendingSignals.shift());
        }
      } finally {
        processingSignals = false;
      }
    }

    async function processSignal(data) {
      if (!pc) return;

      try {
        if (data.type === 'offer') {
          await pc.setRemoteDescription(new RTCSessionDescription(data.sdp));
          await flushPendingCandidates();
          const answer = await pc.createAnswer();
          await pc.setLocalDescription(answer);
          sendSignal({ type: 'answer', sdp: serializeDescription(pc.localDescription) });
        } else if (data.type === 'answer') {
          await pc.setRemoteDescription(new RTCSessionDescription(data.sdp));
          await flushPendingCandidates();
        } else if (data.type === 'ready' && isInitiator) {
          await createOffer();
        } else if (data.type === 'candidate' && data.candidate) {
          if (pc.remoteDescription && pc.remoteDescription.type) {
            await pc.addIceCandidate(new RTCIceCandidate(data.candidate));
          } else {
            pendingCandidates.push(data.candidate);
          }
        } else if (data.type === 'screen-share-start') {
          remoteTile.classList.add('screen-share');
        } else if (data.type === 'screen-share-stop') {
          remoteTile.classList.remove('screen-share');
        } else if (data.type === 'renegotiate' && isInitiator) {
          await renegotiateConnection();
        }
      } catch (err) {
        console.error('Erro processando sinal:', data.type, err);
        showToast(`Falha no sinal ${data.type}: ${err.message || err}`, 'error', 9000);
      }
    }

    window.electronAPI.socketOn('peer-left', () => {
      showToast('A outra pessoa saiu da sala.', 'info');
      remoteVideo.srcObject = null;
      remoteVideo.dataset.empty = 'true';
      remoteTile.classList.remove('is-speaking');
      setConnectionState('connecting');
      callStatus.textContent = 'esperando a outra pessoa voltar...';
      // Mantemos nossa PeerConnection e midia local de pe: quando ela
      // reentrar na mesma sala, o servidor manda "peer-joined" de novo
      // e o handshake recomeca (pc antigo e recriado por seguranca).
      if (pc) {
        pc.close();
        pc = null;
        createPeerConnection();
      }
    });
  }

  async function flushPendingCandidates() {
    while (pendingCandidates.length) {
      const candidate = pendingCandidates.shift();
      try {
        await pc.addIceCandidate(new RTCIceCandidate(candidate));
      } catch (err) {
        console.error('Erro aplicando ICE candidate pendente:', err);
      }
    }
  }

  // ---------------------------------------------------------------------
  // RTCPeerConnection
  // ---------------------------------------------------------------------
  function createPeerConnection() {
    pc = new RTCPeerConnection({
      iceServers: CONFIG.ICE_SERVERS,
      iceCandidatePoolSize: 10,
    });

    // Reanexa as tracks locais atuais (camera ligada/desligada, tela
    // compartilhada ou nao) a nova conexao.
    localStream.getTracks().forEach((track) => pc.addTrack(track, localStream));
    videoTransceiver = pc.getTransceivers().find((transceiver) => transceiver.sender.track?.kind === 'video');
    if (!videoTransceiver) videoTransceiver = pc.addTransceiver('video', { direction: 'sendrecv' });

    pc.onicecandidate = (event) => {
      if (event.candidate) {
        sendSignal({ type: 'candidate', candidate: event.candidate.toJSON() });
      }
    };

    pc.ontrack = (event) => {
      const stream = event.streams[0] || new MediaStream([event.track]);
      remoteVideo.srcObject = stream;
      remoteVideo.dataset.empty = 'false';
      attachSpeakingIndicator(stream, remoteTile);
    };

    pc.onconnectionstatechange = () => {
      if (!pc) return;
      console.log('[pc] connectionState:', pc.connectionState);

      if (pc.connectionState === 'connected') {
        clearTimeout(reconnectTimer);
        clearTimeout(offerRetryTimer);
        clearTimeout(iceFailureTimer);
        setConnectionState('connected');
      } else if (pc.connectionState === 'disconnected') {
        setConnectionState('lost');
        // Rede "piscou" - da um tempo pra reconectar sozinho antes de
        // forcar um ICE restart (evita restart desnecessario em blips curtos).
        clearTimeout(reconnectTimer);
        reconnectTimer = setTimeout(() => attemptIceRestart(), 3000);
      } else if (pc.connectionState === 'failed') {
        setConnectionState('lost');
        attemptIceRestart();
      }
    };

    pc.oniceconnectionstatechange = () => {
      if (!pc) return;
      if (pc.iceConnectionState === 'connected' || pc.iceConnectionState === 'completed') {
        clearTimeout(iceFailureTimer);
      } else if (pc.iceConnectionState === 'failed') {
        showToast('A rede bloqueou a conexao direta. Tentando novamente...', 'error', 7000);
        attemptIceRestart();
      } else if (pc.iceConnectionState === 'checking') {
        clearTimeout(iceFailureTimer);
        iceFailureTimer = setTimeout(() => {
          if (pc && pc.iceConnectionState === 'checking') {
            showToast('A conexao esta bloqueada pela rede. Tente outra rede.', 'error', 9000);
          }
        }, 12000);
      }
    };

    return pc;
  }

  async function attemptIceRestart() {
    if (!pc || !isInitiator) return; // so quem criou a offer original reinicia o ICE
    if (pc.connectionState === 'connected') return;
    try {
      const offer = await pc.createOffer({ iceRestart: true });
      await pc.setLocalDescription(offer);
      sendSignal({ type: 'offer', sdp: pc.localDescription });
    } catch (err) {
      console.error('Erro no ICE restart:', err);
    }
  }

  // ---------------------------------------------------------------------
  // Controles: mutar, camera, compartilhar tela, sair
  // ---------------------------------------------------------------------
  btnMute.addEventListener('click', () => {
    if (!localStream) return;
    const track = localStream.getAudioTracks()[0];
    if (!track) return;
    track.enabled = !track.enabled;
    btnMute.setAttribute('aria-pressed', String(!track.enabled));
  });

  btnCamera.addEventListener('click', () => {
    if (!cameraTrack) return;
    cameraTrack.enabled = !cameraTrack.enabled;
    btnCamera.setAttribute('aria-pressed', String(!cameraTrack.enabled));
  });

  btnShare.addEventListener('click', async () => {
    if (screenStream) {
      stopScreenShare();
    } else {
      await openSourcePicker();
    }
  });

  btnLeave.addEventListener('click', () => leaveCall());

  async function openSourcePicker() {
    let sources;
    try {
      sources = await window.electronAPI.getScreenSources();
    } catch (err) {
      showToast('Nao foi possivel listar telas/janelas.', 'error');
      console.error(err);
      return;
    }

    sourceGrid.innerHTML = '';
    sources.forEach((source) => {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'source-option';
      btn.innerHTML = `
        <img src="${source.thumbnailDataUrl}" alt="" />
        <span class="source-option-name">${escapeHtml(source.name)}</span>
      `;
      btn.addEventListener('click', () => {
        closeSourcePicker();
        startScreenShare(source.id);
      });
      sourceGrid.appendChild(btn);
    });

    sourceModal.hidden = false;
  }

  function closeSourcePicker() {
    sourceModal.hidden = true;
    sourceGrid.innerHTML = '';
  }

  sourceModalClose.addEventListener('click', closeSourcePicker);
  sourceModal.addEventListener('click', (e) => {
    if (e.target === sourceModal) closeSourcePicker();
  });
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && !sourceModal.hidden) closeSourcePicker();
  });

  async function startScreenShare(sourceId) {
    try {
      screenStream = await navigator.mediaDevices.getUserMedia({
        audio: false,
        video: {
          mandatory: {
            chromeMediaSource: 'desktop',
            chromeMediaSourceId: sourceId,
            maxWidth: 1920,
            maxHeight: 1080,
            maxFrameRate: 30,
          },
        },
      });
    } catch (err) {
      showToast('Nao foi possivel iniciar o compartilhamento de tela.', 'error');
      console.error(err);
      return;
    }

    const screenTrack = screenStream.getVideoTracks()[0];

    // O coracao da troca: substitui a track de video no sender existente,
    // sem precisar renegociar a offer/answer.
    const sender = pc && (videoTransceiver?.sender || pc.getSenders().find((s) => s.track?.kind === 'video'));
    if (sender) await sender.replaceTrack(screenTrack);

    // Preview local passa a mostrar a tela compartilhada.
    localVideo.srcObject = screenStream;
    localTile.classList.add('screen-share');
    localTileLabel.textContent = 'sua tela';
    sendSignal({ type: 'screen-share-start' });

    btnShare.setAttribute('aria-pressed', 'true');
    sendSignal({ type: 'renegotiate' });
    await renegotiateConnection();

    // Se o usuario parar o compartilhamento pelo controle nativo do SO
    // (ex: barra "Parar de compartilhar" do Windows/macOS), voltamos pra
    // camera automaticamente.
    screenTrack.addEventListener('ended', () => stopScreenShare());
  }

  async function stopScreenShare() {
    if (!screenStream) return;

    screenStream.getTracks().forEach((t) => t.stop());
    screenStream = null;

    const sender = pc && (videoTransceiver?.sender || pc.getSenders().find((s) => s.track?.kind === 'video'));
    if (sender && cameraTrack) await sender.replaceTrack(cameraTrack);

    localVideo.srcObject = localStream;
    localTile.classList.remove('screen-share');
    localTileLabel.textContent = 'voce';
    btnShare.setAttribute('aria-pressed', 'false');
    sendSignal({ type: 'screen-share-stop' });
    sendSignal({ type: 'renegotiate' });
    await renegotiateConnection();
  }

  function leaveCall() {
    window.electronAPI.socketEmit('leave-room');
    window.electronAPI.socketDisconnect();

    if (screenStream) screenStream.getTracks().forEach((t) => t.stop());
    if (localStream) localStream.getTracks().forEach((t) => t.stop());
    if (pc) pc.close();

    pc = null;
    localStream = null;
    cameraTrack = null;
    screenStream = null;
    currentRoom = null;
    isInitiator = false;
    hasJoinedOnce = false;
    pendingCandidates = [];
    pendingSignals = [];
    pendingPeerJoined = false;
    remotePeerReady = false;
    processingSignals = false;
    videoTransceiver = null;
    clearTimeout(offerRetryTimer);
    clearTimeout(iceFailureTimer);
    remoteVideo.srcObject = null;
    remoteVideo.dataset.empty = 'true';
    localVideo.srcObject = null;
    btnMute.setAttribute('aria-pressed', 'false');
    btnCamera.setAttribute('aria-pressed', 'false');
    btnShare.setAttribute('aria-pressed', 'false');
    localTile.classList.remove('screen-share');
    localTileLabel.textContent = 'voce';
    remoteTile.classList.remove('screen-share');

    goToLobbyScreen();
  }

  // ---------------------------------------------------------------------
  // Lobby: entrar na sala
  // ---------------------------------------------------------------------
  joinForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const roomName = roomInput.value.trim();
    if (!roomName) return;

    joinButton.disabled = true;
    setLobbyStatus('Conectando ao servidor...', 'info');

    // IMPORTANTE: socketConnect() precisa vir antes de wireSocketEvents(),
    // porque o wrapper exposto pelo preload so consegue registrar listeners
    // (socketOn) depois que o objeto `socket` existe do lado do preload.
    currentRoom = roomName;
    window.electronAPI.socketConnect(CONFIG.SIGNALING_SERVER_URL);
    wireSocketEvents();
    // O Socket.IO enfileira este evento se a conexao ainda estiver abrindo.
    window.electronAPI.socketEmit('join-room', roomName);
  });

  function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  }

  // Permissoes de camera/microfone no macOS (no-op no Windows).
  window.electronAPI.checkMediaPermissions().catch(() => {});
})();
