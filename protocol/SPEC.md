# Especificação Oficial do Protocolo Unme (`v1.0` / `v1.1`)

Este documento é a **fonte única de verdade** para comunicação entre **Unme Desktop (Electron/JS)**, **Unme Android (Java)** e o **Signaling Server (Node.js/Socket.IO)**.

Constantes numéricas e regex oficiais residem em [`constants.json`](./constants.json) e os formatos estruturais em [`schemas/`](./schemas/).

---

## 1. Fronteiras de Dados e Privacidade

O protocolo separa estritamente quatro camadas:

| Camada | Meio de Transporte | Passa pelo Servidor? | Persistência |
| :--- | :--- | :--- | :--- |
| **1. Mensagens de sinalização** | WebSocket / Socket.IO sobre TLS (`wss://`) | Sim (apenas repasse em memória) | Nenhuma (proibido gravar `roomName` ou SDP em logs) |
| **2. Dados via WebRTC** | `RTCDataChannel` (SCTP sobre DTLS) | Não (P2P direto) | Apenas perfil local no dispositivo do usuário (`unme.profile.v1`) |
| **3. Metadados de sessão** | Memória volátil do servidor e clientes | Apenas ocupação da sala (`1..2`) | Nenhuma (destruído ao esvaziar a sala) |
| **4. Conteúdo de mídia** | RTP/RTCP sobre SRTP (chaves DTLS) | Não (P2P direto) | Nenhuma |

---

## 2. Identificação de Sessão e Participantes

- **Capacidade absoluta:** máximo de **2 participantes** por sala (`room.maxParticipants = 2`).
- **Nome / Código da sala (`roomName`):**
  - Normalizado com `trim()`.
  - Válido apenas quando `1 <= length <= 40`.
  - Quando gerado automaticamente pelo botão **Criar sala**, usa exatamente **16 caracteres** sorteados com gerador criptográfico uniforme a partir do alfabeto de 67 símbolos definido em `constants.json`.
  - No servidor, o agrupamento Socket.IO usa o prefixo interno `call:${roomName}` para impedir colisão com IDs internos de socket.
- **Identidade do participante:** efêmera, sem contas ou autenticação de usuário. O servidor associa cada conexão apenas ao `socket.id` temporário.
- **Papel na negociação WebRTC:**
  - **Iniciador (`isFirstPeer = true`):** primeiro participante a entrar na sala (ou o participante que permaneceu na sala quando o outro saiu via `peer-left`). Responsável por criar os transceivers iniciais, o DataChannel `"profile"` e emitir `offer` (inclusive em `iceRestart`).
  - **Convidado (`isFirstPeer = false`):** segundo participante a entrar. Não cria oferta; aguarda `offer`, reutiliza os transceivers recebidos, vincula sua mídia e responde com `answer`.

---

## 3. Versionamento do Protocolo

- **Versão `1.0` (atual em produção):**
  - `join-room` envia string pura `roomName`.
  - `joined-room` retorna `{ roomName, isFirstPeer }`.
  - Sinais suportados: `ready`, `offer`, `answer`, `candidate`, `video-state`, `screen-share-start`, `screen-share-stop`, `renegotiate`.
- **Versão `1.1` (retrocompatível):**
  - `join-room` aceita string pura (`1.0`) **ou** objeto `{ roomName, protocolVersion: "1.1", platform }` (`1.1`).
  - `joined-room` pode incluir `protocolVersion: "1.1"` como campo opcional.
  - `ready` pode incluir `protocolVersion` e `capabilities` opcionais.
  - `renegotiate` aceita `{ type: "renegotiate", iceRestart?: boolean }`.
  - **Regra de retrocompatibilidade:** qualquer campo opcional desconhecido por clientes `1.0` deve ser ignorado sem falhar o processamento do sinal.

---

## 4. Eventos de Sinalização (Socket.IO)

### 4.1. Cliente → Servidor

1. **`check-room`** — Payload: `roomName: string`.
   - Usado antes de entrar em uma sala digitada pelo usuário (não usado ao criar sala).
   - O cliente deve aguardar a conexão do socket antes de emitir e aplicar timeout de **30s** na `v1.1` (15s na `v1.0`), exibindo aviso amigável caso o servidor esteja iniciando após 6s.
2. **`join-room`** — Payload: `roomName: string` (ou objeto `{ roomName, protocolVersion, platform }` na `v1.1`).
   - Regras no servidor: rejeita se inválido (`join-error`), rejeita se socket já estiver em outra sala (`join-error`), rejeita se `size >= 2` (`room-full`).
