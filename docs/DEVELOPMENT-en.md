# Unme Development Guide

This guide is intended for developers working on the codebase[cite: 10]. To install and use the application, see the [README](../README.md)[cite: 10].

## Running the Project

Use Node.js 22.12 or newer and npm[cite: 10]. Desktop runs as two separate processes alongside the Android Studio project in `android/`[cite: 10]:

```powershell
# Terminal 1 - local signaling server
cd signaling-server
npm ci
npm start

# Terminal 2 - Desktop app
cd electron-app
npm ci
npm start
```

`electron-app/config.js` and `android/app/src/main/java/com/example/unme/ConfiguracaoApp.java` hold the signaling server URL[cite: 10]. To use a local server, set `SIGNALING_SERVER_URL` / `URL_SERVIDOR_SINALIZACAO` according to your environment[cite: 10]. The default configuration points to the project's hosted server[cite: 10]. Both participants must connect to the same server URL[cite: 10].

Create a room, copy the room code, and send it to the other person, who selects **Join a room**[cite: 10]. Calls start with microphone audio only; the camera can be toggled on afterward[cite: 10]. Both participants can share their screens in the same room, even simultaneously[cite: 10].

## Controls

- **Microphone:** Mutes or unmutes your voice[cite: 10].
- **Camera:** Toggles video on or off without rejoining (on Android, includes **Switch camera** between front and rear while active)[cite: 10].
- **Screen:** Selects a display or window (Desktop) or full screen (Android), with an option to include system/window audio where supported[cite: 10].
- **Right-click (M2) or Volume button (Android):** Opens independent **Peer voice** and **Stream audio** volume sliders, each ranging from 0% to 200%[cite: 10]. Values above 100% apply real gain; signals that are already loud may clip[cite: 10].
- **Mute Output (deafen):** Silences all incoming audio without muting your microphone, preserving individual channel volume levels[cite: 10].
- **Click/tap a video frame:** Spotlights that stream; volume controls do not affect frame sizing[cite: 10].
- **Fullscreen:** Expands the remote stream while keeping volume controls accessible (`Escape` or button on Desktop; system Back button on Android)[cite: 10].
- **Settings:** Selects microphone and output devices (including Bluetooth on Android), adjusts input/output gain, and toggles NS/AEC/AGC with real hardware/browser support checks[cite: 10].

## Local and Shared Profile

On the home screen, open **Edit name and photo** below the room options[cite: 10]. Choose a display name (up to 40 characters) and a JPG, PNG, or WebP photo (up to 8 MB), or remove the photo to fall back to your initials[cite: 10]. The image is center-cropped and downscaled to 256 × 256 before saving[cite: 10].

The profile is stored solely in local device storage (`unme.profile.v1`), with cloud backup disabled on Android (`allowBackup="false"`), and is sent directly over the `"profile"` `RTCDataChannel`[cite: 10].

## Monorepo Architecture

```text
protocol/
  SPEC.md / SPEC-en.md   Single protocol specification v1.0 / v1.1
  constants.json         Limits, timeouts, room code alphabet, default STUN, and screen-audio codec
  schemas/               JSON Schemas for signaling and RTCDataChannel payloads

signaling-server/
  server.js              Rooms capped at 2 participants; rate limiting, signal validation, and masked logs
  package-lock.json      Reproducible dependencies

electron-app/
  main.js                Sandboxed window, Socket.IO client over IPC, permissions, and native screen capture
  preload.js             Explicit contextBridge IPC layer between main process and UI
  config.js              Signaling URL and ICE servers
  media.js               WebRTC channels, RemoteAudioMixer, and ADPCM decoder for "screen-audio"
  microphone.js          Verified voice constraints and input gain node setup
  profile.js             Validation, local persistence, photo processing, and P2P profile channel
  profile-ui.js          Home screen profile editor
  renderer.js            Call lifecycle, device management, vertical layout adaptation, and controls
  index.html             Application UI
  style.css              Theme, layouts, and visual states

android/
  app/src/main/java/com/example/unme/
                         MainActivity, ServicoChamada, MotorChamadaWebRtc, ClienteSinalizacao,
                         MisturadorAudio, CapturadorAudioSistema, CodecAdpcm, RenderizadorTexturaVideo,
                         GerenciadorDispositivosAudio, GerenciadorPerfil, GerenciadorSala, ConfiguracaoApp
```

