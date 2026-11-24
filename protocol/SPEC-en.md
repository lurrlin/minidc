# Official Unme Protocol Specification (`v1.0` / `v1.1`)

This document is the **single source of truth** for communication between **Unme Desktop (Electron/JS)**, **Unme Android (Java)**, and the **Signaling Server (Node.js/Socket.IO)**[cite: 11].

Official numeric constants and regular expressions reside in [`constants.json`](./constants.json) and structural payload definitions in [`schemas/`](./schemas/)[cite: 11].

---

## 1. Data and Privacy Boundaries

The protocol strictly separates four data layers[cite: 11]:

| Layer | Transport | Passes Through Server? | Persistence |
| :--- | :--- | :--- | :--- |
| **1. Signaling messages** | WebSocket / Socket.IO over TLS (`wss://`) | Yes (in-memory relay only) | None (logging `roomName` or SDP is strictly prohibited)[cite: 11] |
| **2. WebRTC data** | `RTCDataChannel` (SCTP over DTLS) | No (direct P2P) | Local user device profile only (`unme.profile.v1`)[cite: 11] |
| **3. Session metadata** | Volatile server and client memory | Room occupancy only (`1..2`) | None (destroyed immediately when room empties)[cite: 11] |
| **4. Media content** | RTP/RTCP over SRTP (DTLS keys) | No (direct P2P) | None[cite: 11] |

---

## 2. Session and Participant Identification

- **Hard capacity:** maximum of **2 participants** per room (`room.maxParticipants = 2`)[cite: 11].
- **Room name / code (`roomName`):**
  - Normalized with `trim()`[cite: 11].
  - Valid only when `1 <= length <= 40`[cite: 11].
  - When auto-generated via **Create room**, uses exactly **16 characters** sampled with a uniform cryptographic generator from the 67-symbol alphabet defined in `constants.json`[cite: 11].
  - On the server, Socket.IO room grouping uses the internal prefix `call:${roomName}` to prevent collisions with internal socket IDs[cite: 11].
- **Participant identity:** ephemeral, with no user accounts or authentication[cite: 11]. The server associates each connection solely with its temporary `socket.id`[cite: 11].
- **WebRTC negotiation roles:**
  - **Initiator (`isFirstPeer = true`):** first participant to join the room (or the remaining participant promoted after the other peer leaves via `peer-left`)[cite: 11]. Responsible for creating initial transceivers, creating the `"profile"` DataChannel, and emitting `offer` (including during `iceRestart`)[cite: 11].
  - **Guest (`isFirstPeer = false`):** second participant to join[cite: 11]. Does not create an offer; waits for `offer`, reuses incoming transceivers, attaches local media tracks, and responds with `answer`[cite: 11].

---

## 3. Protocol Versioning

- **Version `1.0` (baseline):**
  - `join-room` sends a plain `roomName` string[cite: 11].
  - `joined-room` returns `{ roomName, isFirstPeer }`[cite: 11].
  - Supported signals: `ready`, `offer`, `answer`, `candidate`, `video-state`, `screen-share-start`, `screen-share-stop`, `renegotiate`[cite: 11].
- **Version `1.1` (backwards-compatible):**
  - `join-room` accepts either a plain string (`1.0`) **or** an object `{ roomName, protocolVersion: "1.1", platform }` (`1.1`)[cite: 11].
  - `joined-room` may include `protocolVersion: "1.1"` as an optional field[cite: 11].
  - `ready` may include optional `protocolVersion` and `capabilities` fields[cite: 11].
  - `renegotiate` accepts `{ type: "renegotiate", iceRestart?: boolean }`[cite: 11].
  - **Backwards-compatibility rule:** any unknown optional field received by a `1.0` client must be ignored without failing signal processing[cite: 11].

---

## 4. Signaling Events (Socket.IO)

### 4.1. Client -> Server

1. **`check-room`** - Payload: `roomName: string`[cite: 11].
   - Used before joining a user-entered room code (not used when creating a room)[cite: 11].
   - The client must wait for socket connection before emitting and enforce a **30s** timeout on `v1.1` (15s on `v1.0`), displaying a cold-start notice if the server takes longer than 6s to respond[cite: 11].
2. **`join-room`** - Payload: `roomName: string` (or `{ roomName, protocolVersion, platform }` on `v1.1`)[cite: 11].
   - Server rules: rejects if invalid (`join-error`), rejects if socket is already in another room (`join-error`), rejects if `size >= 2` (`room-full`)[cite: 11].
