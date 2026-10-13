# Desenvolvimento do Unme

Este guia é para quem trabalha no código. Para instalar e usar o aplicativo, consulte o [README](../README.md).

## Rodar o projeto

Use Node.js 22.12 ou mais recente e npm. São dois processos separados:

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
- **Botão direito (M2) na tela da outra pessoa:** abre os controles **Voz da pessoa** e **Áudio da transmissão**, cada um de 0% a 200%. Eles controlam canais separados e só aparecem nesse menu, inclusive em tela cheia. Ajustar um não muda o outro. Acima de 100% aplica ganho real; sinais já muito altos podem distorcer.
- **Ouvir (deafen):** o botão com fone na barra inferior silencia todo o áudio recebido, sem desligar o microfone. Clique de novo para ouvir com os volumes anteriores. Alterar os sliders enquanto silenciado não libera som. A opção funciona mesmo quando ativada antes de a outra pessoa entrar.
- **Clique no quadro:** destaca aquela tela; os controles de volume não mudam seu tamanho. Os quadros com avatar e sem compartilhamento usam o mesmo espaço e comportamento dos quadros com vídeo.
- **Tela cheia:** mantém a imagem sem espelhamento e o volume acessível.
- **Tela cheia:** o mesmo botão muda para **Sair da tela cheia**; Escape também sai.
- **Configurações:** escolhe microfone e saída e ajusta ganho de entrada/saída. Redução de ruído, cancelamento de eco e ganho automático reabrem a captura com as opções exigidas, confirmam o resultado no microfone e substituem a voz enviada sem renegociar a chamada. Os estados **Ativo**, **Desligado** e **Indisponível** refletem a captura. Se uma mudança falhar, o microfone anterior permanece ativo. Escape fecha os painéis.

## Perfil local e compartilhado

Na tela inicial, use **Editar nome e foto** abaixo das opções de sala. Escolha seu nome (até 40 caracteres) e uma foto JPG, PNG ou WebP (até 8 MB), ou remova a foto para usar suas iniciais. A imagem é recortada ao centro e reduzida para 256 × 256 antes de salvar. **Salvar perfil** confirma as escolhas; fechar o editor descarta as alterações ainda não salvas.

O perfil fica no armazenamento local do aplicativo neste computador, dentro dos dados de usuário do Electron. Ele continua disponível ao reabrir o app e pode ser alterado novamente na tela inicial. Nome e foto aparecem nos avatares e nomes da chamada.

Um canal de dados WebRTC envia o perfil diretamente à outra pessoa quando a conexão abre, inclusive após reentrar na sala. O servidor de sinalização não recebe o conteúdo do perfil. Ambos os participantes precisam usar a versão com suporte a perfis.

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
  microphone.js      Restrições verificadas de voz e criação de ganho
  profile.js         Validação, persistência, foto e canal P2P do perfil
  profile-ui.js      Editor de perfil da tela inicial
  renderer.js        Ciclo da chamada, dispositivos e controles
  index.html         Interface em português
  style.css          Tema, layouts e estados visuais
  tests/             Regressões no Chromium do Electron e duas interfaces reais
