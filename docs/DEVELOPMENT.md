# Desenvolvimento do Unme

Este guia é para quem trabalha no código. Para instalar e usar o aplicativo, consulte o [README](../README.md).

## Rodar o projeto

Use Node.js 22.12 ou mais recente e npm. São dois processos separados para o Desktop, além do projeto Android Studio em `android/`:

```powershell
# Terminal 1 — servidor local
cd signaling-server
npm ci
npm start

# Terminal 2 — aplicativo Desktop
cd electron-app
npm ci
npm start
```

`electron-app/config.js` e `android/app/src/main/java/com/example/unme/ConfiguracaoApp.java` contêm a URL do servidor. Para usar o servidor local, defina `SIGNALING_SERVER_URL` / `URL_SERVIDOR_SINALIZACAO` conforme o ambiente. A configuração padrão aponta para o servidor hospedado do projeto. Os participantes precisam usar a mesma URL.

Crie uma sala, copie o código e envie para a outra pessoa. Ela usa **Entrar em uma sala**. A chamada começa apenas com microfone; a câmera pode ser ativada depois. Cada participante pode compartilhar sua tela na mesma sala, inclusive ao mesmo tempo.

## Controles

- **Microfone:** liga ou silencia sua voz.
- **Câmera:** ativa ou desativa o vídeo, sem precisar reentrar (no Android inclui **Trocar câmera** frontal/traseira enquanto ligada).
- **Tela:** escolhe uma tela ou janela (Desktop) ou tela inteira (Android); inclui opção para áudio da transmissão quando suportado.
- **Botão direito (M2) ou botão Volume (Android):** abre os controles **Voz da pessoa** e **Áudio da transmissão**, cada um de 0% a 200%, em canais independentes. Acima de 100% aplica ganho real; sinais já muito altos podem distorcer.
- **Ouvir (deafen):** silencia todo o áudio recebido sem desligar o microfone, preservando os níveis individuais dos canais.
- **Clique/toque no quadro:** destaca aquela tela; os controles de volume não mudam seu tamanho.
- **Tela cheia:** amplia a transmissão remota mantendo acesso aos volumes (`Escape` ou botão no Desktop; botão Voltar no Android).
- **Configurações:** escolhe microfone e saída (incluindo Bluetooth no Android), ajusta ganho de entrada/saída e alterna NS/AEC/AGC com verificação de suporte real.

## Perfil local e compartilhado

Na tela inicial, use **Editar nome e foto** abaixo das opções de sala. Escolha seu nome (até 40 caracteres) e uma foto JPG, PNG ou WebP (até 8 MB), ou remova a foto para usar suas iniciais. A imagem é recortada ao centro e reduzida para 256 × 256 antes de salvar.

O perfil fica apenas no armazenamento local do dispositivo (`unme.profile.v1`), com backup em nuvem desativado no Android (`allowBackup="false"`), e viaja diretamente pelo `RTCDataChannel` `"profile"`.

## Arquitetura do Monorepo

```text
protocol/
  SPEC.md            Especificação única do protocolo v1.0 / v1.1
  constants.json     Limites, timeouts, alfabeto, STUN default e codec screen-audio
  schemas/           JSON Schemas de sinalização e RTCDataChannel
  fixtures/          Casos de teste compartilhados consumidos em JS e Java

signaling-server/
  server.js          Salas de até 2 participantes; rate limit, validação de sinal e logs mascarados
  tests/             Testes automatizados com Socket.IO real
  package-lock.json  Dependências reproduzíveis

electron-app/
  main.js            Janela sandboxed, cliente Socket.IO via IPC, permissões e captura nativa
  preload.js         Ponte explícita via contextBridge entre processo principal e interface
  config.js          URL de sinalização e servidores ICE
  media.js           Canais WebRTC, RemoteAudioMixer e decodificador ADPCM do canal "screen-audio"
  microphone.js      Restrições verificadas de voz e criação de ganho
  profile.js         Validação, persistência, foto e canal P2P do perfil
  profile-ui.js      Editor de perfil da tela inicial
  renderer.js        Ciclo da chamada, dispositivos, adaptação vertical e controles
  index.html         Interface em português
  style.css          Tema, layouts e estados visuais
  tests/             Contratos, regressões no Chromium real, UI, smoke e performance

android/
  app/src/main/java/com/example/unme/
                     MainActivity, ServicoChamada, MotorChamadaWebRtc, ClienteSinalizacao,
                     MisturadorAudio, CapturadorAudioSistema, CodecAdpcm, RenderizadorTexturaVideo,
                     GerenciadorDispositivosAudio, GerenciadorPerfil, GerenciadorSala, ConfiguracaoApp
  app/src/test/      Testes unitários e validação das fixtures compartilhadas de protocol/

tests/interop/
  signaling-interop.test.cjs   Handshake e reconexão multiplataforma Desktop ↔ Android
  CHECKLIST-INTEROP.md         Roteiro manual 2×2 em aparelhos reais
```

