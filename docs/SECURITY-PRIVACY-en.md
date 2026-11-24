# Security, Privacy, and Data Model - Unme

This document transparently describes what data Unme transmits, what it stores, and the actual guarantees and limitations of the current architecture[cite: 12].

---

## 1. Data Classification

| Category | Where It Exists | Lifetime | Notes |
| :--- | :--- | :--- | :--- |
| **Media content (voice, camera, screen)** | Direct P2P WebRTC stream (DTLS-SRTP) | Real-time only; never recorded | Never passes through the signaling server[cite: 12] |
| **Profile data (`name`, `photo`)** | Local device (`unme.profile.v1`) + `"profile"` `RTCDataChannel` | Stored locally until removed or edited by the user | Never passes through the signaling server[cite: 12] |
| **Session technical metadata** | Signaling Server RAM (`rooms`) | While the room has 1 or 2 participants | Deleted immediately when the room becomes empty[cite: 12] |
| **Server operational logs** | Node.js process console (Render) | Ephemeral / standard host log retention | Project rule: **logging plaintext room codes**, IPs, or SDPs is strictly prohibited[cite: 12] |

---

## 2. What WebRTC Encryption Guarantees (and What It Does NOT Guarantee)

- **Guarantees:** All media (RTP -> SRTP) and all `DataChannel` traffic (SCTP -> DTLS) are encrypted in transit between the two WebRTC endpoints[cite: 12]. Passive network observers or ISPs cannot read voice, video, screen, or profile payloads[cite: 12].
- **Does NOT guarantee on its own:** **Identity authentication** against an active attacker controlling the signaling server (MitM during SDP / DTLS fingerprint exchange)[cite: 12]. Without user accounts or out-of-band verification, the signaling server is trusted to relay each peer's unmodified SDP[cite: 12].
- **Planned mitigation without accounts or databases:** Keep room codes secret (16 cryptographically generated characters) and, in future security milestones, support optional visual verification derived from local + remote DTLS fingerprints[cite: 12].

---

## 3. Privacy Controls by Component

- **Signaling Server (`signaling-server/`):**
  - Hard room capacity capped at `2` participants.
  - No database connected.
  - Maximum payload cap (`128 KB`) and strict allowlist of signaling message types.
  - Implemented hardening: ephemeral session hash masking for `roomName` in logs, rate limiting against room enumeration, and structural validation per signal type.
- **Desktop (`electron-app/`):**
  - `sandbox: true`, `contextIsolation: true`, `nodeIntegration: false`, blocked external navigation and new window creation.
  - `socket.io-client` isolated in the main process (`main.js`) behind an explicit IPC bridge in `preload.js`.
  - Profile saved strictly in Electron local storage.
- **Android (`android/`):**
  - Screen capture always requires explicit OS user consent (`MediaProjection`) and a persistent foreground notification (`ServicoChamada`).
  - `android:allowBackup="false"` enabled to prevent local profile extraction via cloud backups.
