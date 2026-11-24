# Unme

[Versão em Português](README.pt-br.md)

A space to talk, watch, and share. Just the two of you.

Unme is a **cross-platform** application (**Windows/macOS Desktop** and **Android**) for voice calls, video calls, and bidirectional screen sharing between **exactly two people**, with no accounts, no text chat, and no conversation database.

**[Download Unme for Windows →](https://github.com/lurrlin/minidc/releases)**

## Platforms

- **Desktop (`electron-app/`):** Electron with native Chromium WebRTC, per-window audio capture via WASAPI (`electron-native-screenshare`), and independent audio mixing with the Web Audio API.
- **Android (`android/`):** Native Java application (`minSdk 24`, Android 7.0+) using WebRTC (`stream-webrtc-android`), front/rear camera switching, `MediaProjection` for screen sharing, automatic orientation handling, and optional internal audio capture (Android 10+).
- **Signaling Server (`signaling-server/`):** Node.js + Socket.IO used strictly to pair both participants in a room and exchange connection metadata (SDP/ICE).

Desktop and Android interoperate directly within the same room using the shared contract defined in the [protocol specification](protocol/SPEC.md).

## Starting a Call

1. Click or tap **Create room**.
2. Copy the room code and send it to your partner.
3. The other person selects **Join a room**, pastes the code, and confirms.
4. The call connects automatically once both participants are in the room.

Each room allows a maximum of **2 people**. The camera starts off by default and can be enabled at any time during the call. Share the room code only with the person you want to invite.

## Your Profile

On the home screen, open **Edit name and photo** to customize how you appear during calls:

- Enter a display name (up to **40 characters**) and pick a **JPG, PNG, or WebP** image up to **8 MB**.
- The image is cropped and compressed locally to **256 × 256** before saving.
- Your name and photo are stored exclusively on your device (`unme.profile.v1`), with cloud backup disabled on Android (`allowBackup="false"`), and are transmitted directly to the other participant over a P2P `RTCDataChannel`.

## Call Controls

| Control | Desktop | Android |
| --- | --- | --- |
| **Microphone** | Mutes or unmutes your voice. | Mutes or unmutes your voice. |
| **Camera** | Toggles your webcam on or off. | Toggles the camera and shows **Switch camera** (front/rear) while active. |
| **Screen** | Selects a monitor or window and optionally captures its audio. | Shares the full screen and optionally captures internal audio (Android 10+; DRM-protected apps may mute audio). |
| **Mute Output** | Mutes all incoming audio without muting your microphone. | Mutes all incoming audio without muting your microphone. |
| **Remote Volumes** | Right-click (M2) on your partner's video frame to adjust **Peer voice** and **Stream audio** (`0%–200%`). | **Volume** button / long-press on the remote frame with the same two sliders (`0%–200%`). |
| **Fullscreen / Spotlight** | Click a frame to spotlight it; **Fullscreen** button or `Escape` to exit. | Tap to spotlight; **Fullscreen** button or system Back button to exit. |
| **Settings ⚙** | Audio devices, input/output gain, and verified NS/AEC/AGC processing. | Audio devices (including Bluetooth), input/output gain, and verified NS/AEC/AGC processing. |

## Privacy and Security

- Audio, video, screen share, and profile payloads are encrypted end-to-end via WebRTC (**DTLS-SRTP** / **SCTP**), never pass through the signaling server, and are never recorded.
- The signaling server logs room codes only opaquely using an ephemeral session hash, enforces rate limits against room enumeration, and validates all signaling payloads.
- Full details on the threat model are documented in [security and privacy](docs/SECURITY-PRIVACY.md).

## Technical Documentation

- [Official Protocol Specification (`v1.0` / `v1.1`)](protocol/SPEC.md)
- [Development Guide](docs/DEVELOPMENT.md)
- [Security and Privacy](docs/SECURITY-PRIVACY.md)
- [Mandatory Workflow for New Features](docs/FEATURE-WORKFLOW.md)