```

A conexão negocia três canais `sendrecv`: microfone, vídeo e áudio da tela. O primeiro participante cria os canais; o segundo reutiliza os canais da oferta antes de responder. Isso permite transmitir vídeo depois de entrar sem câmera, nos dois sentidos. A troca câmera/tela usa `replaceTrack`, sem nova sala.

O áudio remoto passa por fontes separadas de Web Audio, uma por track. A associação do transceiver identifica os canais de voz e transmissão, cada um com seu ganho próprio. Eles se somam antes do ganho geral de saída e do elemento de áudio. **Volume geral de saída**, nas configurações, afeta os dois canais. O deafen zera a saída geral e silencia o elemento de áudio, preservando os níveis individuais; reativá-lo restaura a reprodução. O indicador de fala acompanha apenas a voz, mesmo com o áudio recebido silenciado.

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

Os testes também conferem entrada/saída de tela cheia, persistência de nome/foto, compressão da foto e envio pelo canal de dados P2P. Para cada opção de processamento de voz, ligam e desligam a opção, verificam as configurações efetivas da captura e a preservação de mudo e da saída enviada. Uma falha simulada verifica a manutenção do microfone anterior. Os perfis de teste usam armazenamento temporário isolado dos dados reais do usuário.

As medições de áudio verificam a independência de voz/transmissão, ganho da transmissão, silêncio total com deafen e restauração dos volumes. A interface verifica os quadros vazios ocupando o mesmo espaço dos vídeos, o menu M2 oculto por padrão, o roteamento dos canais e o deafen sem mutar o microfone. As imagens de conferência incluem `audio-only.png` e `call.png`.

Os testes simulados não substituem a conferência entre dois computadores de captura nativa de áudio, eficácia acústica da redução de ruído/eco em microfones físicos, permissões do sistema, seleção física de saída e comportamento de redes/NAT diferentes.

### Desempenho e encerramento de chamadas

Em `electron-app/`, execute `npm run test:performance`. Além da revisão funcional, ele mantém duas transmissões simultâneas de canvas em 1280 × 720 a 30 fps por 30 segundos, no renderer de produção com renderização normal, e mede quadros decodificados, tempo de decodificação, tráfego, CPU e memória dos processos do Electron. O relatório fica em `tests/artifacts/performance.json`. CPU e memória incluem o ambiente de testes; essa medição local não representa captura física nem desempenho pela internet e não comprova ausência de vazamentos em uso prolongado. O modo offscreen é usado nos testes funcionais comuns, mas seu custo adicional de memória e CPU não representa o aplicativo distribuído.

Os testes funcionais repetem a saída/reentrada e verificam se microfones antigos foram encerrados, se os elementos de áudio foram liberados e se cada participante mantém apenas um mixer e uma conexão ativos. O áudio recebido pelo WebRTC também é medido no mixer, incluindo silêncio e restauração pelo botão Ouvir.

### Marca e ícone

Abra `docs/icon.html` no navegador para visualizar e baixar o símbolo `ü` em PNG transparente de 1024 × 1024 pixels. A página funciona localmente sem dependências ou hospedagem. Os dois pontos representam os participantes. O PNG e o ICO usados no app e no instalador ficam em `electron-app/assets/`.

Para exportar novamente o PNG a partir da página, execute `npx electron tests/export-icon.cjs` em `electron-app/`. Ao alterar a marca, atualize também o ICO com os tamanhos 16, 24, 32, 48, 64, 128 e 256 pixels antes de gerar o instalador.

### Dependências e auditoria de segurança

O aplicativo usa Electron 44 e electron-builder 26. O runtime requer Windows de 64 bits; para macOS, a versão mínima é 13. Execute `npm audit` tanto em `electron-app/` quanto em `signaling-server/` para conferir os avisos conhecidos das dependências instaladas. O Electron está em `devDependencies`, mas faz parte do app distribuído: não use apenas `npm audit --omit=dev` para avaliar a segurança do instalador.

O override de `global-agent` para 4.1.3 remove a cadeia de logging vulnerável mantida pelo downloader do electron-builder. A API de proxy usada pelo downloader é conferida com `npm run test:build`, por meio de um proxy HTTP local. Reavalie o override quando o builder atualizar essa dependência. A auditoria sem alertas não substitui os testes funcionais nem uma revisão completa de segurança.

Depois de gerar o instalador Windows, execute `npm run test:packaged`. Esse teste carrega o código de produção do `app.asar` com o Electron atualizado, confere o preload, o ícone, a versão e a disponibilidade do módulo nativo de áudio, além da ausência de arquivos de testes e `.env` no pacote. Usa um perfil temporário; não inicia captura de áudio nem instala o executável no sistema.

No servidor, `npm run dev` usa `node --watch server.js`, sem nodemon. A remoção do nodemon elimina a cadeia de padrões vulnerável usada apenas no desenvolvimento. `npm start` continua executando o servidor normalmente.

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
