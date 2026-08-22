# Mini Discord — chamada P2P para 2 pessoas

App desktop (Windows/Mac) com audio, video e compartilhamento de tela,
feito com Electron + WebRTC. Sem servidor de midia: depois do "aperto de
mao" inicial, video e audio trafegam direto entre os dois computadores.

## 1. Estrutura de pastas

```
mini-discord/
├── signaling-server/       # Node.js + Express + Socket.io — SO troca SDP/ICE
│   ├── server.js
│   ├── package.json
│   ├── .env.example
│   └── .gitignore
│
└── electron-app/           # O app instalável
    ├── main.js             # Main process: janela + desktopCapturer + IPC
    ├── preload.js           # Ponte segura (contextBridge) Main <-> Renderer
    ├── config.js            # URL do signaling server + ICE servers (STUN/TURN)
    ├── index.html            # UI (lobby + tela de chamada + seletor de tela)
    ├── style.css              # Dark mode
    ├── renderer.js             # Toda a lógica de WebRTC / Socket.io
    ├── package.json
    └── .gitignore
```

Por que separado assim: o signaling server não sabe nada sobre WebRTC além
de repassar mensagens — pode ficar hospedado 24h de graça em algo como
Render ou Railway. O app Electron é só instalado nos dois computadores e
aponta para essa URL.

## 2. Pré-requisitos

- Node.js 18+ e npm instalados nos dois computadores (e na máquina de onde
  você vai fazer o deploy do signaling server, se for a mesma).
