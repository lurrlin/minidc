# Unme

Um espaço para conversar, ver e compartilhar. Só vocês dois.

O Unme é um aplicativo desktop para chamadas de voz, vídeo e compartilhamento de tela entre duas pessoas. Cada um pode transmitir sua tela ao mesmo tempo, na mesma sala.

**[Baixar o Unme para Windows →](https://github.com/lurrlin/minidc/releases)**

## Baixar e instalar

1. Abra a página de [Releases](https://github.com/lurrlin/minidc/releases) e escolha a versão mais recente.
2. Na seção **Assets**, baixe o instalador com extensão **`.exe`**.
3. Execute o instalador, siga as etapas e abra o **Unme**.
4. Peça à outra pessoa para instalar a mesma versão.

O instalador é para Windows de 64 bits e inclui o necessário para executar o aplicativo. Você precisa de conexão com a internet e de um microfone para conversar; a câmera é opcional.

## Começar uma chamada

1. Clique em **Criar sala**.
2. Copie o código da sala e envie para sua companhia.
3. A outra pessoa clica em **Entrar em uma sala**, cola o código e confirma.
4. A chamada conecta quando os dois estão na sala.

Cada sala recebe até duas pessoas. A câmera começa desligada e pode ser ativada durante a chamada. Compartilhe o código apenas com quem você quer convidar.

## Seu perfil

Na tela inicial, clique em **Editar nome e foto** para escolher como você aparece na chamada.

- Digite seu nome e escolha uma foto **JPG, PNG ou WebP**, de até **8 MB**.
- Clique em **Salvar perfil** para confirmar.
- Use **Remover foto** para voltar a mostrar suas iniciais.

O nome e a foto ficam salvos neste computador, mesmo depois de fechar o aplicativo. Você pode alterá-los novamente na tela inicial. Seu perfil é compartilhado diretamente com a outra pessoa quando a chamada conecta.

## Controles da chamada

| Controle | Como usar |
| --- | --- |
| **Microfone** | Ative ou silencie sua voz. |
| **Câmera** | Ligue ou desligue sua câmera. |
| **Tela** | Escolha uma tela ou janela para transmitir. Abra novamente para trocar a fonte ou parar o compartilhamento. |
| **Ouvir** | Silencie todo o áudio recebido. Clique novamente para restaurar os volumes anteriores. Seu microfone continua com o estado que você escolheu. |
| **Sair** | Encerre sua participação na chamada e volte à tela inicial. |
| **Configurações ⚙** | Escolha o microfone e a saída de áudio, ajuste o volume geral e as opções de tratamento de voz. |

### Compartilhar tela e áudio

Clique em **Tela**, escolha a fonte e marque **Incluir áudio da tela ou janela** se quiser transmitir o som junto. A disponibilidade dessa captura de áudio depende do sistema e da fonte escolhida.

Os dois participantes podem compartilhar suas telas simultaneamente. Ao parar a transmissão, o quadro volta à câmera ou ao avatar.

### Ajustar os volumes

Clique com o **botão direito do mouse (M2)** no quadro da outra pessoa para abrir dois controles independentes:

- **Voz da pessoa:** volume do microfone da sua companhia.
- **Áudio da transmissão:** volume do som da tela ou janela compartilhada.

Os dois vão de **0% a 200%**. O controle **Volume geral de saída**, nas configurações, ajusta o conjunto. O botão **Ouvir** silencia ambos e preserva seus ajustes para quando você voltar a ouvir.

### Destacar uma tela

Clique em um quadro para destacá-lo; clique novamente para voltar à disposição lado a lado. Os quadros funcionam da mesma maneira com vídeo, compartilhamento ou apenas o avatar.

Use **Tela cheia** para ampliar a tela da outra pessoa. Para sair, clique em **Sair da tela cheia** ou pressione **Escape**.

### Melhorar a voz

Nas configurações, você pode alternar **Redução de ruído**, **Cancelamento de eco** e **Ganho automático**. O aplicativo mostra o estado aplicado ao microfone: **Ativo**, **Desligado** ou **Indisponível**. Se uma alteração não puder ser aplicada, a configuração anterior é mantida.

## Se algo não funcionar

- **Não consigo ouvir:** confira o botão **Ouvir**, os volumes do menu M2 e a saída de áudio selecionada nas configurações.
- **A outra pessoa não me ouve:** confira o botão **Microfone**, o dispositivo selecionado e a permissão de acesso ao microfone no sistema.
- **A transmissão está sem som:** abra **Tela** novamente e confira **Incluir áudio da tela ou janela**.
- **A chamada não conecta:** verifique a internet dos dois e tente entrar novamente. Algumas redes podem impedir a conexão; experimentar outra rede ajuda a identificar o problema.

Para relatar um problema, abra uma [issue](https://github.com/lurrlin/minidc/issues) com a versão do Unme, seu sistema operacional e os passos para reproduzir. Envie capturas sem códigos de sala ou informações pessoais.

## Tecnologias e privacidade

- **Electron:** aplicativo desktop e integração com o sistema.
- **WebRTC:** conexão entre os participantes para voz, vídeo, tela e envio do perfil.
- **Web Audio API:** mistura e controle independente dos canais de áudio.
- **Node.js, Express e Socket.IO:** servidor que organiza as salas e troca as informações necessárias para estabelecer a conexão.
- **electron-native-screenshare:** captura nativa de áudio de janelas quando disponível.

O servidor de sinalização recebe códigos de sala e informações de conexão. O conteúdo de áudio, vídeo, tela e perfil é enviado entre os participantes, sem passar por esse servidor. O perfil permanece armazenado localmente no computador.

Para trabalhar no código, consulte o [guia de desenvolvimento](docs/DEVELOPMENT.md).