3. **`signal`** - Payload: `{ roomName: string, data: SignalPayload }`[cite: 11].
   - Forwarded only if the socket is currently in `roomName`, `data.type` is in the allowlist, and the serialized JSON is at most `131072` bytes (128 KB)[cite: 11].
4. **`leave-room`** - No payload[cite: 11].
   - Removes the socket from the room, emits `peer-left` to the remaining peer, and deletes the room from memory if empty[cite: 11].

### 4.2. Server -> Client

1. **`room-check-result`**: `{ roomName: string, exists: boolean, available: boolean }`[cite: 11].
2. **`joined-room`**: `{ roomName: string, isFirstPeer: boolean, protocolVersion?: string }`[cite: 11].
3. **`join-error`**: `string` with user-facing reason (e.g., `"Nome de sala invalido."`, `"Voce ja esta em uma sala."`)[cite: 11].
4. **`room-full`**: `roomName: string`[cite: 11].
5. **`peer-joined`**: no payload; sent only to the participant already waiting in the room[cite: 11].
6. **`peer-left`**: no payload; sent to the remaining participant when the other peer leaves or disconnects[cite: 11].
7. **`signal`**: `SignalPayload` (relayed verbatim to the other participant in the same room)[cite: 11].

---

## 5. State Machine and Complete Handshake

### 5.1. Session States

```text
IDLE (Lobby)
  ├─[Join room]───▶ CHECKING_ROOM ──[exists && available]──▶ JOINING
  └─[Create room]───────────────────────────────────────────▶ JOINING
                                                                 │
                                                         [joined-room]
                                                                 ▼
  IDLE ◀──[leave-room]──── CONNECTED ◀──[ICE connected]──── WAITING_PEER / NEGOTIATING
                              │  ▲                                   ▲
             [disconnected]   │  │ [ICE recovered]                   │
                              ▼  │                                   │
                        RECONNECTING ────[peer-left]─────────────────┘
                                          (promoted to Initiator)
```

### 5.2. Anti-Glare Flow (Offer / Answer / ICE)

1. Both clients initialize WebRTC only after receiving `joined-room` and acquiring the local microphone[cite: 11].
2. **Initiator (`isFirstPeer = true`)** instantiates `RTCPeerConnection` with the **3 `sendrecv` transceivers** and creates the `"profile"` `DataChannel`[cite: 11]. If `peer-joined` has already fired, it waits for the Guest's `ready` signal[cite: 11].
3. **Guest (`isFirstPeer = false`)** instantiates `RTCPeerConnection` **without** adding its own transceivers (preventing orphan m-lines) and sends `signal { type: "ready" }`[cite: 11].
4. Once the Initiator has received both `peer-joined` and `ready` (and `signalingState === "stable"`), it creates and sends `offer`[cite: 11].
5. The Guest receives `offer`, calls `setRemoteDescription`, binds its local tracks to the 3 received transceivers (reusing them as `sendrecv`), flushes queued `candidate` messages, and sends `answer`[cite: 11].
6. ICE candidates (`candidate`) arriving before `remoteDescription` is set must be queued in order and applied immediately after `setRemoteDescription`[cite: 11].
7. Once `connectionState === "connected"`, each side sends `video-state` (and `screen-share-start` if currently sharing screen) to synchronize visual state, including after reconnections[cite: 11].

### 5.3. Failure Handling and Reconnection

- **Socket.IO reconnection (`connect` after `hasJoinedOnce`):** the server does not persist sessions across TCP/WebSocket drops[cite: 11]. The client must clear pending signals, re-emit `join-room`, and renegotiate a fresh `PeerConnection` while keeping local media active[cite: 11].
- **P2P network drop (`connectionState === "disconnected"`):** wait **3000 ms** before taking action (prevents unnecessary restarts on brief network hiccups)[cite: 11]. If it persists or transitions to `failed`:
  - **Initiator:** creates an offer with `iceRestart: true` and sends `offer`[cite: 11].
  - **Guest:** sends `signal { type: "renegotiate", iceRestart: true }` to request that the Initiator trigger an `iceRestart`[cite: 11].
- **Network blockage (`iceConnectionState === "checking"`):** if stuck in `checking` for **12000 ms**, display a warning to the user without terminating the call[cite: 11].

---

## 6. WebRTC Media Contract (3 Transceivers)