- Uma conta grátis em [Render](https://render.com) ou
  [Railway](https://railway.app) para hospedar o `signaling-server`.

## 3. Signaling server

### 3.1 Rodando localmente (para testar)

```bash
cd signaling-server
npm install
npm start
# "Signaling server ouvindo na porta 3000"
```

### 3.2 Deploy (Render, exemplo)

1. Suba a pasta `signaling-server/` para um repositório Git (pode ser um
   repo privado só para isso).
2. No Render: **New > Web Service** → conecte o repo.
   - **Build command:** `npm install`
   - **Start command:** `npm start`
   - Nenhuma variável de ambiente é obrigatória (o `PORT` é injetado
     automaticamente pelo Render).
3. Ao terminar o deploy, copie a URL pública, algo como
   `https://mini-discord-signaling.onrender.com`.

No Railway o processo é equivalente (New Project → Deploy from GitHub repo
→ ele detecta o `npm start` sozinho).

> **Nota sobre planos gratuitos:** Render/Railway free tier costuma
> "dormir" o serviço depois de alguns minutos sem uso e leva ~30s para
> acordar na próxima conexão. Isso só atrasa a entrada na sala — não afeta
> a chamada em si, que já é P2P direto.

## 4. Electron app

### 4.1 Configurar a URL do servidor

Abra `electron-app/config.js` e troque:

```js
SIGNALING_SERVER_URL: 'http://localhost:3000',
```

pela URL que você copiou do Render/Railway, ex:

```js
SIGNALING_SERVER_URL: 'https://mini-discord-signaling.onrender.com',
```

### 4.2 Rodar em desenvolvimento

```bash
cd electron-app
npm install
npm start
```

Isso abre a janela do Electron já apontando para o signaling server
configurado. Repita `npm start` no computador da sua namorada (com o
mesmo `config.js` apontando pro mesmo servidor) e digitem o **mesmo nome
de sala** nos dois — a chamada conecta sozinha.

### 4.3 Gerar o instalável (.exe / .dmg)

```bash
cd electron-app
npm run dist        # gera para a plataforma atual
npm run dist:win    # forçar build Windows (.exe via NSIS)
npm run dist:mac    # forçar build macOS (.dmg)
```

Os artefatos saem em `electron-app/dist/`.

> **Importante sobre cross-compiling:** o `electron-builder` só gera
> `.dmg` assinado corretamente rodando em uma máquina macOS. Se você só
> tem Windows, rode `npm run dist:win` aí e peça para ela rodar
> `npm run dist:mac` no Mac dela (ou usar `npm start` direto em modo dev,
> que também funciona perfeitamente sem gerar instalador).

## 5. Como a arquitetura resolve os pontos "MUITO IMPORTANTE" do briefing

- **`RTCConfiguration` sólida:** `config.js` define STUN público do Google
  (`stun.l.google.com:19302` + um segundo STUN de backup) e
  `iceCandidatePoolSize: 10`. Há um slot pronto para adicionar um servidor
  TURN — veja a seção de troubleshooting abaixo sobre quando isso é
  necessário.
- **ICE candidates e reconexão:** candidatos ICE que chegam antes da
  `remoteDescription` estar pronta são enfileirados e aplicados depois
  (`pendingCandidates`), evitando erros de corrida. Se a `RTCPeerConnection`
  cair (`disconnected`/`failed`), o app tenta um **ICE restart** automático
  depois de alguns segundos. Se a conexão do Socket.io cair, o cliente
  reconecta sozinho e reanuncia a sala.
- **Context Isolation:** `contextIsolation: true`, `nodeIntegration: false`
  e `sandbox: true` em `main.js`. Tudo que o renderer usa (IPC de
  compartilhamento de tela, permissões de mídia, e até o `socket.io-client`,
  que precisa de `require`) é exposto de forma explícita e mínima via
  `contextBridge` em `preload.js` — o renderer nunca vê `require` ou
  `ipcRenderer` crus.
- **`desktopCapturer` no Main process:** `main.js` chama
  `desktopCapturer.getSources()` (API que não existe mais no renderer desde
  o Electron 20) e converte os thumbnails para `dataURL` antes de mandar
  pro renderer via `ipcMain.handle('get-screen-sources')`.
- **Troca de track na hora de compartilhar tela:** em `renderer.js`,
  `startScreenShare()` pega a stream da tela escolhida e chama
  `sender.replaceTrack(screenTrack)` no sender de vídeo já existente da
  `RTCPeerConnection` — **sem renegociar** offer/answer. `stopScreenShare()`
  faz o caminho inverso, voltando pra `cameraTrack`.

## 6. Troubleshooting

**A chamada nunca conecta entre as duas casas (STUN não é suficiente).**
STUN resolve NAT tradicional, mas não redes com CGNAT ou NAT simétrico
(comum em algumas operadoras de internet residencial no Brasil). Se depois
de testar em ambas as redes a conexão nunca fechar, o próximo passo é
adicionar um servidor **TURN** em `config.js` (ex: um plano gratuito da
Twilio, Cloudflare Calls, Metered.ca, ou um `coturn` auto-hospedado) — é
só adicionar mais uma entrada em `ICE_SERVERS` com `urls`, `username` e
`credential`.

**Câmera/microfone não pedem permissão no Mac (build empacotado).**
Já está coberto: `package.json` do Electron app define
`NSCameraUsageDescription` / `NSMicrophoneUsageDescription` no
`extendInfo` do `build.mac`, e `main.js` chama
`systemPreferences.askForMediaAccess()` explicitamente no macOS.

**Tela preta ao compartilhar tela.**
Normalmente é permissão de "Gravação de Tela" do macOS (System Settings →
Privacy & Security → Screen Recording) — precisa marcar o app manualmente
na primeira vez e reabrir o app.

**Firewall corporativo/rede muito restritiva.**
Nesse cenário raramente nem STUN nem TURN sobre UDP resolvem; um TURN
configurado para também aceitar TURN-over-TCP/TLS na porta 443 é o que
mais aumenta a chance de conectar.

## 7. Segurança / privacidade

O signaling server nunca vê áudio, vídeo, nem o conteúdo compartilhado —
só troca metadados de conexão (SDP/ICE) entre os dois sockets da mesma
sala, e descarta tudo quando os dois saem. Ainda assim, como as salas são
identificadas só pelo nome digitado, use um nome de sala que não seja
óbvio de adivinhar (não precisa ser senha forte, mas evite algo como
"sala1").