The connection negotiates three `sendrecv` channels: video (`video:primary`), voice (`audio:voice`), and screen audio (`audio:screen`)[cite: 10]. When Android shares screen audio, the PCM stream is compressed using 4-bit mono IMA-ADPCM at 24 kHz and sent over the `"screen-audio"` `DataChannel`, preserving independent volume control on the receiving end[cite: 10]. The first participant creates the channels; the second reuses the offer's channels before answering[cite: 10]. Switching between camera and screen uses `replaceTrack`/`setTrack` without creating a new room[cite: 10].

Remote audio is routed through separate Web Audio sources (Desktop) or native `AudioTrack` instances identified by `mid` (Android), preserving `0%–200%` gain, `deafen`, and voice-only speaking indicators[cite: 10].

### Branding and Icon

Open `docs/icon.html` in a browser to preview and download the `ü` mark as a 1024 × 1024 transparent PNG[cite: 10]. The two dots represent the two call participants[cite: 10]. The PNG and ICO assets used by the app and installer live in `electron-app/assets/`[cite: 10].

### Dependencies and Security Audit

Desktop uses Electron 44 and electron-builder 26 (Windows 64-bit / macOS 13+)[cite: 10]. Run `npm audit` in both `electron-app/` and `signaling-server/` to inspect known advisories for installed dependencies[cite: 10]. Overriding `global-agent` to 4.1.3 keeps the builder downloader free of an outdated vulnerable logging dependency chain[cite: 10]. On the server, `npm run dev` runs `node --watch server.js`[cite: 10].

## Building Releases

```powershell
# Desktop (Windows / macOS installer)
cd electron-app
npm run dist:win
# or: npm run dist:mac

# Android (debug APK)
cd ../android
.\gradlew.bat assembleDebug
```

Build outputs are generated in `electron-app/dist/` and `android/app/build/outputs/apk/debug/`[cite: 10].

## Hosted Server

Deploy `signaling-server/` on any Node.js hosting service[cite: 10]. Use `npm ci` to install dependencies and `npm start` to run the server[cite: 10]. The server reads `PORT` and optionally `ALLOWED_ORIGIN`; `/health` exposes server status and `protocolVersion`[cite: 10]. Clients on `v1.0` and `v1.1` can interoperate in the same room[cite: 10].

## Network and Privacy

The signaling server receives only connection metadata and room codes (logged strictly via an ephemeral session hash), never audio, video, or profile data[cite: 10]. Media flows encrypted end-to-end via WebRTC (DTLS-SRTP) between participants[cite: 10]. Room codes do not provide strong identity authentication; see full details in [SECURITY-PRIVACY-en.md](./SECURITY-PRIVACY-en.md)[cite: 10].

STUN alone may be insufficient behind CGNAT, symmetric NAT, or restrictive firewalls[cite: 10]. The project ships with STUN only by default; if TURN servers are configured later, follow the privacy plan without embedding hardcoded credentials in client builds[cite: 10].

On macOS, grant camera, microphone, and screen recording permissions in System Settings[cite: 10]. On Desktop, the Socket.IO client runs in the main process (`main.js`) behind an explicit IPC bridge in `preload.js`, allowing `sandbox: true`, `contextIsolation: true`, `nodeIntegration: false`, and blocked external navigation/window creation[cite: 10]. On Android, `allowBackup="false"` keeps profile data strictly on the device[cite: 10].