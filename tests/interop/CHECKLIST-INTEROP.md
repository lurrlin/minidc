# Roteiro Manual de Testes 2×2 e Interoperabilidade — Unme

Este roteiro valida em hardware real todas as combinações entre **Desktop (Electron)** e **Android (Java)** antes de promover mudanças para a branch `master` e publicar no Render.

---

## 0. Pré-requisitos Automatizados (executar antes dos testes físicos)

```powershell
# 1. Signaling Server
npm --prefix signaling-server test

# 2. Desktop (contratos, regressao WebRTC/mixer, UI e build)
npm --prefix electron-app run test:contract
npm --prefix electron-app test
npm --prefix electron-app run test:build

# 3. Android (contratos + regras de negocio)
cd android ; .\gradlew.bat testDebugUnitTest assembleDebug

# 4. Interoperabilidade de sinalizacao v1.0 / v1.1
node --test tests/interop/signaling-interop.test.cjs
```

---

## 1. Matriz de Hardware Recomendada

- **Par Desktop ↔ Android:** 1 PC Windows (Electron rodando via `npm --prefix electron-app start`) + 1 smartphone Android com o APK `app-debug.apk` ou `app-release.apk`.
- **Par Android ↔ Android:** 2 smartphones Android (ou 1 físico + 1 secundário).
- **Par Desktop ↔ Desktop:** validado automaticamente por `npm --prefix electron-app test` e manualmente em 2 instâncias se desejar checar áudio físico.

---

## 2. Cenários Obrigatórios (Matriz 2×2)

| # | Cenário | Desk ↔ Desk | Desk → And | And → Desk | And ↔ And | Critério de Aprovação |
| :-: | :--- | :---: | :---: | :---: | :---: | :--- |
| **1** | Criar sala, entrar e bloquear 3º participante | [ ] | [ ] | [ ] | [ ] | Sala gera código de 16 chars; 2º entra com áudio ativo; 3º recebe *"Essa sala já tem 2 pessoas."* |
| **2** | Cold start do servidor no Render (`check-room` até 30s) | [ ] | [ ] | [ ] | [ ] | Aviso *"Acordando servidor... aguarde um instante."* aparece após 6s sem falhar prematuramente |
| **3** | DataChannel `"profile"` (nome + foto) | [ ] | [ ] | [ ] | [ ] | Avatar e nome exibidos corretamente nos dois lados; editar nome/foto durante chamada atualiza o par em tempo real |
| **4** | Voz, Mute e Ganho de entrada (`0%` / `100%` / `200%`) | [ ] | [ ] | [ ] | [ ] | Mudo silencia apenas a voz; sliders ajustam ganho sem distorção excessiva nem quedas |
| **5** | Volumes remotos independentes (`Voz` vs `Transmissão`) + `Deafen` | [ ] | [ ] | [ ] | [ ] | Slider `Voz da pessoa` não altera o áudio da tela e vice-versa (**`T1` e `T2`**); `Deafen` silencia ambos sem mutar microfone local |
| **6** | Indicador visual de fala (local e remoto) | [ ] | [ ] | [ ] | [ ] | Borda verde acende apenas quando há voz ativa, inclusive com `Deafen` ligado no receptor |
| **7** | Câmera ligar/desligar + proporção vertical↔horizontal | [ ] | [ ] | [ ] | [ ] | Câmera retrato do Android não sofre corte severo no Desktop (`.video-portrait` em `contain`) |
| **8** | Compartilhamento bidirecional de tela + áudio da tela (`"screen-audio"`) | [ ] | [ ] | [ ] | [ ] | Ambos compartilham tela simultaneamente; áudio de mídia do Android chega no canal `'screen'` do Desktop e vice-versa; mutar mic no Android não corta áudio da tela |
| **9** | **Rotação física do Android durante compartilhamento de tela** | N/A | [ ] | **Crítico [ ]** | **Crítico [ ]** | Ao girar o celular (retrato ↔ paisagem), `changeCaptureFormat` atualiza resolução/proporção no Desktop sem letterbox duplo (**`T3`**) |
| **10** | **Recepção de tela Desktop 16:9 / Ultrawide no Android** | [ ] | **Crítico [ ]** | N/A | N/A | `SurfaceViewRenderer` centralizado com fundo `#0C0E15`; menus/controles não ficam escondidos atrás do vídeo (**`T5`**) |
| **11** | Troca de rede / queda breve (`Wi-Fi ↔ 4G`) nos dois papéis | [ ] | [ ] | [ ] | [ ] | Seja no Iniciador ou no Convidado (`renegotiate + iceRestart`), a chamada recupera sem exigir sair da sala (**`C1/C2`**) |
| **12** | Interrupções no Android (Bluetooth hot-plug, chamada telefônica / AudioFocus, sair e reentrar 3×) | N/A | [ ] | [ ] | [ ] | Fone Bluetooth assume o áudio; perda temporária de foco silencia voz e `"screen-audio"`; 3 ciclos de sair/reentrar sem travamentos ou vazamentos |

---

## 3. Registro de Execução

- **Data:** `____/____/______`
- **Branch / Commit testado:** `__________________`
- **Servidor utilizado:** `[ ] Render produção` / `[ ] Local (porta 3000)`
- **Dispositivos Android (modelo / versão OS):** `________________________________________`
- **Observações / Ajustes para Etapa 6:** `_______________________________________________`
