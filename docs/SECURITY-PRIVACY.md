# Segurança, Privacidade e Modelo de Dados — Unme

Este documento descreve de forma transparente o que o Unme transporta, o que armazena e quais garantias e limites reais existem hoje na arquitetura.

---

## 1. Classificação de Dados

| Categoria | Onde existe | Tempo de vida | Observação |
| :--- | :--- | :--- | :--- |
| **Conteúdo de mídia (voz, câmera, tela)** | Fluxo P2P direto WebRTC (DTLS-SRTP) | Apenas em tempo real; jamais gravado | Não passa pelo servidor de sinalização |
| **Dados de perfil (`name`, `photo`)** | Dispositivo local (`unme.profile.v1`) + `RTCDataChannel` `"profile"` | Persistido apenas localmente até o usuário remover/editar | Não passa pelo servidor de sinalização |
| **Metadados técnicos de sessão** | Memória RAM do Signaling Server (`rooms`) | Enquanto a sala tiver 1 ou 2 participantes | Apagado imediatamente quando a sala esvazia |
| **Logs operacionais do servidor** | Console do processo Node.js (Render) | Efêmero / retenção padrão do host | Regra do projeto: **proibido logar código de sala em texto claro** ou IP/SDP |

---

## 2. O que a Criptografia do WebRTC Garante (e o que NÃO Garante)

- **Garante:** toda mídia (RTP → SRTP) e todo `DataChannel` (SCTP → DTLS) são criptografados em trânsito entre os dois endpoints WebRTC. Um observador passivo na rede ou provedor de internet não lê voz, vídeo, tela ou perfil.
- **Não garante isoladamente:** **autenticação de identidade** contra um atacante ativo que controle o servidor de sinalização (MitM na troca de SDP/fingerprints DTLS). Sem contas de usuário ou verificação fora de banda, o servidor de sinalização é confiado para entregar o SDP original de cada par.
- **Mitigação prevista sem contas/banco de dados:** manter o código da sala secreto (16 caracteres gerados criptograficamente) e, em etapas futuras de segurança, suportar verificação visual opcional derivada dos fingerprints DTLS local + remoto.

---

## 3. Controles de Privacidade por Componente

- **Signaling Server (`signaling-server/`):**
  - Capacidade máxima fixa em `2` participantes por sala.
  - Nenhum banco de dados conectado.
  - Trava de tamanho máximo (`128 KB`) e whitelist estrita de tipos de sinal.
  - Plano de endurecimento (Etapa 4): mascaramento de `roomName` em logs, rate limiting por socket/IP e validação estrutural por tipo de sinal.
- **Desktop (`electron-app/`):**
  - `contextIsolation: true`, `nodeIntegration: false`, bloqueio de navegação externa e abertura de novas janelas.
  - Perfil salvo apenas no armazenamento local do Electron.
  - Plano de endurecimento (Etapa 3): mover `socket.io-client` para o processo principal e ativar `sandbox: true`.
- **Android (`android/`):**
  - Captura de tela sempre exige confirmação explícita do sistema operacional (`MediaProjection`) e notificação persistente (`ServicoChamada`).
  - Plano de endurecimento (Etapa 2): `android:allowBackup="false"` para impedir cópia do perfil local em backups de nuvem.
