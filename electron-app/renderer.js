
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
  const createRoomButton = document.getElementById('create-room-button');
  const showJoinButton = document.getElementById('show-join-button');
  const backToLobbyButton = document.getElementById('back-to-lobby-button');
  const createdRoom = document.getElementById('created-room');
  const createdRoomCode = document.getElementById('created-room-code');
  const copyRoomButton = document.getElementById('copy-room-button');
  const lobbyStatus = document.getElementById('lobby-status');

  const connectionDot = document.getElementById('connection-dot');
  const roomLabel = document.getElementById('room-label');
  const activeRoomCode = document.getElementById('active-room-code');
  const copyActiveRoomButton = document.getElementById('copy-active-room');
  const callStatus = document.getElementById('call-status');
  const stage = document.querySelector('.stage');

  const remoteVideo = document.getElementById('remote-video');
  const remoteAudio = document.getElementById('remote-audio');
  const remoteTile = document.getElementById('remote-tile');
  const localVideo = document.getElementById('local-video');
  const localTile = document.getElementById('local-tile');
  const localTileLabel = document.getElementById('local-tile-label');
  const btnFullscreen = document.getElementById('btn-fullscreen');
  const btnRemoteMute = document.getElementById('btn-remote-mute');
  const remoteContextMenu = document.getElementById('remote-context-menu');

  const btnMute = document.getElementById('btn-mute');
  const btnCamera = document.getElementById('btn-camera');
  const btnShare = document.getElementById('btn-share');
  const btnLeave = document.getElementById('btn-leave');
  const remoteVolume = document.getElementById('remote-volume');
  const remoteVolumeValue = document.getElementById('remote-volume-value');
  const settingsModal = document.getElementById('settings-modal');
  const openSettingsButton = document.getElementById('open-settings');
  const settingsCloseButton = document.getElementById('settings-close');
  const settingsMicrophone = document.getElementById('settings-microphone');
  const settingsOutput = document.getElementById('settings-output');
  const settingsInputVolume = document.getElementById('settings-input-volume');
  const settingsOutputVolume = document.getElementById('settings-output-volume');
  const settingsInputValue = document.getElementById('settings-input-value');
  const settingsOutputValue = document.getElementById('settings-output-value');
  const settingsNoiseSuppression = document.getElementById('settings-noise-suppression');
  const settingsEchoCancellation = document.getElementById('settings-echo-cancellation');
  const settingsAutoGain = document.getElementById('settings-auto-gain');

  const sourceModal = document.getElementById('source-modal');
  const sourceModalClose = document.getElementById('source-modal-close');
  const sourceGrid = document.getElementById('source-grid');
  const includeSystemAudio = document.getElementById('include-system-audio');
  const stopScreenShareButton = document.getElementById('stop-screen-share');

  const toastEl = document.getElementById('toast');

  // ---------------------------------------------------------------------
  // Estado
  // ---------------------------------------------------------------------
  let localStream = null;
  let cameraTrack = null; // guardamos a track da webcam separada pra poder voltar dela apos o compartilhamento de tela
  let screenStream = null;
  let screenAudioTrack = null;
  let screenAudioTransceiver = null;
  let nativeAudioContext = null;
  let nativeAudioNode = null;
  let nativeAudioDestination = null;
  let nativeAudioCleanup = null;
  let remoteMixer = null;
  const speakingIndicators = new Map();
  let joining = false;
  let pc = null;
  let currentRoom = null;
  let isInitiator = false;
  let hasJoinedOnce = false;
  let pendingCandidates = [];
  let pendingSignals = [];
  let pendingPeerJoined = false;
  let remotePeerReady = false;
  let processingSignals = false;
  let videoTransceiver = null;
  let iceFailureTimer = null;
  let renegotiationQueued = false;
  let renegotiationInProgress = false;
  let toastTimer = null;
  let reconnectTimer = null;
  let roomCheckResolver = null;
  let selectedMicrophoneId = '';
  let microphoneSender = null;
  let microphoneGainContext = null;
  let microphoneGainNode = null;
  let microphoneGainDestination = null;

  function getMicrophoneConstraints(deviceId = selectedMicrophoneId) {
    return {
      ...(deviceId ? { deviceId: { exact: deviceId } } : {}),
      echoCancellation: settingsEchoCancellation.checked,
      noiseSuppression: settingsNoiseSuppression.checked,
      autoGainControl: settingsAutoGain.checked,
    };
  }

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

  function ensureRemoteMixer() {
    if (!remoteMixer) remoteMixer = new window.CallMedia.RemoteAudioMixer(remoteAudio);
    return remoteMixer;
  }

  async function applyRemoteVolume() {
    const value = remoteVolume.value;
    settingsOutputVolume.value = value;
    for (const output of [remoteVolumeValue, settingsOutputValue]) output.textContent = value + '%';
    try {
      await ensureRemoteMixer().setVolume(value);
    } catch (error) {
      console.warn('Falha ao iniciar a saída de áudio:', error);
      showToast('Não foi possível iniciar o áudio. Tente ajustar o volume novamente.', 'error');
    }
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
    activeRoomCode.textContent = roomName;
  }

  function goToLobbyScreen() {
    callScreen.hidden = true;
    lobbyScreen.hidden = false;
    joinButton.disabled = false;
    setLobbyStatus('');
    resetLobbyMode();
  }

  function setJoining(value) {
    joining = value;
    for (const button of [joinButton, createRoomButton, showJoinButton]) button.disabled = value;
  }

  function resetLobbyMode() {
    joinForm.hidden = true;
    createRoomButton.hidden = false;
    showJoinButton.hidden = false;
    createdRoom.hidden = true;
    roomInput.value = '';
    createdRoomCode.textContent = '';
  }

  function updateTileLayout() {
    const compact = !remoteTile.classList.contains('has-video') && !localTile.classList.contains('has-video');
    stage.classList.toggle('compact-layout', compact);
  }

  updateTileLayout();

  function generateRoomCode() {
    const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%&*+-=';
    const values = new Uint32Array(16);
    crypto.getRandomValues(values);
    return Array.from(values, (value) => alphabet[value % alphabet.length]).join('');
  }

  async function copyRoomCode() {
    try {
      await navigator.clipboard.writeText(createdRoomCode.textContent || activeRoomCode.textContent);
      showToast('Codigo copiado.', 'info', 2500);
    } catch (err) {
      showToast('Nao foi possivel copiar o codigo.', 'error');
    }
  }

  // ---------------------------------------------------------------------
  // Midia local + indicador de "quem esta falando"
  // (Web Audio API - AnalyserNode - e a unica animacao com proposito do app)
  // ---------------------------------------------------------------------
  function attachSpeakingIndicator(stream, tileEl) {
    speakingIndicators.get(tileEl)?.();
    try {
      const audioCtx = new (window.AudioContext || window.webkitAudioContext)();
      const source = audioCtx.createMediaStreamSource(stream);
      const analyser = audioCtx.createAnalyser();
      analyser.fftSize = 512;
      analyser.smoothingTimeConstant = 0.8;
      source.connect(analyser);

      const data = new Uint8Array(analyser.frequencyBinCount);
      const SPEAKING_THRESHOLD = 14; // ajuste fino se estiver sensivel demais/de menos

      let frame;
      speakingIndicators.set(tileEl, () => {
        cancelAnimationFrame(frame);
        source.disconnect();
        audioCtx.close().catch(() => {});
        tileEl.classList.remove('is-speaking');
      });
      function tick() {
        analyser.getByteFrequencyData(data);
        const avg = data.reduce((sum, v) => sum + v, 0) / data.length;
        tileEl.classList.toggle('is-speaking', avg > SPEAKING_THRESHOLD);
        frame = requestAnimationFrame(tick);
      }
      tick();
    } catch (err) {
      // Web Audio falhando nao pode derrubar a chamada - so perdemos o efeito visual.
      console.warn('Nao foi possivel iniciar o indicador de fala:', err);
    }
  }

  async function startLocalMedia() {
    const audioConstraints = getMicrophoneConstraints();

    try {
      localStream = await navigator.mediaDevices.getUserMedia({
        audio: audioConstraints,
        video: false,
      });
    } catch (videoError) {
      // Duas instancias no mesmo PC podem disputar a mesma camera.
      // Mantemos o audio para a chamada continuar mesmo sem video local.
      console.warn('Camera indisponivel; tentando iniciar somente o audio:', videoError);
      try {
        localStream = await navigator.mediaDevices.getUserMedia({ audio: getMicrophoneConstraints(''), video: false });
        showToast('Camera ocupada. A chamada continuara somente com audio nesta janela.', 'info', 7000);
      } catch (audioError) {
        console.error('Nao foi possivel acessar camera/microfone:', audioError);
        throw audioError;
      }
    }

    cameraTrack = null;
    localVideo.srcObject = localStream;
    attachSpeakingIndicator(localStream, localTile);
    await refreshMicrophones();
  }

  async function refreshMicrophones() {
    const devices = (await navigator.mediaDevices.enumerateDevices())
      .filter((device) => device.kind === 'audioinput');
    settingsMicrophone.innerHTML = '';
    devices.forEach((device, index) => {
      const option = document.createElement('option');
      option.value = device.deviceId;
      option.textContent = device.label || `Microfone ${index + 1}`;
      settingsMicrophone.appendChild(option);
    });
    if (selectedMicrophoneId && devices.some((device) => device.deviceId === selectedMicrophoneId)) {
      settingsMicrophone.value = selectedMicrophoneId;
    } else if (devices[0]) {
      selectedMicrophoneId = devices[0].deviceId;
      settingsMicrophone.value = selectedMicrophoneId;
    }
  }

  async function changeMicrophone() {
    if (!localStream || !settingsMicrophone.value) return;
    try {
      const nextStream = await navigator.mediaDevices.getUserMedia({
        audio: getMicrophoneConstraints(settingsMicrophone.value),
        video: false,
      });
      const nextTrack = nextStream.getAudioTracks()[0];
      await stopMicrophoneVolume();
      const currentTrack = localStream.getAudioTracks()[0];
      nextTrack.enabled = currentTrack?.enabled ?? true;
      if (microphoneSender) await microphoneSender.replaceTrack(nextTrack);
      localStream.removeTrack(currentTrack);
      currentTrack?.stop();
      localStream.addTrack(nextTrack);
      selectedMicrophoneId = settingsMicrophone.value;
      attachSpeakingIndicator(localStream, localTile);
      await applyMicrophoneVolume();
      showToast('Microfone alterado.', 'info', 2500);
    } catch (err) {
      showToast('Nao foi possivel trocar o microfone.', 'error');
      console.error(err);
      await refreshMicrophones();
    }
  }

  async function applyAudioProcessingSettings() {
    const microphoneTrack = localStream?.getAudioTracks()[0];
    if (!microphoneTrack) return;
    try {
      await microphoneTrack.applyConstraints(getMicrophoneConstraints(selectedMicrophoneId));
      showToast('Tratamento de audio atualizado.', 'info', 2500);
    } catch (err) {
      showToast('Nao foi possivel aplicar esse tratamento de audio.', 'error');
      console.error('Erro aplicando tratamento de audio:', err);
    }
  }

  async function stopMicrophoneVolume() {
    if (microphoneSender) {
      const sourceTrack = localStream?.getAudioTracks()[0];
      if (sourceTrack) await microphoneSender.replaceTrack(sourceTrack);
    }
    await microphoneGainContext?.close();
    microphoneGainContext = null;
    microphoneGainNode = null;
    microphoneGainDestination = null;
  }

  async function applyMicrophoneVolume() {
    if (!localStream || !microphoneSender) return;
    const sourceTrack = localStream.getAudioTracks()[0];
    if (!sourceTrack) return;
    if (!microphoneGainNode) {
      microphoneGainContext = new AudioContext();
      const source = microphoneGainContext.createMediaStreamSource(new MediaStream([sourceTrack]));
      microphoneGainNode = microphoneGainContext.createGain();
      microphoneGainDestination = microphoneGainContext.createMediaStreamDestination();
      source.connect(microphoneGainNode);
      microphoneGainNode.connect(microphoneGainDestination);
    }
    await microphoneSender.replaceTrack(microphoneGainDestination.stream.getAudioTracks()[0]);
    microphoneGainNode.gain.value = Number(settingsInputVolume.value) / 100;
    await microphoneGainContext.resume();
  }

  async function refreshAudioOutputs() {
    const devices = (await navigator.mediaDevices.enumerateDevices())
      .filter((device) => device.kind === 'audiooutput');
    settingsOutput.innerHTML = '';
    devices.forEach((device, index) => {
      const option = document.createElement('option');
      option.value = device.deviceId;
      option.textContent = device.label || `Saida de audio ${index + 1}`;
      settingsOutput.appendChild(option);
    });
  }

  async function changeAudioOutput() {
    if (!settingsOutput.value || typeof remoteAudio.setSinkId !== 'function') return;
    try {
      await remoteAudio.setSinkId(settingsOutput.value);
    } catch (err) {
      showToast('Nao foi possivel trocar a saida de audio.', 'error');
      console.error(err);
    }
  }

  async function openSettings() {
    await Promise.all([refreshMicrophones(), refreshAudioOutputs()]);
    settingsModal.hidden = false;
  }

  async function enableCamera() {
    if (!localStream || !videoTransceiver) return;
    if (cameraTrack) {
      cameraTrack.enabled = !cameraTrack.enabled;
      btnCamera.setAttribute('aria-pressed', String(cameraTrack.enabled));
      localTile.classList.toggle('has-video', Boolean(screenStream || cameraTrack.enabled));
      updateTileLayout();
      return;
    }

    try {
      const cameraStream = await navigator.mediaDevices.getUserMedia({
        video: { width: { ideal: 1280 }, height: { ideal: 720 } },
      });
      cameraTrack = cameraStream.getVideoTracks()[0];
      localStream.addTrack(cameraTrack);
      if (!screenStream) {
        await videoTransceiver.sender.replaceTrack(cameraTrack);
        localVideo.srcObject = localStream;
      }
      localTile.classList.add('has-video', 'video-square');
      updateTileLayout();
      btnCamera.setAttribute('aria-pressed', 'true');
    } catch (err) {
      showToast('Nao foi possivel ligar a camera.', 'error');
      console.error(err);
    }
  }

  async function startNativeAudioCapture(pid) {
    if (!pid || !window.electronAPI.startNativeAudio) return null;

    nativeAudioContext = new AudioContext({ sampleRate: 48000 });
    await nativeAudioContext.audioWorklet.addModule(URL.createObjectURL(new Blob([`
      class WasapiProcessor extends AudioWorkletProcessor {
        constructor() {
          super();
          this.chunks = [];
          this.offset = 0;
          this.port.onmessage = (event) => this.chunks.push(event.data);
        }
        process(_inputs, outputs) {
          const output = outputs[0];
          for (let frame = 0; frame < output[0].length; frame += 1) {
            const chunk = this.chunks[0];
            if (!chunk) {
              output[0][frame] = 0;
              output[1][frame] = 0;
              continue;
            }
            const sampleIndex = this.offset * 2;
            output[0][frame] = chunk[sampleIndex] || 0;
            output[1][frame] = chunk[sampleIndex + 1] || output[0][frame];
            this.offset += 1;
            if (this.offset * 2 >= chunk.length) {
              this.chunks.shift();
              this.offset = 0;
            }
          }
          return true;
        }
      }
      registerProcessor('wasapi-processor', WasapiProcessor);
    `], { type: 'application/javascript' })));

    nativeAudioNode = new AudioWorkletNode(nativeAudioContext, 'wasapi-processor', {
      numberOfInputs: 0,
      numberOfOutputs: 1,
      outputChannelCount: [2],
    });
    nativeAudioDestination = nativeAudioContext.createMediaStreamDestination();
    nativeAudioNode.connect(nativeAudioDestination);
    nativeAudioCleanup = window.electronAPI.onNativeAudioData((packet) => {
      const bytes = packet.data instanceof Uint8Array ? packet.data : new Uint8Array(packet.data);
      const samples = new Float32Array(bytes.buffer, bytes.byteOffset, Math.floor(bytes.byteLength / 4)).slice();
      nativeAudioNode?.port.postMessage(samples, [samples.buffer]);
    });
    await window.electronAPI.startNativeAudio(pid);
    await nativeAudioContext.resume();
    return nativeAudioDestination.stream.getAudioTracks()[0];
  }

  async function stopNativeAudioCapture() {
    nativeAudioCleanup?.();
    nativeAudioCleanup = null;
    await window.electronAPI.stopNativeAudio?.();
    nativeAudioNode?.disconnect();
    nativeAudioContext?.close();
    nativeAudioNode = null;
    nativeAudioDestination = null;
    nativeAudioContext = null;
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
    if (!pc || !isInitiator) return;
    if (renegotiationInProgress || pc.signalingState !== 'stable') {
      renegotiationQueued = true;
      return;
    }
    renegotiationInProgress = true;
    try {
      const offer = await pc.createOffer();
      await pc.setLocalDescription(offer);
      sendSignal({ type: 'offer', sdp: serializeDescription(pc.localDescription) });
    } catch (err) {
      console.error('Erro renegociando midia:', err);
      showToast(`Falha ao atualizar a midia: ${err.message || err}`, 'error', 9000);
    } finally {
      renegotiationInProgress = false;
      if (renegotiationQueued && pc?.signalingState === 'stable') {
        renegotiationQueued = false;
        await renegotiateConnection();
      }
    }
  }

  function wireSocketEvents() {
    window.electronAPI.socketOn('room-check-result', (result) => {
      roomCheckResolver?.(result);
      roomCheckResolver = null;
    });
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
      if (!hasJoinedOnce) {
        roomCheckResolver?.({ exists: false, available: false, error: true });
        window.electronAPI.socketDisconnect();
        currentRoom = null;
        setJoining(false);
      }
      showToast(`Erro de conexao: ${err?.message || 'verifique sua internet.'}`, 'error', 6000);
    });

    window.electronAPI.socketOn('join-error', (message) => {
      setLobbyStatus(message, 'error');
      window.electronAPI.socketDisconnect();
      currentRoom = null;
      setJoining(false);
    });

    window.electronAPI.socketOn('room-full', () => {
      setLobbyStatus('Essa sala ja tem 2 pessoas.', 'error');
      setJoining(false);
      window.electronAPI.socketDisconnect();
    });

    window.electronAPI.socketOn('joined-room', async ({ roomName, isFirstPeer }) => {
      if (hasJoinedOnce && pc) {
        pc.close();
        pc = null;
      }
      currentRoom = roomName;
      isInitiator = isFirstPeer;
      hasJoinedOnce = true;

      if (!pc) {
        // Primeira vez entrando nesta sessao do app: pega a midia local
        // e cria a PeerConnection. Em reconexoes, so re-anunciamos a sala
        // (o pc so e recriado se realmente caiu, ver onconnectionstatechange).
        goToCallScreen(roomName);
        try {
          if (!localStream) await startLocalMedia();
        } catch (err) {
          showToast('Nao foi possivel acessar camera/microfone. Verifique as permissoes.', 'error', 6000);
          console.error(err);
          leaveCall();
          return;
        }
        await createPeerConnection();
        await drainSignals();

        if (pendingPeerJoined && isInitiator) {
          pendingPeerJoined = false;
          await createOffer();
        }

        sendSignal({ type: 'ready' });

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
      if (!pc || !isInitiator || !remotePeerReady) return;
      if (pc.signalingState !== 'stable') return;
      try {
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
          await bindLocalChannels();
          await applyMicrophoneVolume();
          await flushPendingCandidates();
          const answer = await pc.createAnswer();
          await pc.setLocalDescription(answer);
          sendSignal({ type: 'answer', sdp: serializeDescription(pc.localDescription) });
        } else if (data.type === 'answer') {
          if (pc.signalingState !== 'have-local-offer') return;
          await pc.setRemoteDescription(new RTCSessionDescription(data.sdp));
          await flushPendingCandidates();
          if (renegotiationQueued) {
            renegotiationQueued = false;
            await renegotiateConnection();
          }
        } else if (data.type === 'ready') {
          remotePeerReady = true;
          if (isInitiator) await createOffer();
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

    window.electronAPI.socketOn('peer-left', async () => {
      showToast('A outra pessoa saiu da sala.', 'info');
      remoteVideo.srcObject = null;
      remoteVideo.dataset.empty = 'true';
      remoteTile.classList.remove('is-speaking', 'has-video', 'screen-share');
      speakingIndicators.get(remoteTile)?.();
      await remoteMixer?.close();
      remoteMixer = null;
      isInitiator = true;
      remotePeerReady = false;
      pendingSignals = [];
      updateTileLayout();
      setConnectionState('connecting');
      callStatus.textContent = 'esperando a outra pessoa voltar...';
      // Mantemos nossa PeerConnection e midia local de pe: quando ela
      // reentrar na mesma sala, o servidor manda "peer-joined" de novo
      // e o handshake recomeca (pc antigo e recriado por seguranca).
      if (pc) {
        pc.close();
        pc = null;
        await createPeerConnection();
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
  async function bindLocalChannels() {
    const channels = await window.CallMedia.bindMediaChannels(pc, {
      microphone: microphoneGainDestination?.stream.getAudioTracks()[0] || localStream.getAudioTracks()[0],
      video: screenStream?.getVideoTracks()[0] || cameraTrack,
      screenAudio: screenAudioTrack,
    });
    microphoneSender = channels.microphoneSender;
    videoTransceiver = channels.videoTransceiver;
    screenAudioTransceiver = channels.screenAudioTransceiver;
  }

  async function createPeerConnection() {
    clearTimeout(reconnectTimer);
    clearTimeout(iceFailureTimer);
    pendingCandidates = [];
    videoTransceiver = null;
    screenAudioTransceiver = null;
    microphoneSender = null;
    pc = new RTCPeerConnection({
      iceServers: CONFIG.ICE_SERVERS,
      iceCandidatePoolSize: 10,
    });

    // The answerer binds the offered channels after setRemoteDescription.
    // Empty channels created on both sides leave orphaned recvonly senders.
    if (isInitiator) await bindLocalChannels();
    if (isInitiator) await applyMicrophoneVolume();

    const connection = pc;

    pc.onicecandidate = (event) => {
      if (event.candidate) {
        sendSignal({ type: 'candidate', candidate: event.candidate.toJSON() });
      }
    };

    pc.ontrack = (event) => {
      if (event.track.kind === 'video') {
        remoteVideo.srcObject = new MediaStream([event.track]);
        remoteVideo.dataset.empty = 'false';
        remoteTile.classList.add('has-video', 'video-square');
        updateTileLayout();
        remoteVideo.play().catch(() => {});
      } else if (event.track.kind === 'audio') {
        const mixer = ensureRemoteMixer();
        mixer.addTrack(event.track);
        mixer.setVolume(remoteVolume.value).catch(console.warn);
        attachSpeakingIndicator(mixer.destination.stream, remoteTile);
      }
    };

    pc.onconnectionstatechange = () => {
      if (pc !== connection) return;
      console.log('[pc] connectionState:', pc.connectionState);

      if (pc.connectionState === 'connected') {
        clearTimeout(reconnectTimer);
        clearTimeout(iceFailureTimer);
        setConnectionState('connected');
        if (screenStream) sendSignal({ type: 'screen-share-start' });
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
      if (pc !== connection) return;
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
      sendSignal({ type: 'offer', sdp: serializeDescription(pc.localDescription) });
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

  btnCamera.addEventListener('click', enableCamera);
  openSettingsButton.addEventListener('click', openSettings);
  settingsCloseButton.addEventListener('click', () => { settingsModal.hidden = true; });
  settingsModal.addEventListener('click', (event) => {
    if (event.target === settingsModal) settingsModal.hidden = true;
  });
  settingsMicrophone.addEventListener('change', changeMicrophone);
  settingsOutput.addEventListener('change', changeAudioOutput);
  [settingsNoiseSuppression, settingsEchoCancellation, settingsAutoGain]
    .forEach((setting) => setting.addEventListener('change', applyAudioProcessingSettings));
  settingsInputVolume.addEventListener('input', () => {
    settingsInputValue.value = `${settingsInputVolume.value}%`;
    settingsInputValue.textContent = `${settingsInputVolume.value}%`;
    applyMicrophoneVolume().catch((err) => console.error('Erro ajustando volume do microfone:', err));
  });
  navigator.mediaDevices.addEventListener('devicechange', refreshMicrophones);

  remoteVideo.muted = true;
  remoteVolume.addEventListener('input', applyRemoteVolume);

  settingsOutputVolume.addEventListener('input', () => {
    remoteVolume.value = settingsOutputVolume.value;
    remoteVolume.dispatchEvent(new Event('input'));
  });

  function toggleVideoSize(tile, event) {
    if (event.target.closest('button, input, label, select, .context-menu, .volume-panel')) return;
    tile.classList.toggle('video-expanded');
    tile.classList.toggle('video-square');
  }

  remoteTile.addEventListener('click', (event) => toggleVideoSize(remoteTile, event));
  localTile.addEventListener('click', (event) => toggleVideoSize(localTile, event));

  btnFullscreen.addEventListener('click', async (event) => {
    event.stopPropagation();
    try {
      await remoteTile.requestFullscreen();
    } catch (err) {
      showToast('Nao foi possivel abrir tela cheia.', 'error');
    }
  });

  function closeRemoteContextMenu() {
    remoteContextMenu.hidden = true;
  }

  remoteTile.addEventListener('contextmenu', (event) => {
    event.preventDefault();
    remoteContextMenu.hidden = false;
    const bounds = remoteTile.getBoundingClientRect();
    remoteContextMenu.style.left = `${Math.max(8, Math.min(event.clientX - bounds.left, bounds.width - remoteContextMenu.offsetWidth - 8))}px`;
    remoteContextMenu.style.top = `${Math.max(8, Math.min(event.clientY - bounds.top, bounds.height - remoteContextMenu.offsetHeight - 8))}px`;
  });
  remoteContextMenu.addEventListener('click', (event) => event.stopPropagation());

  btnRemoteMute.addEventListener('click', (event) => {
    event.stopPropagation();
    remoteAudio.muted = !remoteAudio.muted;
    btnRemoteMute.textContent = remoteAudio.muted ? 'Desmutar live' : 'Mutar live';
    closeRemoteContextMenu();
  });

  document.addEventListener('click', closeRemoteContextMenu);

  btnShare.addEventListener('click', async () => {
    await openSourcePicker();
  });

  stopScreenShareButton.addEventListener('click', async () => {
    closeSourcePicker();
    await stopScreenShare();
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
    includeSystemAudio.checked = Boolean(screenAudioTrack);
    stopScreenShareButton.hidden = !screenStream;
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
        startScreenShare(source.id, includeSystemAudio.checked, source.nativeAudioPid);
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
    if (e.key === 'Escape') {
      closeSourcePicker();
      settingsModal.hidden = true;
      closeRemoteContextMenu();
    }
  });

  async function startScreenShare(sourceId, includeAudio = false, nativeAudioPid = null) {
    const previousScreenStream = screenStream;
    const useNativeAudio = includeAudio && nativeAudioPid && window.electronAPI.startNativeAudio;
    let nextScreenStream;
    try {
      nextScreenStream = await navigator.mediaDevices.getUserMedia({
        audio: useNativeAudio ? false : includeAudio ? {
          mandatory: {
            chromeMediaSource: 'desktop',
            chromeMediaSourceId: sourceId,
          },
        } : false,
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
      screenStream = nextScreenStream;
    } catch (err) {
      showToast('Nao foi possivel iniciar o compartilhamento de tela.', 'error');
      console.error(err);
      return;
    }

    const screenTrack = screenStream.getVideoTracks()[0];
    if (useNativeAudio) {
      try {
        screenAudioTrack = await startNativeAudioCapture(nativeAudioPid);
      } catch (nativeError) {
        console.warn('WASAPI falhou; usando audio de sistema como fallback:', nativeError);
        await stopNativeAudioCapture();
        try {
          const fallbackAudio = await navigator.mediaDevices.getUserMedia({
            audio: { mandatory: { chromeMediaSource: 'desktop', chromeMediaSourceId: sourceId } },
            video: false,
          });
          screenAudioTrack = fallbackAudio.getAudioTracks()[0] || null;
          if (screenAudioTrack) screenStream.addTrack(screenAudioTrack);
        } catch (fallbackError) {
          console.warn('Fallback de audio de sistema tambem falhou:', fallbackError);
          screenAudioTrack = null;
        }
      }
    } else {
      screenAudioTrack = screenStream.getAudioTracks()[0] || null;
    }

    // O coracao da troca: substitui a track de video no sender existente,
    // sem precisar renegociar a offer/answer.
    const sender = pc && (videoTransceiver?.sender || pc.getSenders().find((s) => s.track?.kind === 'video'));
    if (sender) await sender.replaceTrack(screenTrack);
    if (screenAudioTrack && screenAudioTransceiver) {
      await screenAudioTransceiver.sender.replaceTrack(screenAudioTrack);
    } else if (screenAudioTransceiver) {
      await screenAudioTransceiver.sender.replaceTrack(null);
    }

    if (previousScreenStream && previousScreenStream !== screenStream) {
      previousScreenStream.getTracks().forEach((track) => track.stop());
    }

    // Preview local passa a mostrar a tela compartilhada.
    localVideo.srcObject = screenStream;
    localTile.classList.add('screen-share');
    localTile.classList.add('has-video', 'video-square');
    updateTileLayout();
    localTileLabel.textContent = 'sua tela';
    sendSignal({ type: 'screen-share-start' });

    btnShare.setAttribute('aria-pressed', 'true');
    stopScreenShareButton.hidden = false;

    // Se o usuario parar o compartilhamento pelo controle nativo do SO
    // (ex: barra "Parar de compartilhar" do Windows/macOS), voltamos pra
    // camera automaticamente.
    screenTrack.addEventListener('ended', () => {
      if (screenStream === nextScreenStream) stopScreenShare();
    });
  }

  async function stopScreenShare() {
    if (!screenStream) return;

    screenStream.getTracks().forEach((t) => t.stop());
    screenStream = null;
    stopScreenShareButton.hidden = true;
    if (screenAudioTransceiver) await screenAudioTransceiver.sender.replaceTrack(null);
    await stopNativeAudioCapture();
    screenAudioTrack = null;

    const sender = pc && (videoTransceiver?.sender || pc.getSenders().find((s) => s.track?.kind === 'video'));
    if (sender) await sender.replaceTrack(cameraTrack || null);

    localVideo.srcObject = localStream;
    localTile.classList.remove('screen-share');
    localTile.classList.toggle('has-video', Boolean(cameraTrack?.enabled));
    updateTileLayout();
    localTileLabel.textContent = 'Você';
    btnShare.setAttribute('aria-pressed', 'false');
    sendSignal({ type: 'screen-share-stop' });
  }

  function leaveCall() {
    setJoining(false);
    roomCheckResolver?.({ error: true });
    roomCheckResolver = null;
    window.electronAPI.socketEmit('leave-room');
    window.electronAPI.socketDisconnect();

    if (screenStream) screenStream.getTracks().forEach((t) => t.stop());
    if (localStream) localStream.getTracks().forEach((t) => t.stop());
    remoteMixer?.close().catch(() => {});
    remoteMixer = null;
    speakingIndicators.forEach((cleanup) => cleanup());
    speakingIndicators.clear();
    stopNativeAudioCapture().catch(() => {});
    stopMicrophoneVolume().catch(() => {});
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
    screenAudioTransceiver = null;
    screenAudioTrack = null;
    clearTimeout(reconnectTimer);
    clearTimeout(iceFailureTimer);
    renegotiationQueued = false;
    renegotiationInProgress = false;
    microphoneSender = null;
    remoteVideo.srcObject = null;
    remoteVideo.dataset.empty = 'true';
    localVideo.srcObject = null;
    btnMute.setAttribute('aria-pressed', 'false');
    btnCamera.setAttribute('aria-pressed', 'false');
    btnShare.setAttribute('aria-pressed', 'false');
    localTile.classList.remove('screen-share', 'has-video', 'video-expanded');
    localTile.classList.add('video-square');
    remoteTile.classList.remove('video-expanded');
    remoteTile.classList.add('video-square');
    remoteAudio.muted = false;
    btnRemoteMute.textContent = 'Silenciar áudio';
    closeRemoteContextMenu();
    settingsModal.hidden = true;
    closeSourcePicker();
    localTileLabel.textContent = 'Você';
    remoteTile.classList.remove('screen-share');
    remoteTile.classList.remove('has-video');
    updateTileLayout();

    goToLobbyScreen();
  }

  // ---------------------------------------------------------------------
  // Lobby: entrar na sala
  // ---------------------------------------------------------------------
  async function beginJoin(roomName, verifyRoom = false) {
    if (!roomName || joining) return;
    setJoining(true);
    setLobbyStatus('Conectando ao servidor...', 'info');

    // IMPORTANTE: socketConnect() precisa vir antes de wireSocketEvents(),
    // porque o wrapper exposto pelo preload so consegue registrar listeners
    // (socketOn) depois que o objeto `socket` existe do lado do preload.
    currentRoom = roomName;
    window.electronAPI.socketConnect(CONFIG.SIGNALING_SERVER_URL);
    wireSocketEvents();
    if (verifyRoom) {
      const roomResult = await new Promise((resolve) => {
        const timer = setTimeout(() => {
          roomCheckResolver = null;
          resolve({ error: true });
        }, 15000);
        roomCheckResolver = (result) => {
          clearTimeout(timer);
          resolve(result);
        };
        window.electronAPI.socketEmit('check-room', roomName);
      });
      if (roomResult.error || !roomResult.exists) {
        window.electronAPI.socketDisconnect();
        currentRoom = null;
        setJoining(false);
        setLobbyStatus(roomResult.error ? 'O servidor não respondeu. Tente novamente.' : 'Essa sala não existe.', 'error');
        return;
      }
      if (!roomResult.available) {
        window.electronAPI.socketDisconnect();
        currentRoom = null;
        setJoining(false);
        setLobbyStatus('Essa sala ja esta cheia.', 'error');
        return;
      }
    }
    // O Socket.IO enfileira este evento se a conexao ainda estiver abrindo.
    window.electronAPI.socketEmit('join-room', roomName);
  }

  joinForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    await beginJoin(roomInput.value.trim(), true);
  });

  createRoomButton.addEventListener('click', async () => {
    const roomCode = generateRoomCode();
    createdRoomCode.textContent = roomCode;
    createdRoom.hidden = false;
    await beginJoin(roomCode);
  });

  showJoinButton.addEventListener('click', () => {
    joinForm.hidden = false;
    showJoinButton.hidden = true;
    createRoomButton.hidden = true;
    roomInput.focus();
  });

  backToLobbyButton.addEventListener('click', resetLobbyMode);

  copyRoomButton.addEventListener('click', copyRoomCode);
  copyActiveRoomButton.addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(activeRoomCode.textContent);
      showToast('Codigo copiado.', 'info', 2500);
    } catch (err) {
      showToast('Nao foi possivel copiar o codigo.', 'error');
    }
  });

  function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  }

  // Permissoes de camera/microfone no macOS (no-op no Windows).
  window.electronAPI.checkMediaPermissions().catch(() => {});
})();