3. **`signal`** — Payload: `{ roomName: string, data: SignalPayload }`.
   - Encaminhado somente se o socket estiver atualmente na sala `roomName`, `data.type` pertencer à whitelist e o JSON serializado tiver até `131072` bytes (128 KB).
4. **`leave-room`** — Sem payload.
   - Remove o socket da sala, envia `peer-left` para o par restante e apaga a sala da memória se vazia.

### 4.2. Servidor → Cliente

1. **`room-check-result`**: `{ roomName: string, exists: boolean, available: boolean }`.
2. **`joined-room`**: `{ roomName: string, isFirstPeer: boolean, protocolVersion?: string }`.
3. **`join-error`**: `string` com motivo amigável (`"Nome de sala invalido."`, `"Voce ja esta em uma sala."`).
4. **`room-full`**: `roomName: string`.
5. **`peer-joined`**: sem payload, enviado apenas ao participante que já estava na sala.
6. **`peer-left`**: sem payload, enviado ao participante restante quando o outro sai ou desconecta.
7. **`signal`**: `SignalPayload` (repassado exatamente ao outro participante da mesma sala).

---

## 5. Máquina de Estados e Handshake Completo

### 5.1. Estados da Sessão

```text
IDLE (Lobby)
  ├─[Entrar em sala]──▶ CHECKING_ROOM ──[exists && available]──▶ JOINING
  └─[Criar sala]────────────────────────────────────────────────▶ JOINING
                                                                     │
                                                             [joined-room]
                                                                     ▼
  IDLE ◀──[leave-room]──── CONNECTED ◀──[ICE connected]──── WAITING_PEER / NEGOTIATING
                              │  ▲                                   ▲
             [disconnected]   │  │ [ICE recovered]                   │
                              ▼  │                                   │
                        RECONNECTING ────[peer-left]─────────────────┘
                                          (promovido a Iniciador)
```

### 5.2. Fluxo Anti-Glare (Oferta / Resposta / ICE)

1. Ambos os clientes só iniciam WebRTC após `joined-room` + abertura do microfone local.
2. **Iniciador (`isFirstPeer = true`)** instancia `RTCPeerConnection` com os **3 transceivers `sendrecv`** e cria o `DataChannel` `"profile"`. Se já houver `peer-joined`, aguarda `ready` do convidado.
3. **Convidado (`isFirstPeer = false`)** instancia `RTCPeerConnection` **sem** adicionar transceivers próprios (evita m-lines órfãs) e envia `signal { type: "ready" }`.
4. Quando o Iniciador recebeu `peer-joined` e `ready` (e `signalingState === "stable"`), cria e envia `offer`.
5. O Convidado recebe `offer`, executa `setRemoteDescription`, vincula suas faixas locais nos 3 transceivers recebidos (reutilizando-os como `sendrecv`), descarrega `candidate`s enfileirados e envia `answer`.
6. Candidatos ICE (`candidate`) recebidos antes de `remoteDescription` devem ser enfileirados em ordem e aplicados imediatamente após `setRemoteDescription`.
7. Após `connectionState === "connected"`, cada lado envia `video-state` (e `screen-share-start` se já estiver transmitindo tela) para sincronizar o estado visual mesmo em reentradas.

### 5.3. Tratamento de Falhas e Reconexão

- **Reconexão de Socket.IO (`connect` após `hasJoinedOnce`):** o servidor não preserva sessões após queda de TCP/WebSocket. O cliente deve limpar sinais pendentes, re-emitir `join-room` e renegociar uma nova `PeerConnection` mantendo a mídia local ativa.
- **Queda de rede P2P (`connectionState === "disconnected"`):** aguardar **3000 ms** antes de agir (evita reinícios em oscilações rápidas). Se persistir ou atingir `failed`:
  - **Iniciador:** cria oferta com `iceRestart: true` e envia `offer`.
  - **Convidado:** envia `signal { type: "renegotiate", iceRestart: true }` para solicitar que o Iniciador dispare o `iceRestart`.
- **Bloqueio de rede (`iceConnectionState === "checking"`):** se permanecer em `checking` por **12000 ms**, exibir aviso ao usuário sem encerrar a chamada.

---

## 6. Contrato de Mídia WebRTC (3 Transceivers)

