# Unme

Aplicativo desktop para duas pessoas, com voz, câmera e compartilhamento simultâneo de tela. Feito com Electron, WebRTC e Socket.IO.

## Rodar o projeto

Use Node.js 22 ou mais recente e npm. São dois processos separados:

```powershell
# Terminal 1 — servidor local
cd signaling-server
npm ci
npm start

# Terminal 2 — aplicativo
cd electron-app
npm ci
npm start
```

`electron-app/config.js` contém a URL do servidor. Para usar o servidor local, defina `SIGNALING_SERVER_URL` como `http://localhost:3000`. A configuração atual aponta para o servidor hospedado do projeto. As duas pessoas devem usar a mesma URL.

Crie uma sala, copie o código e envie para a outra pessoa. Ela usa **Entrar em uma sala**. A chamada começa apenas com microfone; a câmera pode ser ativada depois. Cada participante pode compartilhar sua tela na mesma sala, inclusive ao mesmo tempo.

## Controles

- **Microfone:** liga ou silencia sua voz.
- **Câmera:** ativa ou desativa o vídeo, sem precisar reentrar.
- **Tela:** escolhe uma tela ou janela; permite trocar a fonte ou parar a transmissão. A opção de áudio inclui o som da captura quando disponível.
- **Áudio da chamada:** slider visível no vídeo remoto, de 0% a 200%. Controla a mistura da voz e do áudio compartilhado. Acima de 100% aplica ganho real; sinais já muito altos podem distorcer.
- **Clique no vídeo:** destaca aquela tela; os controles de volume não mudam seu tamanho.
- **Tela cheia:** mantém a imagem sem espelhamento e o volume acessível.
- **Configurações:** escolhe microfone e saída, ajusta ganho de entrada/saída e processamento de voz. Escape fecha os painéis.

## Arquitetura

```text
signaling-server/
  server.js          Salas de até dois participantes; encaminha SDP/ICE e estado de mídia
  tests/             Testes locais com Socket.IO real
  package-lock.json  Dependências reproduzíveis

electron-app/
  main.js            Janela, permissões e captura nativa
  preload.js         Ponte explícita entre Electron e interface
  config.js          URL de sinalização e servidores ICE
  media.js           Associação de canais WebRTC e mistura de áudio
  renderer.js        Ciclo da chamada, dispositivos e controles
  index.html         Interface em português
  style.css          Tema, layouts e estados visuais
  tests/             Regressões no Chromium do Electron e duas interfaces reais
```

A conexão negocia três canais `sendrecv`: microfone, vídeo e áudio da tela. O primeiro participante cria os canais; o segundo reutiliza os canais da oferta antes de responder. Isso permite transmitir vídeo depois de entrar sem câmera, nos dois sentidos. A troca câmera/tela usa `replaceTrack`, sem nova sala.

O áudio remoto passa por fontes separadas de Web Audio, uma por track, conectadas a um ganho comum e a uma saída reproduzida pelo elemento de áudio. Assim, voz e som da tela são misturados, o ganho de 200% funciona e a seleção de dispositivo de saída permanece disponível.

Ao sair, a aplicação libera tracks, conexões, contextos de áudio, indicadores de fala e timers. Quem permanece na sala assume a iniciativa da próxima conexão. A tela ativa é reanexada quando a outra pessoa volta.

## Testes

```powershell
cd electron-app
npm test

cd ../signaling-server
npm test
```

Os testes do aplicativo usam o Chromium incluído no Electron, vídeo gerado por canvas e dispositivos de mídia simulados, sem acessar seu microfone/câmera reais ou o servidor hospedado. Verificam decodificação de vídeo nos dois sentidos, dois canais de áudio, ganho medido em 0/50/100/200%, compartilhamento simultâneo na interface de produção, câmera, parar/reiniciar tela, sincronização de volume, janela mínima e sair/reentrar. As capturas de conferência ficam em `electron-app/tests/artifacts/`, ignoradas pelo Git.

Os testes do servidor usam uma porta local aleatória e conferem limite de participantes, validação, encaminhamento restrito à sala, colisão com identificadores internos, mensagens inválidas, saída/reentrada e limpeza de salas.

Os testes simulados não substituem a conferência entre dois computadores de captura nativa de áudio, permissões do sistema, seleção física de saída e comportamento de redes/NAT diferentes.

## Gerar instalador

```powershell
cd electron-app
npm run dist:win
# No macOS:
npm run dist:mac
```

Os arquivos saem em `electron-app/dist/`. O módulo `electron-native-screenshare` pode precisar das ferramentas nativas de compilação da plataforma. Se ele não carregar em execução, o app continua disponível e tenta a captura de áudio do sistema. A disponibilidade desse áudio depende da plataforma, fonte e permissões.

## Servidor hospedado

Hospede `signaling-server/` em um serviço Node.js. Use `npm ci` para instalação e `npm start` para inicialização. O servidor lê `PORT` e opcionalmente `ALLOWED_ORIGIN`; `/health` expõe seu estado. Atualize a URL em `config.js` e distribua o mesmo aplicativo aos dois participantes.

As mudanças deste remaster também incluem validação no servidor. Atualize o servidor hospedado junto com os clientes; o novo evento `video-state` mantém a interface coerente ao desligar a câmera ou parar a tela.

## Rede e privacidade

O servidor de sinalização recebe metadados de conexão e códigos de sala, não áudio ou vídeo. A mídia é protegida pelo WebRTC e segue diretamente entre os participantes, ou por TURN quando configurado. Os códigos identificam salas, mas não são um sistema de contas/autenticação: compartilhe-os apenas com a pessoa convidada.

STUN pode ser insuficiente em redes com CGNAT, NAT simétrico ou firewall restritivo. Nesses casos, adicione um TURN válido a `ICE_SERVERS` em `config.js`, com suas credenciais. O projeto não fornece um TURN próprio.

No macOS, autorize câmera, microfone e gravação de tela nas configurações do sistema. O preload usa `socket.io-client` via Node, portanto o sandbox da janela está desativado; `contextIsolation` continua ligado, `nodeIntegration` desligado, navegação externa e novas janelas bloqueadas.