Every connection negotiates **3 `sendrecv` m-lines** under `UNIFIED_PLAN` in this exact order[cite: 11]:

| Index | Kind | Logical Role | Dynamic Track Switching |
| :---: | :--- | :--- | :--- |
| `0` | `video` (`video[0]`) | **Camera or Screen** (`video`) | `replaceTrack`/`setTrack` (`cameraTrack` <-> `screenTrack` <-> `null`) without SDP renegotiation[cite: 11] |
| `1` | `audio` (`audio[0]`) | **Microphone voice** (`voice`) | `replaceTrack`/`setTrack` when switching microphones or audio constraints[cite: 11] |
| `2` | `audio` (`audio[1]`) | **Screen audio** (`screen`) | `replaceTrack`/`setTrack` (used natively on Desktop)[cite: 11] |

- **Mandatory receiver rule:** distinguish `voice` from `screen` by the audio transceiver's position in the connection's ordered transceiver list (or by the recorded `mid` of the first audio transceiver), **never** by wrapper object reference equality[cite: 11].
- **Video sender parameters:** when sharing a screen, set degradation preference to `maintain-resolution` and content hint to `"detail"` where supported by the platform[cite: 11].

---

## 7. WebRTC Data Channels (`RTCDataChannel`)

### 7.1. `"profile"` Channel (P2P Profile)

- Created by the Initiator (`label: "profile"`, reliable and ordered)[cite: 11].
- Format: UTF-8 JSON validated against [`schemas/datachannel-profile.schema.json`](./schemas/datachannel-profile.schema.json)[cite: 11].
- Rules:
  - `name`: `trim()`, maximum `40` characters[cite: 11]. If empty, fall back locally to default labels (`"Você"` / `"Sua companhia"` in PT-BR UI)[cite: 11].
  - `photo`: empty string or `data:image/(jpeg|png|webp);base64,...` up to `60000` characters (input file up to `8 MB`, center-cropped to a `256x256` square over `#1a1d2a` background, JPEG-compressed trying qualities `0.8`, `0.6`, `0.4`)[cite: 11].
  - Sent when the channel opens and whenever updated during a call[cite: 11].

### 7.2. `"screen-audio"` Channel (IMA-ADPCM Screen Audio - v1.1)

- Secondary unreliable channel (`label: "screen-audio"`, `ordered: false`, `maxRetransmits: 0`) used to enable independent **Stream audio** volume control when Android is the sender (where Java WebRTC does not expose feeding raw PCM into a second `AudioSource` without NDK)[cite: 11].
- Little-endian binary packet with a **12-byte** header defined in [`schemas/datachannel-screen-audio.schema.json`](./schemas/datachannel-screen-audio.schema.json), encoded as **4-bit mono IMA-ADPCM at 24 kHz** (20 ms frames = 480 samples)[cite: 11].
- On Desktop receivers, decoded frames feed the `'screen'` channel of `RemoteAudioMixer`; on Android receivers, they feed a dedicated `AudioTrack` controlled by the `CANAL_TELA` gain in `MisturadorAudio`[cite: 11].

---

## 8. Audio Mixing Rules and Speaking Indicator

- All volume controls accept integers from **0% to 200%** (`100%` default), clamped to `[0, 200]`[cite: 11].
- Independent controls:
  - `volumeEntrada` (`0..200%`): linear gain applied to outgoing microphone audio (`volume / 100`)[cite: 11].
  - `volumeVoz` (`0..200%`): gain for the remote voice channel (`audio[0]`)[cite: 11].
  - `volumeTela` (`0..200%`): gain for the remote screen stream channel (`audio[1]` or `"screen-audio"` DataChannel)[cite: 11].
  - `volumeGeral` (`0..200%`): master multiplier applied over both `volumeVoz` and `volumeTela`[cite: 11].
- **Deafen (`Mute Output`):**
  - Immediately sets effective incoming audio output gain to zero (`ganhoEfetivo = 0`) without muting the local microphone and **without overwriting** saved values of `volumeVoz`, `volumeTela`, and `volumeGeral`[cite: 11].
  - Adjusting sliders while deafened updates internal state without leaking audio until output is unmuted[cite: 11].
- **Speaking indicator:** threshold `SPEAKING_THRESHOLD = 14` (on a `0..255` scale), evaluated independently for the local microphone and solely the remote `voice` channel, remaining active even while `deafen` is enabled[cite: 11].