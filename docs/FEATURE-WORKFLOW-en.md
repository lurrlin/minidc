# New Feature Development Guide (Unme)

This guide ensures that every new feature or improvement in Unme is **planned once** for both **Desktop (Electron)** and **Android (Java)**, maintaining feature parity and protocol compatibility[cite: 9].

---

## Gate 0 - Mandatory Identity Filter

Before designing any UI screen or network message, confirm that the proposal **does not violate** any product boundary[cite: 9]:

- [ ] Does the session remain strictly limited to **exactly 2 participants**[cite: 9]?
- [ ] Does it work without a database, conversation history, text chat, or social features[cite: 9]?
- [ ] Does it preserve simplicity and a minimal number of steps to connect[cite: 9]?
- [ ] Does it avoid unnecessary content collection or persistence[cite: 9]?

If any answer is **no**, the feature is out of scope for Unme and must be discarded or redesigned[cite: 9].

---

## Standard 9-Step Workflow

1. **Define expected behavior**
   - Document the user flow, visual states, numeric limits, UI copy, and error cases in 1 page[cite: 9].
2. **Identify shared business rules**
   - Identical validations, limits, and calculations across JS and Java must be added to [`protocol/constants.json`](../protocol/constants.json)[cite: 9].
3. **Identify required protocol changes**
   - Define any new fields in `SignalPayload`, new signaling events, or `RTCDataChannel` messages[cite: 9].
   - Update [`protocol/SPEC-en.md`](../protocol/SPEC-en.md) and [`protocol/schemas/`](../protocol/schemas/)[cite: 9].
   - Maintain backwards compatibility with clients on the previous protocol version (optional fields or negotiation via `protocolVersion`)[cite: 9].
4. **Assess Signaling Server impact (`signaling-server/`)**
   - Only modify [`signaling-server/server.js`](../signaling-server/server.js) if session coordination requires it[cite: 9].
   - Never store content, never log `roomName` or personal data, and validate the new payload on the server[cite: 9].
5. **Plan Desktop implementation (`electron-app/`)**
   - Map changes across the main process (`main.js`), `preload.js` (preserving `contextIsolation: true` and `sandbox`), `media.js`/`renderer.js`, and UI (`index.html`/`style.css`)[cite: 9].
6. **Plan Android implementation (`android/`)**
   - Map runtime permissions, lifecycle (`MainActivity`/`ServicoChamada`), native APIs (`MediaProjection`, `AudioManager`, `Camera2`), XML layouts, and codebase naming conventions[cite: 9].
7. **Implement one platform at a time with small commits**
   - Always work on a separate branch using atomic commits (e.g., first `protocol/`, then `android/`, then `electron-app/`, then `signaling-server/`)[cite: 9].
8. **Validate builds and interoperability**
   - Build the Desktop (`npm run dist:win`) and Android (`./gradlew.bat assembleDebug`) packages and manually test Desktop <-> Android interoperability on physical devices[cite: 9].
9. **Update documentation**
   - Update `README.md`, `docs/DEVELOPMENT-en.md`, and `protocol/SPEC-en.md` within the same delivery cycle[cite: 9].