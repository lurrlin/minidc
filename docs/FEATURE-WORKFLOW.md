# Guia de Desenvolvimento de Novas Funcionalidades (Unme)

Este guia garante que toda nova funcionalidade ou melhoria do Unme seja **planejada uma única vez** para **Desktop (Electron)** e **Android (Java)**, mantendo paridade funcional e compatibilidade de protocolo.

---

## Gate 0 — Filtro Obrigatório de Identidade

Antes de desenhar qualquer tela ou mensagem de rede, confirme que a proposta **não viola** nenhum limite do produto:

- [ ] A sessão continua limitada a **exatamente 2 participantes**?
- [ ] Funciona sem banco de dados, sem histórico, sem chat e sem recursos sociais?
- [ ] Preserva simplicidade e poucas etapas para conectar?
- [ ] Evita coleta ou persistência desnecessária de conteúdo?

Se qualquer resposta for **não**, a funcionalidade está fora do escopo do Unme e deve ser descartada ou redesenhada.

---

## Fluxo Padrão em 9 Etapas

1. **Definir o comportamento esperado**
   - Descreva o fluxo do usuário, estados visuais, limites numéricos, textos em português e casos de erro em 1 página.
2. **Identificar regras de negócio compartilhadas**
   - Validações, limites e cálculos idênticos entre JS e Java devem ser adicionados a [`protocol/constants.json`](../protocol/constants.json).
3. **Identificar alterações necessárias no protocolo**
   - Defina se há novos campos em `SignalPayload`, novos eventos ou mensagens em `RTCDataChannel`.
   - Atualize [`protocol/SPEC.md`](../protocol/SPEC.md) e [`protocol/schemas/`](../protocol/schemas/).
   - Mantenha retrocompatibilidade com clientes na versão anterior (campos opcionais ou negociação via `protocolVersion`).
4. **Avaliar impacto no Signaling Server (`signaling-server/`)**
   - Só altere [`signaling-server/server.js`](../signaling-server/server.js) se a coordenação da sessão exigir.
   - Nunca armazene conteúdo, nunca faça log de `roomName` ou dados pessoais, e valide o novo payload no servidor.
5. **Planejar a implementação Desktop (`electron-app/`)**
   - Mapeie impacto no processo principal (`main.js`), `preload.js` (mantendo `contextIsolation: true` e `sandbox`), `media.js`/`renderer.js` e UI (`index.html`/`style.css`).
6. **Planejar a implementação Android (`android/`)**
   - Mapeie permissões em tempo de execução, ciclo de vida (`MainActivity`/`ServicoChamada`), APIs nativas (`MediaProjection`, `AudioManager`, `Camera2`), layouts XML e nomenclatura em português.
7. **Implementar uma plataforma por vez com commits pequenos**
   - Trabalhe sempre em branch separada, com commits atômicos e em português (ex.: primeiro `protocol/`, depois `android/`, depois `electron-app/`, depois `signaling-server/`).
8. **Validar builds e interoperabilidade**
   - Gere os pacotes de Desktop (`npm run dist:win`) e Android (`./gradlew.bat assembleDebug`) e valide manualmente Desktop ↔ Android em aparelhos reais.
9. **Atualizar documentação**
   - Atualize `README.md`, `docs/DEVELOPMENT.md` e `protocol/SPEC.md` no mesmo ciclo da entrega.
