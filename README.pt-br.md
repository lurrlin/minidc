# Unme

Um espaço para conversar, ver e compartilhar. Só vocês dois.

O Unme é um aplicativo **multiplataforma** (**Desktop Windows/macOS** e **Android**) para chamadas de voz, vídeo e compartilhamento bidirecional de tela entre **exatamente duas pessoas**, sem contas, sem chat de texto e sem banco de dados de conversas.

**[Baixar o Unme para Windows →](https://github.com/lurrlin/minidc/releases)**

| Tela Inicial e Perfil | Chamada Ativa e Compartilhamento de Tela |
| :---: | :---: |
| ![Unme Tela Inicial](https://github.com/user-attachments/assets/b9dad19c-beb0-4b02-ad3a-8587e4cc392e) | ![Unme Chamada](https://github.com/user-attachments/assets/00cdd292-850b-43d4-85ef-62b4daf7179c) |

## Plataformas

- **Desktop (`electron-app/`):** Electron com WebRTC nativo do Chromium, captura de áudio de janela via WASAPI (`electron-native-screenshare`) e mixagem independente com Web Audio API.
- **Android (`android/`):** aplicativo nativo em Java (`minSdk 24`, Android 7.0+) com WebRTC (`stream-webrtc-android`), alternância entre câmera frontal e traseira, `MediaProjection` para tela compartilhada, adaptação automática ao girar o aparelho e captura opcional de áudio interno (Android 10+).
- **Servidor de sinalização (`signaling-server/`):** Node.js + Socket.IO usado apenas para reunir as duas pessoas na sala e trocar metadados de conexão (SDP/ICE).

Desktop e Android interoperam diretamente na mesma sala usando o contrato compartilhado em [especificação do protocolo](protocol/SPEC.md).

## Começar uma chamada

1. Clique ou toque em **Criar sala**.
2. Copie o código da sala e envie para sua companhia.
3. A outra pessoa escolhe **Entrar em uma sala**, cola o código e confirma.
4. A chamada conecta quando os dois estão na sala.

Cada sala recebe no máximo **2 pessoas**. A câmera começa desligada e pode ser ligada a qualquer momento durante a chamada. Compartilhe o código apenas com quem você quer convidar.

## Seu perfil

Na tela inicial, abra **Editar nome e foto** para escolher como você aparece na chamada:

- Digite seu nome (até **40 caracteres**) e escolha uma foto **JPG, PNG ou WebP** de até **8 MB**.
- A foto é recortada e compactada localmente para **256 × 256** antes de salvar.
- O nome e a foto ficam salvos apenas neste dispositivo (`unme.profile.v1`), com backup em nuvem desativado no Android (`allowBackup="false"`), e são enviados diretamente ao outro participante por `RTCDataChannel` P2P.

## Controles da chamada

| Controle | Desktop | Android |
| --- | --- | --- |
| **Microfone** | Silencia ou reativa sua voz. | Silencia ou reativa sua voz. |
| **Câmera** | Liga ou desliga a webcam. | Liga/desliga a câmera e exibe **Trocar câmera** (frontal/traseira) quando ativa. |
| **Tela** | Escolhe monitor/janela e opcionalmente captura áudio. | Compartilha a tela inteira e opcionalmente áudio interno (Android 10+; apps DRM podem silenciar). |
| **Ouvir** | Silencia toda a saída recebida sem mutar seu microfone. | Silencia toda a saída recebida sem mutar seu microfone. |
| **Volumes remotos** | Botão direito (M2) no quadro da companhia para **Voz da pessoa** e **Áudio da transmissão** (`0%–200%`). | Botão **Volume** / toque longo no quadro remoto com os mesmos dois sliders (`0%–200%`). |
| **Tela cheia / Destaque** | Clique no quadro para destacar; **Tela cheia** ou `Escape`. | Toque para destacar; botão **Tela cheia** e botão Voltar do sistema. |
| **Configurações ⚙** | Dispositivos, ganho de entrada/saída e NS/AEC/AGC verificados. | Dispositivos (incluindo Bluetooth), ganho de entrada/saída e NS/AEC/AGC verificados. |

## Privacidade e segurança

- O conteúdo de áudio, vídeo, tela e perfil trafega criptografado ponto a ponto via WebRTC (**DTLS-SRTP** / **SCTP**) sem passar pelo servidor de sinalização e nunca é gravado.
- O servidor de sinalização registra códigos de sala apenas de forma opaca por hash efêmero de sessão, aplica limites de taxa contra enumeração e valida os payloads de sinalização.
- Detalhes transparentes do modelo de ameaças estão em [segurança e privacidade](docs/SECURITY-PRIVACY.md).

## Documentação técnica

- [Especificação oficial do protocolo (`v1.0` / `v1.1`)](protocol/SPEC.md)
- [Guia de desenvolvimento](docs/DEVELOPMENT.md)
- [Segurança e privacidade](docs/SECURITY-PRIVACY.md)
- [Fluxo obrigatório para novas funcionalidades](docs/FEATURE-WORKFLOW.md)