A conexão negocia três canais `sendrecv`: vídeo (`video:primary`), voz (`audio:voice`) e áudio da tela (`audio:screen`). Quando o Android transmite áudio da tela, o fluxo PCM é comprimido em IMA-ADPCM 4-bit mono 24 kHz e enviado pelo `DataChannel` `"screen-audio"`, mantendo controle independente de volume no receptor. O primeiro participante cria os canais; o segundo reutiliza os canais da oferta antes de responder. A troca câmera/tela usa `replaceTrack`/`setTrack`, sem nova sala.

O áudio remoto passa por fontes separadas de Web Audio (Desktop) ou `AudioTrack` nativos identificados por `mid` (Android), preservando ganho `0%–200%`, `deafen` e indicador de fala apenas para a voz.

## Testes

```powershell
# Servidor de sinalização
npm --prefix signaling-server test

# Desktop (contratos, regressão/UI no Chromium e build)
npm --prefix electron-app run test:contract
npm --prefix electron-app test
npm --prefix electron-app run test:build

# Android (unitários + contratos + APK debug)
cd android ; .\gradlew.bat testDebugUnitTest assembleDebug ; cd ..

# Interoperabilidade multiplataforma
node --test tests/interop/signaling-interop.test.cjs
```

Os testes do Desktop usam o Chromium incluído no Electron, vídeo gerado por canvas e dispositivos simulados. Os do servidor e os de interoperabilidade abrem uma porta local aleatória com Socket.IO real. Os testes do Android validam regras de negócio, ADPCM, roteamento de mídia e todas as fixtures de `protocol/fixtures/`. Consulte [CHECKLIST-INTEROP.md](../tests/interop/CHECKLIST-INTEROP.md) para validação física 2×2.

### Desempenho e encerramento de chamadas

Em `electron-app/`, execute `npm run test:performance`. O teste mantém duas transmissões simultâneas de canvas em 1280 × 720 a 30 fps por 30 segundos no renderer de produção e gera `tests/artifacts/performance.json`.

### Marca e ícone

Abra `docs/icon.html` no navegador para visualizar e baixar o símbolo `ü` em PNG transparente de 1024 × 1024 pixels. Os dois pontos representam os participantes. O PNG e o ICO usados no app e no instalador ficam em `electron-app/assets/`.

Para exportar novamente o PNG a partir da página, execute `npx electron tests/export-icon.cjs` em `electron-app/`. Ao alterar a marca, atualize também o ICO com os tamanhos 16, 24, 32, 48, 64, 128 e 256 pixels antes de gerar o instalador.

### Dependências e auditoria de segurança

O Desktop usa Electron 44 e electron-builder 26 (Windows 64 bits / macOS 13+). Execute `npm audit` tanto em `electron-app/` quanto em `signaling-server/` para conferir os avisos conhecidos das dependências instaladas. O override de `global-agent` para 4.1.3 é verificado em `npm run test:build`.

Depois de gerar o instalador Windows, execute `npm run test:packaged` para validar o `app.asar` empacotado. No servidor, `npm run dev` usa `node --watch server.js`.

## Gerar instalador

```powershell
cd electron-app
npm run dist:win
# No macOS:
npm run dist:mac
```

Os arquivos saem em `electron-app/dist/`.

## Servidor hospedado

Hospede `signaling-server/` em um serviço Node.js. Use `npm ci` para instalação e `npm start` para inicialização. O servidor lê `PORT` e opcionalmente `ALLOWED_ORIGIN`; `/health` expõe seu estado e `protocolVersion`. Clientes `v1.0` e `v1.1` convivem na mesma sala.

## Rede e privacidade

O servidor de sinalização recebe metadados de conexão e códigos de sala (logados apenas por hash efêmero de sessão), nunca áudio, vídeo ou perfil. A mídia segue criptografada via WebRTC (DTLS-SRTP) entre os participantes. Os códigos não são autenticação com identidade forte; veja os detalhes completos em [SECURITY-PRIVACY.md](./SECURITY-PRIVACY.md).

STUN pode ser insuficiente em redes com CGNAT, NAT simétrico ou firewall restritivo. O projeto mantém apenas STUN por padrão; caso necessário futuramente, configure servidores TURN seguindo o plano de privacidade sem embutir credenciais fixas nos clientes.

No macOS, autorize câmera, microfone e gravação de tela nas configurações do sistema. No Desktop, o cliente Socket.IO executa no processo principal (`main.js`) atrás de uma ponte explícita IPC no `preload.js`, permitindo manter `sandbox: true`, `contextIsolation: true`, `nodeIntegration: false`, navegação externa e abertura de janelas bloqueadas. No Android, `allowBackup="false"` mantém o perfil exclusivamente no aparelho.