A conexão sempre negocia **3 m-lines `sendrecv`** em `UNIFIED_PLAN` nesta ordem:

| Índice | Tipo | Papel Lógico | Troca dinâmica |
| :---: | :--- | :--- | :--- |
| `0` | `audio` (`audio[0]`) | **Voz do microfone** (`voice`) | `replaceTrack`/`setTrack` ao trocar mic ou aplicar processamento |
| `1` | `video` (`video[0]`) | **Câmera ou Tela** (`video`) | `replaceTrack`/`setTrack` (`cameraTrack` ↔ `screenTrack` ↔ `null`) sem renegociar SDP |
| `2` | `audio` (`audio[1]`) | **Áudio da tela** (`screen`) | `replaceTrack`/`setTrack` (usado nativamente no Desktop) |

- **Regra obrigatória no receptor:** distinguir `voice` de `screen` pela posição do transceiver de áudio na lista ordenada da conexão (ou pelo `mid` registrado do primeiro transceiver de áudio), **nunca** por comparação de referência de objeto wrapper.
- **Parâmetros de envio de vídeo:** ao transmitir tela, configurar preferência de degradação `maintain-resolution` e hint de conteúdo `"detail"` quando suportado pela plataforma.

---

## 7. Canais de Dados WebRTC (`RTCDataChannel`)

### 7.1. Canal `"profile"` (Perfil P2P)

- Criado pelo Iniciador (`label: "profile"`, confiável e ordenado).
- Formato: JSON UTF-8 validado por [`schemas/datachannel-profile.schema.json`](./schemas/datachannel-profile.schema.json).
- Regras:
  - `name`: `trim()`, máximo `40` caracteres. Se vazio, usar fallback visual local (`"Você"` / `"Sua companhia"`).
  - `photo`: string vazia ou `data:image/(jpeg|png|webp);base64,...` com comprimento máximo de `60000` caracteres (arquivo original de entrada até `8 MB`, recorte central quadrado `256x256` sobre fundo `#1a1d2a`, compressão JPEG tentando qualidades `0.8`, `0.6`, `0.4`).
  - Enviado ao abrir o canal e sempre que alterado durante a chamada.

### 7.2. Canal `"screen-audio"` (Áudio da Tela em IMA-ADPCM — v1.1)

- Canal secundário não-confiável (`label: "screen-audio"`, `ordered: false`, `maxRetransmits: 0`) usado para permitir controle independente do volume `Áudio da transmissão` quando o emissor é o Android (onde o WebRTC Java não expõe inserção de PCM em uma 2ª `AudioSource` sem NDK).
- Pacote binário little-endian com cabeçalho de **12 bytes** especificado em [`schemas/datachannel-screen-audio.schema.json`](./schemas/datachannel-screen-audio.schema.json), codificado em **IMA-ADPCM 4-bit mono a 24 kHz** (quadros de 20 ms = 480 amostras).
- No receptor Desktop, os quadros decodificados alimentam o canal `'screen'` do `RemoteAudioMixer`; no Android, alimentam um `AudioTrack` dedicado controlado pelo ganho `CANAL_TELA` do `MisturadorAudio`.

---

## 8. Regras de Mistura de Áudio e Indicador de Fala

- Todos os controles de volume aceitam inteiros de **0% a 200%** (`100%` padrão), limitados ao intervalo `[0, 200]`.
- Controles independentes:
  - `volumeEntrada` (`0..200%`): ganho linear aplicado ao microfone enviado (`volume / 100`).
  - `volumeVoz` (`0..200%`): ganho do canal de voz remota (`audio[0]`).
  - `volumeTela` (`0..200%`): ganho do canal de transmissão remota (`audio[1]` ou DataChannel `"screen-audio"`).
  - `volumeGeral` (`0..200%`): multiplicador master aplicado sobre `volumeVoz` e `volumeTela`.
- **Deafen (`Ouvir` / `Silenciado`):**
  - Zera imediatamente a saída de áudio recebida (`ganhoEfetivo = 0`) sem mutar o microfone local e **sem alterar** os valores salvos de `volumeVoz`, `volumeTela` e `volumeGeral`.
  - Alterar sliders enquanto em deafen atualiza os valores internos sem vazar som até o usuário reativar a escuta.
- **Indicador de fala:** limiar `SPEAKING_THRESHOLD = 14` (escala `0..255`), aplicado separadamente ao microfone local e apenas ao canal `voice` remoto, devendo continuar funcionando mesmo com `deafen` ativo.
