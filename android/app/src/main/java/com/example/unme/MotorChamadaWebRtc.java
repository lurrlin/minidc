package com.example.unme;

import android.content.Context;
import android.content.Intent;
import android.media.AudioDeviceInfo;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.AudioTrackSink;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.RtpTransceiver;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class MotorChamadaWebRtc implements ClienteSinalizacao.OuvinteSinalizacao {

    public interface OuvinteInterfaceChamada {
        void aoMudarStatusLobby(String mensagem, String tom);

        void aoMudarEstadoEntrando(boolean entrando);

        void aoIrParaTelaChamada(String nomeSala);

        void aoIrParaTelaLobby();

        void aoMudarEstadoConexao(String estado);

        void aoAtualizarTextoStatusChamada(String texto);

        void aoAtualizarTextoVazioRemoto(String texto);

        void aoMostrarToast(String mensagem, String tom, int duracaoMs);

        void aoAtualizarPerfis(PerfilUsuario perfilRemoto, boolean compartilhandoTelaLocal, boolean compartilhandoTelaRemota);

        void aoAtualizarEstadoVideoLocal(boolean possuiVideo, boolean compartilhandoTela, boolean cameraAtiva);

        void aoAtualizarEstadoVideoRemoto(boolean possuiVideo, boolean compartilhandoTela);

        void aoMudarFalaLocal(boolean falando);

        void aoMudarFalaRemota(boolean falando);

        PerfilUsuario obterPerfilLocalAtual();
    }

    private final Context contextoApp;
    private final Handler manipuladorPrincipal = new Handler(Looper.getMainLooper());
    private final OuvinteInterfaceChamada ouvinteUi;
    private final ClienteSinalizacao clienteSinalizacao;
    private final MisturadorAudio misturadorAudio;
    private final GerenciadorDispositivosAudio gerenciadorDispositivos;
    private final CapturadorAudioSistema capturadorAudioSistema;

    private EglBase contextoEgl;
    private PeerConnectionFactory fabricaConexao;
    private JavaAudioDeviceModule moduloAudio;

    private SurfaceViewRenderer renderizadorLocal;
    private SurfaceViewRenderer renderizadorRemoto;

    // Mídia local
    private AudioSource fonteMicrofoneLocal;
    private AudioTrack faixaMicrofoneLocal;
    private boolean microfoneHabilitado = true;
    private boolean atualizandoMicrofone = false;

    private VideoCapturer capturadorCamera;
    private SurfaceTextureHelper auxiliarTexturaCamera;
    private VideoSource fonteCameraLocal;
    private VideoTrack faixaCameraLocal;
    private boolean cameraAtiva = false;
    private boolean cameraFrontal = true;

    private ScreenCapturerAndroid capturadorTela;
    private SurfaceTextureHelper auxiliarTexturaTela;
    private VideoSource fonteTelaLocal;
    private VideoTrack faixaTelaLocal;
    private boolean compartilhandoTela = false;
    private boolean compartilhandoAudioTela = false;
    private boolean ocupadoCompartilhando = false;

    // Opções de tratamento de voz
    private boolean reducaoRuido = true;
    private boolean cancelamentoEco = true;
    private boolean ganhoAutomatico = true;

    // Indicadores de fala
    private volatile int ultimaTaxaGravada = 48000;
    private volatile int ultimosCanaisGravados = 1;
    private double nivelFalaLocalSuavizado = 0.0;
    private boolean falandoLocal = false;
    private double nivelFalaRemotaSuavizado = 0.0;
    private boolean falandoRemoto = false;
    private Runnable limpadorSilencioLocal;
    private Runnable limpadorSilencioRemoto;

    // Estado da sala e WebRTC
    private PeerConnection conexaoPar;
    private RtpTransceiver transceptorMicrofone;
    private RtpTransceiver transceptorVideo;
    private RtpTransceiver transceptorAudioTela;
    private DataChannel canalPerfil;

    private VideoTrack faixaVideoRemotaAtual;
    private AudioTrack faixaVozRemota;
    private AudioTrack faixaTelaRemota;
    private AudioTrackSink piaFalaRemota;

    private String salaAtual = null;
    private boolean entrandoNaSala = false;
    private boolean iniciador = false;
    private boolean jaEntrouAlgumaVez = false;
    private boolean parceiroEntrouPendente = false;
    private boolean parceiroRemotoPronto = false;
    private Boolean videoRemotoHabilitado = null;
    private boolean telaRemotaCompartilhada = false;
    private PerfilUsuario perfilRemoto = PerfilUsuario.vazio();

    private final Queue<JSONObject> sinaisPendentes = new LinkedList<>();
    private final List<IceCandidate> candidatosPendentes = new ArrayList<>();
    private boolean processandoSinais = false;
    private boolean renegociacaoEnfileirada = false;
    private boolean renegociacaoEmAndamento = false;

    private Runnable temporizadorReconexao;
    private Runnable temporizadorFalhaIce;

    public MotorChamadaWebRtc(Context contexto, OuvinteInterfaceChamada ouvinteUi) {
        this.contextoApp = contexto.getApplicationContext();
        this.ouvinteUi = ouvinteUi;
        this.clienteSinalizacao = new ClienteSinalizacao();
        this.clienteSinalizacao.definirOuvinte(this);
        this.misturadorAudio = new MisturadorAudio();
        this.gerenciadorDispositivos = new GerenciadorDispositivosAudio(contextoApp);
        this.capturadorAudioSistema = new CapturadorAudioSistema();
        inicializarFabricaWebRtc();
    }

    private void inicializarFabricaWebRtc() {
        contextoEgl = EglBase.create();
        PeerConnectionFactory.InitializationOptions opcoesInicializacao =
                PeerConnectionFactory.InitializationOptions.builder(contextoApp)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(opcoesInicializacao);

        moduloAudio = JavaAudioDeviceModule.builder(contextoApp)
                .setUseHardwareNoiseSuppressor(JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported())
                .setUseHardwareAcousticEchoCanceler(JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported())
                .setAudioRecordDataCallback((formatoAudio, quantidadeCanais, taxaAmostragem, bufferAudio) -> {
                    ultimaTaxaGravada = taxaAmostragem;
                    ultimosCanaisGravados = quantidadeCanais;
                    int bytesRestantes = bufferAudio.remaining();
                    if (!microfoneHabilitado) {
                        processarAmostraFalaLocal(0.0);
                        MisturadorAudio.aplicarGanhoPcm16(bufferAudio, bytesRestantes, 0.0);
                    } else {
                        double nivelBruto = MisturadorAudio.calcularNivelAudio(bufferAudio, bytesRestantes);
                        processarAmostraFalaLocal(nivelBruto);
                        MisturadorAudio.aplicarGanhoPcm16(
                                bufferAudio,
                                bytesRestantes,
                                misturadorAudio.obterGanhoEntrada()
                        );
                    }
                    if (compartilhandoAudioTela && capturadorAudioSistema.estaCapturando()) {
                        capturadorAudioSistema.misturarNoBufferGravado(bufferAudio, bytesRestantes);
                    }
                })
                .createAudioDeviceModule();

        DefaultVideoEncoderFactory fabricaCodificador = new DefaultVideoEncoderFactory(
                contextoEgl.getEglBaseContext(),
                true,
                true
        );
        DefaultVideoDecoderFactory fabricaDecodificador = new DefaultVideoDecoderFactory(
                contextoEgl.getEglBaseContext()
        );

        fabricaConexao = PeerConnectionFactory.builder()
                .setAudioDeviceModule(moduloAudio)
                .setVideoEncoderFactory(fabricaCodificador)
                .setVideoDecoderFactory(fabricaDecodificador)
                .createPeerConnectionFactory();
    }

    public EglBase.Context obterContextoEgl() {
        return contextoEgl.getEglBaseContext();
    }

    public MisturadorAudio obterMisturadorAudio() {
        return misturadorAudio;
    }

    public GerenciadorDispositivosAudio obterGerenciadorDispositivos() {
        return gerenciadorDispositivos;
    }

    public void vincularRenderizadores(SurfaceViewRenderer local, SurfaceViewRenderer remoto) {
        this.renderizadorLocal = local;
        this.renderizadorRemoto = remoto;
        if (renderizadorLocal != null) {
            renderizadorLocal.init(obterContextoEgl(), null);
            renderizadorLocal.setEnableHardwareScaler(true);
            renderizadorLocal.setMirror(true);
            renderizadorLocal.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        }
        if (renderizadorRemoto != null) {
            renderizadorRemoto.init(obterContextoEgl(), null);
            renderizadorRemoto.setEnableHardwareScaler(true);
            renderizadorRemoto.setMirror(false);
            renderizadorRemoto.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        }
    }

    // ---------------------------------------------------------------------
    // Lobby e Entrada na Sala
    // ---------------------------------------------------------------------

    public void iniciarEntradaSala(String nomeSalaBruto, boolean verificarExistencia) {
        String nomeSala = GerenciadorSala.normalizarNomeSala(nomeSalaBruto);
        if (nomeSala.isEmpty() || entrandoNaSala) {
            return;
        }
        definirEntrando(true);
        ouvinteUi.aoMudarStatusLobby("Conectando ao servidor...", "info");

        salaAtual = nomeSala;
        clienteSinalizacao.conectar(ConfiguracaoApp.URL_SERVIDOR_SINALIZACAO);

        if (verificarExistencia) {
            clienteSinalizacao.verificarSala(nomeSala, resultado -> {
                if (resultado.erro || !resultado.existe) {
                    clienteSinalizacao.desconectar();
                    salaAtual = null;
                    definirEntrando(false);
                    ouvinteUi.aoMudarStatusLobby(
                            resultado.erro
                                    ? "O servidor não respondeu. Tente novamente."
                                    : "Essa sala não existe.",
                            "error"
                    );
                    return;
                }
                if (!resultado.disponivel) {
                    clienteSinalizacao.desconectar();
                    salaAtual = null;
                    definirEntrando(false);
                    ouvinteUi.aoMudarStatusLobby("Essa sala ja esta cheia.", "error");
                    return;
                }
                clienteSinalizacao.entrarNaSala(nomeSala);
            });
        } else {
            clienteSinalizacao.entrarNaSala(nomeSala);
        }
    }

    private void definirEntrando(boolean valor) {
        entrandoNaSala = valor;
        ouvinteUi.aoMudarEstadoEntrando(valor);
    }

    // ---------------------------------------------------------------------
    // Eventos do Servidor de Sinalização (Socket.IO)
    // ---------------------------------------------------------------------

    @Override
    public void aoConectar() {
        if (salaAtual != null && jaEntrouAlgumaVez) {
            clienteSinalizacao.entrarNaSala(salaAtual);
        }
    }

    @Override
    public void aoDesconectar() {
        if (jaEntrouAlgumaVez) {
            ouvinteUi.aoMudarEstadoConexao("lost");
        }
    }

    @Override
    public void aoErroConexao(String mensagemErro) {
        ouvinteUi.aoMudarStatusLobby("Nao foi possivel conectar ao servidor.", "error");
        if (!jaEntrouAlgumaVez) {
            clienteSinalizacao.cancelarVerificacaoPendenteComErro();
            clienteSinalizacao.desconectar();
            salaAtual = null;
            definirEntrando(false);
        }
        String detalhe = (mensagemErro != null && !mensagemErro.isEmpty())
                ? mensagemErro
                : "verifique sua internet.";
        ouvinteUi.aoMostrarToast("Erro de conexao: " + detalhe, "error", 6000);
    }

    @Override
    public void aoErroEntrarSala(String mensagem) {
        ouvinteUi.aoMudarStatusLobby(mensagem, "error");
        clienteSinalizacao.desconectar();
        salaAtual = null;
        definirEntrando(false);
    }

    @Override
    public void aoSalaCheia(String nomeSala) {
        ouvinteUi.aoMudarStatusLobby("Essa sala ja tem 2 pessoas.", "error");
        definirEntrando(false);
        clienteSinalizacao.desconectar();
    }

    @Override
    public void aoEntrarNaSala(String nomeSala, boolean primeiroParticipante) {
        if (jaEntrouAlgumaVez && conexaoPar != null) {
            fecharConexaoParAtual();
        }
        salaAtual = nomeSala;
        iniciador = primeiroParticipante;
        jaEntrouAlgumaVez = true;

        if (conexaoPar == null) {
            ouvinteUi.aoIrParaTelaChamada(nomeSala);
            ServicoChamada.iniciarOuAtualizar(contextoApp, nomeSala, compartilhandoTela, cameraAtiva, null);
            try {
                if (faixaMicrofoneLocal == null) {
                    iniciarMidiaLocal();
                }
            } catch (Exception erroMidia) {
                ouvinteUi.aoMostrarToast(
                        "Nao foi possivel acessar camera/microfone. Verifique as permissoes.",
                        "error",
                        6000
                );
                sairDaChamada();
                return;
            }

            criarConexaoPar();
            drenarSinais();

            if (parceiroEntrouPendente && iniciador) {
                parceiroEntrouPendente = false;
                criarOfertaSePronto();
            }

            enviarSinalSimples("ready");
        }

        ouvinteUi.aoMudarEstadoConexao("connecting");
        ouvinteUi.aoMudarStatusLobby(
                primeiroParticipante ? "Sala criada. Esperando a outra pessoa entrar..." : "Entrando...",
                "ok"
        );
    }

    @Override
    public void aoParceiroEntrar() {
        parceiroEntrouPendente = true;
        if (conexaoPar != null && iniciador) {
            parceiroEntrouPendente = false;
            criarOfertaSePronto();
        }
    }

    @Override
    public void aoParceiroSair() {
        ouvinteUi.aoMostrarToast("A outra pessoa saiu da sala.", "info", 4000);
        limparMidiaRemota();
        ouvinteUi.aoAtualizarTextoVazioRemoto("Esperando sua companhia voltar…");
        iniciador = true;
        parceiroRemotoPronto = false;
        perfilRemoto = PerfilUsuario.vazio();
        canalPerfil = null;
        videoRemotoHabilitado = null;
        telaRemotaCompartilhada = false;
        sinaisPendentes.clear();
        ouvinteUi.aoAtualizarEstadoVideoRemoto(false, false);
        ouvinteUi.aoAtualizarPerfis(perfilRemoto, compartilhandoTela, false);
        ouvinteUi.aoMudarEstadoConexao("connecting");
        ouvinteUi.aoAtualizarTextoStatusChamada("esperando a outra pessoa voltar...");

        if (conexaoPar != null) {
            fecharConexaoParAtual();
            criarConexaoPar();
        }
    }

    @Override
    public void aoReceberSinal(JSONObject dadosSinal) {
        sinaisPendentes.offer(dadosSinal);
        drenarSinais();
    }

    // ---------------------------------------------------------------------
    // Mídia Local (Microfone, Ganho, Tratamento de Voz, Câmera, Tela)
    // ---------------------------------------------------------------------

    private void iniciarMidiaLocal() {
        gerenciadorDispositivos.iniciarModoComunicacao();
        List<GerenciadorDispositivosAudio.OpcaoDispositivo> microfones =
                gerenciadorDispositivos.listarMicrofones();
        String idMic = gerenciadorDispositivos.obterIdMicrofoneSelecionado();
        GerenciadorDispositivosAudio.OpcaoDispositivo escolhido =
                gerenciadorDispositivos.obterMicrofonePorId(idMic);
        if (escolhido != null && moduloAudio != null && escolhido.infoNativa != null) {
            try {
                moduloAudio.setPreferredInputDevice(escolhido.infoNativa);
            } catch (Exception erroDispositivo) {
                moduloAudio.setPreferredInputDevice(null);
                gerenciadorDispositivos.definirIdMicrofoneSelecionado(
                        microfones.isEmpty() ? "" : microfones.get(0).id
                );
                ouvinteUi.aoMostrarToast(
                        "Microfone selecionado indisponível. Usando o dispositivo padrão.",
                        "info",
                        7000
                );
            }
        }

        if (moduloAudio != null && JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported()) {
            moduloAudio.setNoiseSuppressorEnabled(reducaoRuido);
        }

        MediaConstraints restricoesAudio = construirRestricoesAudio(
                reducaoRuido,
                cancelamentoEco,
                ganhoAutomatico
        );
        fonteMicrofoneLocal = fabricaConexao.createAudioSource(restricoesAudio);
        faixaMicrofoneLocal = fabricaConexao.createAudioTrack("microfone_local", fonteMicrofoneLocal);
        microfoneHabilitado = true;
        faixaMicrofoneLocal.setEnabled(true);
        if (moduloAudio != null) {
            moduloAudio.setMicrophoneMute(false);
            moduloAudio.setSpeakerMute(misturadorAudio.estaSilenciadoTotal());
        }
        cameraAtiva = false;
    }

    private static MediaConstraints construirRestricoesAudio(
            boolean usarReducaoRuido,
            boolean usarCancelamentoEco,
            boolean usarGanhoAutomatico
    ) {
        MediaConstraints restricoes = new MediaConstraints();
        String strRuido = String.valueOf(usarReducaoRuido);
        String strEco = String.valueOf(usarCancelamentoEco);
        String strGanho = String.valueOf(usarGanhoAutomatico);

        restricoes.mandatory.add(new MediaConstraints.KeyValuePair("googNoiseSuppression", strRuido));
        restricoes.mandatory.add(new MediaConstraints.KeyValuePair("googNoiseSuppression2", strRuido));
        restricoes.mandatory.add(new MediaConstraints.KeyValuePair("googEchoCancellation", strEco));
        restricoes.mandatory.add(new MediaConstraints.KeyValuePair("googEchoCancellation2", strEco));
        restricoes.mandatory.add(new MediaConstraints.KeyValuePair("googAutoGainControl", strGanho));
        restricoes.mandatory.add(new MediaConstraints.KeyValuePair("googAutoGainControl2", strGanho));
        restricoes.mandatory.add(new MediaConstraints.KeyValuePair("googHighpassFilter", "true"));
        return restricoes;
    }

    public boolean alternarMudoMicrofone() {
        if (faixaMicrofoneLocal == null) {
            return false;
        }
        microfoneHabilitado = !microfoneHabilitado;
        faixaMicrofoneLocal.setEnabled(microfoneHabilitado);
        if (moduloAudio != null) {
            moduloAudio.setMicrophoneMute(!microfoneHabilitado);
        }
        if (!microfoneHabilitado) {
            definirFalandoLocal(false);
        }
        return !microfoneHabilitado; // true = mutado (aria-pressed='true')
    }

    public boolean estaMicrofoneMutado() {
        return !microfoneHabilitado;
    }

    public boolean alternarSilenciarAudioRecebido() {
        boolean novoEstado = !misturadorAudio.estaSilenciadoTotal();
        misturadorAudio.definirSilenciadoTotal(novoEstado);
        aplicarVolumesRemotos();
        return novoEstado;
    }

    public boolean estaAudioRecebidoSilenciado() {
        return misturadorAudio.estaSilenciadoTotal();
    }

    public void definirVolumeVozRemota(int percentual) {
        misturadorAudio.definirVolumeCanal(MisturadorAudio.CANAL_VOZ, percentual);
        aplicarVolumesRemotos();
    }

    public void definirVolumeTransmissaoRemota(int percentual) {
        misturadorAudio.definirVolumeCanal(MisturadorAudio.CANAL_TELA, percentual);
        aplicarVolumesRemotos();
    }

    public void definirVolumeGeralSaida(int percentual) {
        misturadorAudio.definirVolumeGeral(percentual);
        aplicarVolumesRemotos();
    }

    public void definirVolumeEntradaMicrofone(int percentual) {
        misturadorAudio.definirVolumeEntrada(percentual);
    }

    public void aplicarVolumesRemotos() {
        boolean silenciado = misturadorAudio.estaSilenciadoTotal();
        if (moduloAudio != null) {
            moduloAudio.setSpeakerMute(silenciado);
        }
        if (faixaVozRemota != null) {
            try {
                faixaVozRemota.setVolume(misturadorAudio.obterGanhoEfetivoCanal(MisturadorAudio.CANAL_VOZ));
            } catch (Exception ignorado) {
            }
        }
        if (faixaTelaRemota != null) {
            try {
                faixaTelaRemota.setVolume(misturadorAudio.obterGanhoEfetivoCanal(MisturadorAudio.CANAL_TELA));
            } catch (Exception ignorado) {
            }
        }
    }

    public boolean trocarMicrofone(String idDispositivo) {
        if (faixaMicrofoneLocal == null || atualizandoMicrofone) {
            return false;
        }
        GerenciadorDispositivosAudio.OpcaoDispositivo opcao =
                gerenciadorDispositivos.obterMicrofonePorId(idDispositivo);
        if (opcao == null) {
            return false;
        }
        boolean sucesso = substituirPipelineMicrofone(
                opcao.infoNativa,
                reducaoRuido,
                cancelamentoEco,
                ganhoAutomatico
        );
        if (sucesso) {
            gerenciadorDispositivos.definirIdMicrofoneSelecionado(opcao.id);
            ouvinteUi.aoMostrarToast("Microfone alterado.", "info", 2500);
        } else {
            ouvinteUi.aoMostrarToast(
                    "Não foi possível trocar o microfone. O anterior continua ativo.",
                    "error",
                    4000
            );
        }
        return sucesso;
    }

    public boolean aplicarOpcoesProcessamentoAudio(
            boolean novaReducaoRuido,
            boolean novoCancelamentoEco,
            boolean novoGanhoAutomatico
    ) {
        if (faixaMicrofoneLocal == null || atualizandoMicrofone) {
            return false;
        }
        GerenciadorDispositivosAudio.OpcaoDispositivo atual =
                gerenciadorDispositivos.obterMicrofonePorId(
                        gerenciadorDispositivos.obterIdMicrofoneSelecionado()
                );
        AudioDeviceInfo infoAtual = atual != null ? atual.infoNativa : null;
        boolean sucesso = substituirPipelineMicrofone(
                infoAtual,
                novaReducaoRuido,
                novoCancelamentoEco,
                novoGanhoAutomatico
        );
        if (sucesso) {
            this.reducaoRuido = novaReducaoRuido;
            this.cancelamentoEco = novoCancelamentoEco;
            this.ganhoAutomatico = novoGanhoAutomatico;
        }
        return sucesso;
    }

    private boolean substituirPipelineMicrofone(
            AudioDeviceInfo dispositivoPreferido,
            boolean usarReducaoRuido,
            boolean usarCancelamentoEco,
            boolean usarGanhoAutomatico
    ) {
        if (fabricaConexao == null || atualizandoMicrofone) {
            return false;
        }
        atualizandoMicrofone = true;
        AudioSource novaFonte = null;
        AudioTrack novaFaixa = null;
        try {
            if (moduloAudio != null) {
                moduloAudio.setPreferredInputDevice(dispositivoPreferido);
                if (JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported()) {
                    moduloAudio.setNoiseSuppressorEnabled(usarReducaoRuido);
                }
            }
            MediaConstraints restricoes = construirRestricoesAudio(
                    usarReducaoRuido,
                    usarCancelamentoEco,
                    usarGanhoAutomatico
            );
            novaFonte = fabricaConexao.createAudioSource(restricoes);
            novaFaixa = fabricaConexao.createAudioTrack(
                    "microfone_local_" + System.currentTimeMillis(),
                    novaFonte
            );
            novaFaixa.setEnabled(microfoneHabilitado);

            if (transceptorMicrofone != null && conexaoPar != null
                    && conexaoPar.signalingState() != PeerConnection.SignalingState.CLOSED) {
                boolean substituido = transceptorMicrofone.getSender().setTrack(novaFaixa, false);
                if (!substituido) {
                    throw new IllegalStateException("Falha ao substituir faixa de áudio no RtpSender.");
                }
            }

            AudioTrack faixaAntiga = faixaMicrofoneLocal;
            AudioSource fonteAntiga = fonteMicrofoneLocal;
            faixaMicrofoneLocal = novaFaixa;
            fonteMicrofoneLocal = novaFonte;
            novaFaixa = null;
            novaFonte = null;

            if (faixaAntiga != null) {
                faixaAntiga.dispose();
            }
            if (fonteAntiga != null) {
                fonteAntiga.dispose();
            }
            return true;
        } catch (Exception erro) {
            if (novaFaixa != null) {
                novaFaixa.dispose();
            }
            if (novaFonte != null) {
                novaFonte.dispose();
            }
            return false;
        } finally {
            atualizandoMicrofone = false;
        }
    }

    public boolean obterReducaoRuido() {
        return reducaoRuido;
    }

    public boolean obterCancelamentoEco() {
        return cancelamentoEco;
    }

    public boolean obterGanhoAutomatico() {
        return ganhoAutomatico;
    }

    public boolean opcaoProcessamentoDisponivel(String chave) {
        if (faixaMicrofoneLocal == null) {
            return true;
        }
        // O módulo APM do WebRTC suporta as três opções em todos os aparelhos Android;
        // verificamos também a disponibilidade dos efeitos nativos do sistema.
        if ("noiseSuppression".equals(chave)) {
            return NoiseSuppressor.isAvailable() || fabricaConexao != null;
        }
        if ("echoCancellation".equals(chave)) {
            return AcousticEchoCanceler.isAvailable() || fabricaConexao != null;
        }
        if ("autoGainControl".equals(chave)) {
            return AutomaticGainControl.isAvailable() || fabricaConexao != null;
        }
        return false;
    }

    // ---------------------------------------------------------------------
    // Câmera
    // ---------------------------------------------------------------------

    public void alternarCamera() {
        if (faixaMicrofoneLocal == null || transceptorVideo == null) {
            return;
        }

        if (faixaCameraLocal != null && capturadorCamera != null) {
            cameraAtiva = !cameraAtiva;
            faixaCameraLocal.setEnabled(cameraAtiva);
            try {
                if (cameraAtiva) {
                    capturadorCamera.startCapture(1280, 720, 30);
                } else {
                    capturadorCamera.stopCapture();
                }
            } catch (Exception ignorado) {
            }

            if (!compartilhandoTela) {
                transceptorVideo.getSender().setTrack(cameraAtiva ? faixaCameraLocal : null, false);
                atualizarRenderizadorLocal();
            }
            ServicoChamada.iniciarOuAtualizar(contextoApp, salaAtual, compartilhandoTela, cameraAtiva, null);
            boolean temVideo = compartilhandoTela || cameraAtiva;
            ouvinteUi.aoAtualizarEstadoVideoLocal(temVideo, compartilhandoTela, cameraAtiva);
            enviarSinalEstadoVideo(temVideo);
            return;
        }

        try {
            CameraEnumerator enumerador = Camera2Enumerator.isSupported(contextoApp)
                    ? new Camera2Enumerator(contextoApp)
                    : new Camera1Enumerator(true);

            String nomeCameraEscolhida = null;
            cameraFrontal = true;
            for (String nome : enumerador.getDeviceNames()) {
                if (enumerador.isFrontFacing(nome)) {
                    nomeCameraEscolhida = nome;
                    cameraFrontal = true;
                    break;
                }
            }
            if (nomeCameraEscolhida == null) {
                for (String nome : enumerador.getDeviceNames()) {
                    nomeCameraEscolhida = nome;
                    cameraFrontal = enumerador.isFrontFacing(nome);
                    break;
                }
            }
            if (nomeCameraEscolhida == null) {
                throw new IllegalStateException("Nenhuma câmera encontrada.");
            }

            CameraVideoCapturer capturador = enumerador.createCapturer(nomeCameraEscolhida, null);
            if (capturador == null) {
                throw new IllegalStateException("Falha ao abrir câmera.");
            }

            auxiliarTexturaCamera = SurfaceTextureHelper.create("ThreadCameraUnme", obterContextoEgl());
            fonteCameraLocal = fabricaConexao.createVideoSource(false);
            capturador.initialize(auxiliarTexturaCamera, contextoApp, fonteCameraLocal.getCapturerObserver());
            capturador.startCapture(1280, 720, 30);

            capturadorCamera = capturador;
            faixaCameraLocal = fabricaConexao.createVideoTrack("camera_local", fonteCameraLocal);
            faixaCameraLocal.setEnabled(true);
            cameraAtiva = true;

            if (!compartilhandoTela) {
                transceptorVideo.getSender().setTrack(faixaCameraLocal, false);
                atualizarRenderizadorLocal();
            }

            ServicoChamada.iniciarOuAtualizar(contextoApp, salaAtual, compartilhandoTela, true, null);
            ouvinteUi.aoAtualizarEstadoVideoLocal(true, compartilhandoTela, true);
            enviarSinalEstadoVideo(true);
        } catch (Exception erroCamera) {
            ouvinteUi.aoMostrarToast("Nao foi possivel ligar a camera.", "error", 4000);
        }
    }

    public boolean estaCameraAtiva() {
        return cameraAtiva;
    }

    // ---------------------------------------------------------------------
    // Compartilhamento de Tela + Áudio
    // ---------------------------------------------------------------------

    public void iniciarCompartilhamentoTela(Intent dadosPermissaoProjecao, boolean incluirAudio) {
        if (ocupadoCompartilhando || faixaMicrofoneLocal == null || conexaoPar == null || dadosPermissaoProjecao == null) {
            return;
        }
        ocupadoCompartilhando = true;
        PeerConnection conexaoEsperada = conexaoPar;

        ServicoChamada.iniciarOuAtualizar(contextoApp, salaAtual, true, cameraAtiva, () -> {
            try {
                if (conexaoPar != conexaoEsperada || faixaMicrofoneLocal == null) {
                    ocupadoCompartilhando = false;
                    return;
                }
                substituirCompartilhamentoTelaInterno(dadosPermissaoProjecao, incluirAudio, conexaoEsperada);
            } catch (Exception erro) {
                ouvinteUi.aoMostrarToast("Não foi possível trocar a tela compartilhada.", "error", 4000);
            } finally {
                ocupadoCompartilhando = false;
            }
        });
    }

    private void substituirCompartilhamentoTelaInterno(
            Intent dadosPermissaoProjecao,
            boolean incluirAudio,
            PeerConnection conexaoEsperada
    ) {
        ScreenCapturerAndroid capturadorAnterior = capturadorTela;
        SurfaceTextureHelper texturaAnterior = auxiliarTexturaTela;
        VideoTrack faixaAnterior = faixaTelaLocal;
        VideoSource fonteAnterior = fonteTelaLocal;
        capturadorAudioSistema.parar();
        compartilhandoAudioTela = false;

        int[] dimensoes = calcularDimensoesCapturaTela();
        ScreenCapturerAndroid novoCapturador;
        SurfaceTextureHelper novaTextura;
        VideoSource novaFonte;
        VideoTrack novaFaixa;

        try {
            novoCapturador = new ScreenCapturerAndroid(
                    dadosPermissaoProjecao,
                    new MediaProjection.Callback() {
                        @Override
                        public void onStop() {
                            executarNaThreadPrincipal(() -> {
                                if (compartilhandoTela) {
                                    pararCompartilhamentoTela();
                                }
                            });
                        }
                    }
            );
            novaTextura = SurfaceTextureHelper.create("ThreadTelaUnme", obterContextoEgl());
            novaFonte = fabricaConexao.createVideoSource(true);
            novoCapturador.initialize(novaTextura, contextoApp, novaFonte.getCapturerObserver());
            novoCapturador.startCapture(dimensoes[0], dimensoes[1], 30);
            novaFaixa = fabricaConexao.createVideoTrack("tela_local_" + System.currentTimeMillis(), novaFonte);
            novaFaixa.setEnabled(true);
        } catch (Exception erroCaptura) {
            ouvinteUi.aoMostrarToast("Nao foi possivel iniciar o compartilhamento de tela.", "error", 4000);
            ServicoChamada.iniciarOuAtualizar(contextoApp, salaAtual, compartilhandoTela, cameraAtiva, null);
            return;
        }

        if (conexaoPar != conexaoEsperada || faixaMicrofoneLocal == null) {
            encerrarRecursosTela(novoCapturador, novaTextura, novaFaixa, novaFonte);
            return;
        }

        RtpSender remetenteVideo = transceptorVideo != null ? transceptorVideo.getSender() : null;
        if (remetenteVideo != null && !remetenteVideo.setTrack(novaFaixa, false)) {
            encerrarRecursosTela(novoCapturador, novaTextura, novaFaixa, novaFonte);
            ouvinteUi.aoMostrarToast("Não foi possível trocar a tela compartilhada.", "error", 4000);
            return;
        }

        // Limpa eventual captura anterior
        encerrarRecursosTela(capturadorAnterior, texturaAnterior, faixaAnterior, fonteAnterior);

        capturadorTela = novoCapturador;
        auxiliarTexturaTela = novaTextura;
        fonteTelaLocal = novaFonte;
        faixaTelaLocal = novaFaixa;
        compartilhandoTela = true;

        if (incluirAudio) {
            MediaProjection projecao = novoCapturador.getMediaProjection();
            boolean iniciouAudio = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    && capturadorAudioSistema.iniciar(projecao, ultimaTaxaGravada, ultimosCanaisGravados);
            compartilhandoAudioTela = iniciouAudio;
            if (!iniciouAudio) {
                ouvinteUi.aoMostrarToast("A tela está sendo compartilhada sem áudio.", "info", 4000);
            }
        }

        atualizarRenderizadorLocal();
        ouvinteUi.aoAtualizarEstadoVideoLocal(true, true, cameraAtiva);
        ouvinteUi.aoAtualizarPerfis(perfilRemoto, true, telaRemotaCompartilhada);
        enviarSinalSimples("screen-share-start");
        enviarSinalEstadoVideo(true);
    }

    public void pararCompartilhamentoTela() {
        if (!compartilhandoTela) {
            return;
        }
        compartilhandoTela = false;
        compartilhandoAudioTela = false;
        capturadorAudioSistema.parar();

        ScreenCapturerAndroid cap = capturadorTela;
        SurfaceTextureHelper tex = auxiliarTexturaTela;
        VideoTrack faixa = faixaTelaLocal;
        VideoSource fonte = fonteTelaLocal;
        capturadorTela = null;
        auxiliarTexturaTela = null;
        faixaTelaLocal = null;
        fonteTelaLocal = null;

        if (renderizadorLocal != null && faixa != null) {
            try {
                faixa.removeSink(renderizadorLocal);
            } catch (Exception ignorado) {
            }
        }

        if (transceptorVideo != null && conexaoPar != null
                && conexaoPar.signalingState() != PeerConnection.SignalingState.CLOSED) {
            transceptorVideo.getSender().setTrack(cameraAtiva ? faixaCameraLocal : null, false);
        }
        if (transceptorAudioTela != null && conexaoPar != null
                && conexaoPar.signalingState() != PeerConnection.SignalingState.CLOSED) {
            transceptorAudioTela.getSender().setTrack(null, false);
        }

        encerrarRecursosTela(cap, tex, faixa, fonte);

        ServicoChamada.iniciarOuAtualizar(contextoApp, salaAtual, false, cameraAtiva, null);
        atualizarRenderizadorLocal();
        ouvinteUi.aoAtualizarEstadoVideoLocal(cameraAtiva, false, cameraAtiva);
        ouvinteUi.aoAtualizarPerfis(perfilRemoto, false, telaRemotaCompartilhada);
        enviarSinalSimples("screen-share-stop");
        enviarSinalEstadoVideo(cameraAtiva);
    }

    public boolean estaCompartilhandoTela() {
        return compartilhandoTela;
    }

    public boolean estaCompartilhandoAudioTela() {
        return compartilhandoAudioTela;
    }

    private void encerrarRecursosTela(
            ScreenCapturerAndroid cap,
            SurfaceTextureHelper tex,
            VideoTrack faixa,
            VideoSource fonte
    ) {
        if (cap != null) {
            try {
                cap.stopCapture();
            } catch (Exception ignorado) {
            }
            try {
                cap.dispose();
            } catch (Exception ignorado) {
            }
        }
        if (faixa != null) {
            try {
                faixa.dispose();
            } catch (Exception ignorado) {
            }
        }
        if (fonte != null) {
            try {
                fonte.dispose();
            } catch (Exception ignorado) {
            }
        }
        if (tex != null) {
            try {
                tex.dispose();
            } catch (Exception ignorado) {
            }
        }
    }

    private int[] calcularDimensoesCapturaTela() {
        WindowManager gerenciadorJanela = (WindowManager) contextoApp.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metricas = contextoApp.getResources().getDisplayMetrics();
        int largura = metricas.widthPixels > 0 ? metricas.widthPixels : 720;
        int altura = metricas.heightPixels > 0 ? metricas.heightPixels : 1280;
        if (gerenciadorJanela != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.graphics.Rect limites = gerenciadorJanela.getCurrentWindowMetrics().getBounds();
            if (limites.width() > 0 && limites.height() > 0) {
                largura = limites.width();
                altura = limites.height();
            }
        }

        int maxDimensao = Math.max(largura, altura);
        if (maxDimensao > 1280) {
            double escala = 1280.0 / maxDimensao;
            largura = (int) Math.round(largura * escala);
            altura = (int) Math.round(altura * escala);
        }
        // Múltiplos de 16 evitam artefatos em encoders de hardware H.264/VP8
        largura = Math.max(320, (largura / 16) * 16);
        altura = Math.max(320, (altura / 16) * 16);
        return new int[]{largura, altura};
    }

    private void atualizarRenderizadorLocal() {
        if (renderizadorLocal == null) {
            return;
        }
        if (faixaCameraLocal != null) {
            try {
                faixaCameraLocal.removeSink(renderizadorLocal);
            } catch (Exception ignorado) {
            }
        }
        if (faixaTelaLocal != null) {
            try {
                faixaTelaLocal.removeSink(renderizadorLocal);
            } catch (Exception ignorado) {
            }
        }

        if (compartilhandoTela && faixaTelaLocal != null) {
            renderizadorLocal.setMirror(false);
            renderizadorLocal.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
            faixaTelaLocal.addSink(renderizadorLocal);
        } else if (cameraAtiva && faixaCameraLocal != null) {
            renderizadorLocal.setMirror(cameraFrontal);
            renderizadorLocal.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
            faixaCameraLocal.addSink(renderizadorLocal);
        } else {
            renderizadorLocal.clearImage();
        }
    }

    // ---------------------------------------------------------------------
    // PeerConnection, 3 Canais sendrecv e DataChannel de Perfil
    // ---------------------------------------------------------------------

    private void vincularCanaisLocais() {
        if (conexaoPar == null) {
            return;
        }
        List<RtpTransceiver> todos = conexaoPar.getTransceivers();
        List<RtpTransceiver> canaisAudio = new ArrayList<>();
        RtpTransceiver canalVideoEncontrado = null;

        for (RtpTransceiver canal : todos) {
            if (canal.getMediaType() == MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO) {
                canaisAudio.add(canal);
            } else if (canal.getMediaType() == MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO && canalVideoEncontrado == null) {
                canalVideoEncontrado = canal;
            }
        }

        RtpTransceiver.RtpTransceiverInit initEnvioRecepcao =
                new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV);

        transceptorVideo = canalVideoEncontrado != null
                ? canalVideoEncontrado
                : conexaoPar.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, initEnvioRecepcao);
        transceptorMicrofone = canaisAudio.size() > 0
                ? canaisAudio.get(0)
                : conexaoPar.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, initEnvioRecepcao);
        transceptorAudioTela = canaisAudio.size() > 1
                ? canaisAudio.get(1)
                : conexaoPar.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, initEnvioRecepcao);

        transceptorMicrofone.setDirection(RtpTransceiver.RtpTransceiverDirection.SEND_RECV);
        transceptorVideo.setDirection(RtpTransceiver.RtpTransceiverDirection.SEND_RECV);
        transceptorAudioTela.setDirection(RtpTransceiver.RtpTransceiverDirection.SEND_RECV);

        VideoTrack videoAtivo = compartilhandoTela
                ? faixaTelaLocal
                : (cameraAtiva ? faixaCameraLocal : null);

        transceptorMicrofone.getSender().setTrack(faixaMicrofoneLocal, false);
        transceptorVideo.getSender().setTrack(videoAtivo, false);
        transceptorAudioTela.getSender().setTrack(null, false);
    }

    private void criarConexaoPar() {
        cancelarTemporizadoresConexao();
        candidatosPendentes.clear();
        transceptorMicrofone = null;
        transceptorVideo = null;
        transceptorAudioTela = null;
        canalPerfil = null;

        List<PeerConnection.IceServer> servidoresIce = new ArrayList<>();
        for (String url : ConfiguracaoApp.SERVIDORES_ICE) {
            servidoresIce.add(PeerConnection.IceServer.builder(url).createIceServer());
        }

        PeerConnection.RTCConfiguration configuracaoRtc = new PeerConnection.RTCConfiguration(servidoresIce);
        configuracaoRtc.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        configuracaoRtc.iceCandidatePoolSize = ConfiguracaoApp.TAMANHO_POOL_CANDIDATOS_ICE;

        final PeerConnection[] referenciaConexao = new PeerConnection[1];
        PeerConnection novaConexao = fabricaConexao.createPeerConnection(configuracaoRtc, new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState novoEstado) {
            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState novoEstadoIce) {
                executarNaThreadPrincipal(() -> {
                    if (conexaoPar == null || conexaoPar != referenciaConexao[0]) return;
                    if (novoEstadoIce == PeerConnection.IceConnectionState.CONNECTED
                            || novoEstadoIce == PeerConnection.IceConnectionState.COMPLETED) {
                        cancelarTemporizadorFalhaIce();
                    } else if (novoEstadoIce == PeerConnection.IceConnectionState.FAILED) {
                        ouvinteUi.aoMostrarToast(
                                "A rede bloqueou a conexao direta. Tentando novamente...",
                                "error",
                                7000
                        );
                        tentarReiniciarIce();
                    } else if (novoEstadoIce == PeerConnection.IceConnectionState.CHECKING) {
                        cancelarTemporizadorFalhaIce();
                        temporizadorFalhaIce = () -> {
                            if (conexaoPar != null
                                    && conexaoPar.iceConnectionState() == PeerConnection.IceConnectionState.CHECKING) {
                                ouvinteUi.aoMostrarToast(
                                        "A conexao esta bloqueada pela rede. Tente outra rede.",
                                        "error",
                                        9000
                                );
                            }
                        };
                        manipuladorPrincipal.postDelayed(temporizadorFalhaIce, 12000L);
                    }
                });
            }

            @Override
            public void onStandardizedIceConnectionChange(PeerConnection.IceConnectionState novoEstado) {
            }

            @Override
            public void onConnectionChange(PeerConnection.PeerConnectionState novoEstado) {
                executarNaThreadPrincipal(() -> {
                    if (conexaoPar == null || conexaoPar != referenciaConexao[0]) return;
                    if (novoEstado == PeerConnection.PeerConnectionState.CONNECTED) {
                        cancelarTemporizadoresConexao();
                        ouvinteUi.aoMudarEstadoConexao("connected");
                        ouvinteUi.aoAtualizarTextoVazioRemoto("Câmera desligada. A conversa continua.");
                        boolean temVideoLocal = compartilhandoTela || cameraAtiva;
                        enviarSinalEstadoVideo(temVideoLocal);
                        if (compartilhandoTela) {
                            enviarSinalSimples("screen-share-start");
                        }
                    } else if (novoEstado == PeerConnection.PeerConnectionState.DISCONNECTED) {
                        ouvinteUi.aoMudarEstadoConexao("lost");
                        cancelarTemporizadorReconexao();
                        temporizadorReconexao = MotorChamadaWebRtc.this::tentarReiniciarIce;
                        manipuladorPrincipal.postDelayed(temporizadorReconexao, 3000L);
                    } else if (novoEstado == PeerConnection.PeerConnectionState.FAILED) {
                        ouvinteUi.aoMudarEstadoConexao("lost");
                        tentarReiniciarIce();
                    }
                });
            }

            @Override
            public void onIceConnectionReceivingChange(boolean recebendo) {
            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState estadoColeta) {
            }

            @Override
            public void onIceCandidate(IceCandidate candidato) {
                executarNaThreadPrincipal(() -> {
                    if (conexaoPar == null || conexaoPar != referenciaConexao[0] || candidato == null) return;
                    try {
                        JSONObject jsonCandidato = new JSONObject();
                        jsonCandidato.put("candidate", candidato.sdp);
                        jsonCandidato.put("sdpMid", candidato.sdpMid);
                        jsonCandidato.put("sdpMLineIndex", candidato.sdpMLineIndex);

                        JSONObject sinal = new JSONObject();
                        sinal.put("type", "candidate");
                        sinal.put("candidate", jsonCandidato);
                        clienteSinalizacao.enviarSinal(salaAtual, sinal);
                    } catch (JSONException ignorado) {
                    }
                });
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] candidatos) {
            }

            @Override
            public void onAddStream(MediaStream fluxo) {
            }

            @Override
            public void onRemoveStream(MediaStream fluxo) {
            }

            @Override
            public void onDataChannel(DataChannel canal) {
                executarNaThreadPrincipal(() -> {
                    if (conexaoPar != referenciaConexao[0]) {
                        canal.close();
                        return;
                    }
                    if ("profile".equals(canal.label())) {
                        vincularCanalPerfil(canal);
                    } else {
                        canal.close();
                    }
                });
            }

            @Override
            public void onRenegotiationNeeded() {
            }

            @Override
            public void onAddTrack(RtpReceiver receptor, MediaStream[] fluxos) {
            }

            @Override
            public void onTrack(RtpTransceiver transceptor) {
                executarNaThreadPrincipal(() -> {
                    if (conexaoPar == null || conexaoPar != referenciaConexao[0]) return;
                    MediaStreamTrack faixaRecebida = transceptor.getReceiver().track();
                    if (faixaRecebida == null) return;

                    if (faixaRecebida instanceof VideoTrack) {
                        if (faixaVideoRemotaAtual != null && renderizadorRemoto != null) {
                            try {
                                faixaVideoRemotaAtual.removeSink(renderizadorRemoto);
                            } catch (Exception ignorado) {
                            }
                        }
                        faixaVideoRemotaAtual = (VideoTrack) faixaRecebida;
                        if (renderizadorRemoto != null) {
                            renderizadorRemoto.setScalingType(
                                    telaRemotaCompartilhada
                                            ? RendererCommon.ScalingType.SCALE_ASPECT_FIT
                                            : RendererCommon.ScalingType.SCALE_ASPECT_FILL
                            );
                            faixaVideoRemotaAtual.addSink(renderizadorRemoto);
                        }
                        boolean mostrar = videoRemotoHabilitado != null && videoRemotoHabilitado;
                        ouvinteUi.aoAtualizarEstadoVideoRemoto(mostrar, telaRemotaCompartilhada);
                    } else if (faixaRecebida instanceof AudioTrack) {
                        AudioTrack faixaAudio = (AudioTrack) faixaRecebida;
                        List<RtpTransceiver> canaisAudio = new ArrayList<>();
                        for (RtpTransceiver t : conexaoPar.getTransceivers()) {
                            if (t.getMediaType() == MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO) {
                                canaisAudio.add(t);
                            }
                        }
                        boolean ehCanalVoz = !canaisAudio.isEmpty() && transceptor == canaisAudio.get(0);
                        if (ehCanalVoz) {
                            desanexarPiaFalaRemota();
                            faixaVozRemota = faixaAudio;
                            piaFalaRemota = (dadosAudio, bitsPorAmostra, taxa, canais, quadros, timestamp) -> {
                                double nivelBruto = MisturadorAudio.calcularNivelAudio(
                                        dadosAudio,
                                        dadosAudio.remaining()
                                );
                                processarAmostraFalaRemota(nivelBruto);
                            };
                            faixaVozRemota.addSink(piaFalaRemota);
                        } else {
                            faixaTelaRemota = faixaAudio;
                        }
                        aplicarVolumesRemotos();
                    }
                });
            }
        });

        referenciaConexao[0] = novaConexao;
        conexaoPar = novaConexao;

        if (iniciador && conexaoPar != null) {
            vincularCanaisLocais();
            DataChannel.Init initCanal = new DataChannel.Init();
            vincularCanalPerfil(conexaoPar.createDataChannel("profile", initCanal));
        }
    }

    private void vincularCanalPerfil(DataChannel canal) {
        if (canal == null) {
            return;
        }
        canalPerfil = canal;
        canal.registerObserver(new DataChannel.Observer() {
            @Override
            public void onBufferedAmountChange(long quantidadeAnterior) {
            }

            @Override
            public void onStateChange() {
                executarNaThreadPrincipal(() -> {
                    if (canalPerfil != canal) return;
                    if (canal.state() == DataChannel.State.OPEN) {
                        enviarPerfilPeloCanalDados();
                    }
                });
            }

            @Override
            public void onMessage(DataChannel.Buffer buffer) {
                if (buffer.binary || buffer.data == null) {
                    return;
                }
                int tamanho = buffer.data.remaining();
                if (tamanho <= 0 || tamanho > (GerenciadorPerfil.TAMANHO_MAXIMO_FOTO + 1024)) {
                    return;
                }
                byte[] bytes = new byte[tamanho];
                buffer.data.get(bytes);
                String texto = new String(bytes, StandardCharsets.UTF_8);
                PerfilUsuario recebido = GerenciadorPerfil.interpretarPayloadCanalDados(texto);
                if (recebido != null) {
                    executarNaThreadPrincipal(() -> {
                        if (canalPerfil != canal) return;
                        perfilRemoto = recebido;
                        ouvinteUi.aoAtualizarPerfis(perfilRemoto, compartilhandoTela, telaRemotaCompartilhada);
                    });
                }
            }
        });

        if (canal.state() == DataChannel.State.OPEN) {
            enviarPerfilPeloCanalDados();
        }
    }

    public void notificarAlteracaoPerfilLocal() {
        ouvinteUi.aoAtualizarPerfis(perfilRemoto, compartilhandoTela, telaRemotaCompartilhada);
        enviarPerfilPeloCanalDados();
    }

    private void enviarPerfilPeloCanalDados() {
        if (canalPerfil == null || canalPerfil.state() != DataChannel.State.OPEN) {
            return;
        }
        String payload = GerenciadorPerfil.criarPayloadCanalDados(ouvinteUi.obterPerfilLocalAtual());
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        canalPerfil.send(new DataChannel.Buffer(buffer, false));
    }

    // ---------------------------------------------------------------------
    // Fila de Sinais SDP / ICE e Renegociação
    // ---------------------------------------------------------------------

    private void criarOfertaSePronto() {
        if (conexaoPar == null || !iniciador || !parceiroRemotoPronto) {
            return;
        }
        if (conexaoPar.signalingState() != PeerConnection.SignalingState.STABLE) {
            return;
        }
        renegociarConexao();
    }

    private void renegociarConexao() {
        if (conexaoPar == null || !iniciador || !parceiroRemotoPronto) {
            return;
        }
        if (renegociacaoEmAndamento || conexaoPar.signalingState() != PeerConnection.SignalingState.STABLE) {
            renegociacaoEnfileirada = true;
            return;
        }
        renegociacaoEmAndamento = true;
        final PeerConnection conexaoAlvo = conexaoPar;

        conexaoAlvo.createOffer(new ObservadorSdpBase() {
            @Override
            public void onCreateSuccess(SessionDescription oferta) {
                executarNaThreadPrincipal(() -> {
                    if (conexaoPar != conexaoAlvo) {
                        finalizarCicloRenegociacao();
                        return;
                    }
                    conexaoAlvo.setLocalDescription(new ObservadorSdpBase() {
                        @Override
                        public void onSetSuccess() {
                            executarNaThreadPrincipal(() -> {
                                if (conexaoPar == conexaoAlvo) {
                                    enviarDescricaoSdp("offer", conexaoAlvo.getLocalDescription());
                                }
                                finalizarCicloRenegociacao();
                            });
                        }

                        @Override
                        public void onSetFailure(String erro) {
                            executarNaThreadPrincipal(() -> {
                                if (conexaoPar == conexaoAlvo) {
                                    ouvinteUi.aoMostrarToast("Falha ao atualizar a midia: " + erro, "error", 9000);
                                }
                                finalizarCicloRenegociacao();
                            });
                        }
                    }, oferta);
                });
            }

            @Override
            public void onCreateFailure(String erro) {
                executarNaThreadPrincipal(() -> {
                    if (conexaoPar == conexaoAlvo) {
                        ouvinteUi.aoMostrarToast("Falha ao atualizar a midia: " + erro, "error", 9000);
                    }
                    finalizarCicloRenegociacao();
                });
            }
        }, new MediaConstraints());
    }

    private void finalizarCicloRenegociacao() {
        renegociacaoEmAndamento = false;
        if (renegociacaoEnfileirada
                && conexaoPar != null
                && conexaoPar.signalingState() == PeerConnection.SignalingState.STABLE) {
            renegociacaoEnfileirada = false;
            renegociarConexao();
        }
    }

    private void tentarReiniciarIce() {
        if (conexaoPar == null || !iniciador) {
            return;
        }
        if (conexaoPar.connectionState() == PeerConnection.PeerConnectionState.CONNECTED) {
            return;
        }
        final PeerConnection conexaoAlvo = conexaoPar;
        MediaConstraints restricoes = new MediaConstraints();
        restricoes.mandatory.add(new MediaConstraints.KeyValuePair("IceRestart", "true"));

        conexaoAlvo.createOffer(new ObservadorSdpBase() {
            @Override
            public void onCreateSuccess(SessionDescription oferta) {
                executarNaThreadPrincipal(() -> {
                    if (conexaoPar != conexaoAlvo) return;
                    conexaoAlvo.setLocalDescription(new ObservadorSdpBase() {
                        @Override
                        public void onSetSuccess() {
                            executarNaThreadPrincipal(() -> {
                                if (conexaoPar == conexaoAlvo) {
                                    enviarDescricaoSdp("offer", conexaoAlvo.getLocalDescription());
                                }
                            });
                        }
                    }, oferta);
                });
            }
        }, restricoes);
    }

    private void drenarSinais() {
        if (processandoSinais || conexaoPar == null || sinaisPendentes.isEmpty()) {
            return;
        }
        processandoSinais = true;
        processarProximoSinalDaFila();
    }

    private void processarProximoSinalDaFila() {
        if (conexaoPar == null || sinaisPendentes.isEmpty()) {
            processandoSinais = false;
            return;
        }
        JSONObject dados = sinaisPendentes.poll();
        if (dados == null) {
            processarProximoSinalDaFila();
            return;
        }

        String tipo = dados.optString("type", "");
        final PeerConnection conexaoAlvo = conexaoPar;

        try {
            if ("offer".equals(tipo)) {
                JSONObject sdpJson = dados.optJSONObject("sdp");
                String descricaoTexto = sdpJson != null ? sdpJson.optString("sdp", "") : "";
                SessionDescription ofertaRemota = new SessionDescription(
                        SessionDescription.Type.OFFER,
                        descricaoTexto
                );
                conexaoAlvo.setRemoteDescription(new ObservadorSdpBase() {
                    @Override
                    public void onSetSuccess() {
                        executarNaThreadPrincipal(() -> {
                            if (conexaoPar != conexaoAlvo) {
                                processarProximoSinalDaFila();
                                return;
                            }
                            vincularCanaisLocais();
                            descarregarCandidatosPendentes();
                            conexaoAlvo.createAnswer(new ObservadorSdpBase() {
                                @Override
                                public void onCreateSuccess(SessionDescription resposta) {
                                    executarNaThreadPrincipal(() -> {
                                        if (conexaoPar != conexaoAlvo) {
                                            processarProximoSinalDaFila();
                                            return;
                                        }
                                        conexaoAlvo.setLocalDescription(new ObservadorSdpBase() {
                                            @Override
                                            public void onSetSuccess() {
                                                executarNaThreadPrincipal(() -> {
                                                    if (conexaoPar == conexaoAlvo) {
                                                        enviarDescricaoSdp("answer", conexaoAlvo.getLocalDescription());
                                                    }
                                                    processarProximoSinalDaFila();
                                                });
                                            }

                                            @Override
                                            public void onSetFailure(String erro) {
                                                executarNaThreadPrincipal(() -> {
                                                    ouvinteUi.aoMostrarToast("Falha no sinal offer: " + erro, "error", 9000);
                                                    processarProximoSinalDaFila();
                                                });
                                            }
                                        }, resposta);
                                    });
                                }

                                @Override
                                public void onCreateFailure(String erro) {
                                    executarNaThreadPrincipal(() -> {
                                        ouvinteUi.aoMostrarToast("Falha no sinal offer: " + erro, "error", 9000);
                                        processarProximoSinalDaFila();
                                    });
                                }
                            }, new MediaConstraints());
                        });
                    }

                    @Override
                    public void onSetFailure(String erro) {
                        executarNaThreadPrincipal(() -> {
                            ouvinteUi.aoMostrarToast("Falha no sinal offer: " + erro, "error", 9000);
                            processarProximoSinalDaFila();
                        });
                    }
                }, ofertaRemota);
                return;
            } else if ("answer".equals(tipo)) {
                if (conexaoAlvo.signalingState() != PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
                    processarProximoSinalDaFila();
                    return;
                }
                JSONObject sdpJson = dados.optJSONObject("sdp");
                String descricaoTexto = sdpJson != null ? sdpJson.optString("sdp", "") : "";
                SessionDescription respostaRemota = new SessionDescription(
                        SessionDescription.Type.ANSWER,
                        descricaoTexto
                );
                conexaoAlvo.setRemoteDescription(new ObservadorSdpBase() {
                    @Override
                    public void onSetSuccess() {
                        executarNaThreadPrincipal(() -> {
                            if (conexaoPar == conexaoAlvo) {
                                descarregarCandidatosPendentes();
                                if (renegociacaoEnfileirada) {
                                    renegociacaoEnfileirada = false;
                                    renegociarConexao();
                                }
                            }
                            processarProximoSinalDaFila();
                        });
                    }

                    @Override
                    public void onSetFailure(String erro) {
                        executarNaThreadPrincipal(() -> {
                            ouvinteUi.aoMostrarToast("Falha no sinal answer: " + erro, "error", 9000);
                            processarProximoSinalDaFila();
                        });
                    }
                }, respostaRemota);
                return;
            } else if ("ready".equals(tipo)) {
                parceiroRemotoPronto = true;
                if (iniciador) {
                    criarOfertaSePronto();
                }
            } else if ("candidate".equals(tipo)) {
                JSONObject candJson = dados.optJSONObject("candidate");
                if (candJson != null) {
                    IceCandidate candidato = new IceCandidate(
                            candJson.optString("sdpMid", ""),
                            candJson.optInt("sdpMLineIndex", 0),
                            candJson.optString("candidate", "")
                    );
                    if (conexaoAlvo.getRemoteDescription() != null) {
                        conexaoAlvo.addIceCandidate(candidato);
                    } else {
                        candidatosPendentes.add(candidato);
                    }
                }
            } else if ("screen-share-start".equals(tipo)) {
                telaRemotaCompartilhada = true;
                videoRemotoHabilitado = true;
                if (renderizadorRemoto != null) {
                    renderizadorRemoto.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
                }
                ouvinteUi.aoAtualizarEstadoVideoRemoto(true, true);
                ouvinteUi.aoAtualizarPerfis(perfilRemoto, compartilhandoTela, true);
            } else if ("screen-share-stop".equals(tipo)) {
                telaRemotaCompartilhada = false;
                if (renderizadorRemoto != null) {
                    renderizadorRemoto.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
                }
                boolean possuiVideo = videoRemotoHabilitado != null && videoRemotoHabilitado;
                ouvinteUi.aoAtualizarEstadoVideoRemoto(possuiVideo, false);
                ouvinteUi.aoAtualizarPerfis(perfilRemoto, compartilhandoTela, false);
            } else if ("video-state".equals(tipo)) {
                videoRemotoHabilitado = dados.optBoolean("enabled", false);
                ouvinteUi.aoAtualizarEstadoVideoRemoto(videoRemotoHabilitado, telaRemotaCompartilhada);
            } else if ("renegotiate".equals(tipo) && iniciador) {
                renegociarConexao();
            }
        } catch (Exception erroSinal) {
            ouvinteUi.aoMostrarToast("Falha no sinal " + tipo + ": " + erroSinal.getMessage(), "error", 9000);
        }

        processarProximoSinalDaFila();
    }

    private void descarregarCandidatosPendentes() {
        if (conexaoPar == null) {
            return;
        }
        while (!candidatosPendentes.isEmpty()) {
            IceCandidate candidato = candidatosPendentes.remove(0);
            try {
                conexaoPar.addIceCandidate(candidato);
            } catch (Exception ignorado) {
            }
        }
    }

    private void enviarDescricaoSdp(String tipo, SessionDescription descricao) {
        if (descricao == null || salaAtual == null) {
            return;
        }
        try {
            JSONObject sdpJson = new JSONObject();
            sdpJson.put("type", descricao.type.canonicalForm());
            sdpJson.put("sdp", descricao.description);

            JSONObject sinal = new JSONObject();
            sinal.put("type", tipo);
            sinal.put("sdp", sdpJson);
            clienteSinalizacao.enviarSinal(salaAtual, sinal);
        } catch (JSONException ignorado) {
        }
    }

    private void enviarSinalSimples(String tipo) {
        if (salaAtual == null) {
            return;
        }
        try {
            JSONObject sinal = new JSONObject();
            sinal.put("type", tipo);
            clienteSinalizacao.enviarSinal(salaAtual, sinal);
        } catch (JSONException ignorado) {
        }
    }

    private void enviarSinalEstadoVideo(boolean habilitado) {
        if (salaAtual == null) {
            return;
        }
        try {
            JSONObject sinal = new JSONObject();
            sinal.put("type", "video-state");
            sinal.put("enabled", habilitado);
            clienteSinalizacao.enviarSinal(salaAtual, sinal);
        } catch (JSONException ignorado) {
        }
    }

    // ---------------------------------------------------------------------
    // Indicadores Visuais de Fala
    // ---------------------------------------------------------------------

    private void processarAmostraFalaLocal(double nivelInstantaneo) {
        nivelFalaLocalSuavizado = (0.8 * nivelFalaLocalSuavizado) + (0.2 * nivelInstantaneo);
        boolean falandoAgora = MisturadorAudio.detectarFala(nivelFalaLocalSuavizado);
        executarNaThreadPrincipal(() -> {
            definirFalandoLocal(falandoAgora);
            if (limpadorSilencioLocal != null) {
                manipuladorPrincipal.removeCallbacks(limpadorSilencioLocal);
            }
            if (falandoAgora) {
                limpadorSilencioLocal = () -> definirFalandoLocal(false);
                manipuladorPrincipal.postDelayed(limpadorSilencioLocal, 350L);
            }
        });
    }

    private void definirFalandoLocal(boolean valor) {
        if (falandoLocal != valor) {
            falandoLocal = valor;
            ouvinteUi.aoMudarFalaLocal(valor);
        }
    }

    private void processarAmostraFalaRemota(double nivelInstantaneo) {
        nivelFalaRemotaSuavizado = (0.8 * nivelFalaRemotaSuavizado) + (0.2 * nivelInstantaneo);
        boolean falandoAgora = MisturadorAudio.detectarFala(nivelFalaRemotaSuavizado);
        executarNaThreadPrincipal(() -> {
            definirFalandoRemoto(falandoAgora);
            if (limpadorSilencioRemoto != null) {
                manipuladorPrincipal.removeCallbacks(limpadorSilencioRemoto);
            }
            if (falandoAgora) {
                limpadorSilencioRemoto = () -> definirFalandoRemoto(false);
                manipuladorPrincipal.postDelayed(limpadorSilencioRemoto, 350L);
            }
        });
    }

    private void definirFalandoRemoto(boolean valor) {
        if (falandoRemoto != valor) {
            falandoRemoto = valor;
            ouvinteUi.aoMudarFalaRemota(valor);
        }
    }

    // ---------------------------------------------------------------------
    // Limpeza e Encerramento da Chamada
    // ---------------------------------------------------------------------

    public void sairDaChamada() {
        definirEntrando(false);
        clienteSinalizacao.cancelarVerificacaoPendenteComErro();
        clienteSinalizacao.sairDaSala();
        clienteSinalizacao.desconectar();

        compartilhandoTela = false;
        compartilhandoAudioTela = false;
        capturadorAudioSistema.parar();
        encerrarRecursosTela(capturadorTela, auxiliarTexturaTela, faixaTelaLocal, fonteTelaLocal);
        capturadorTela = null;
        auxiliarTexturaTela = null;
        faixaTelaLocal = null;
        fonteTelaLocal = null;

        if (capturadorCamera != null) {
            try {
                capturadorCamera.stopCapture();
            } catch (Exception ignorado) {
            }
            try {
                capturadorCamera.dispose();
            } catch (Exception ignorado) {
            }
            capturadorCamera = null;
        }
        if (faixaCameraLocal != null) {
            if (renderizadorLocal != null) {
                try {
                    faixaCameraLocal.removeSink(renderizadorLocal);
                } catch (Exception ignorado) {
                }
            }
            faixaCameraLocal.dispose();
            faixaCameraLocal = null;
        }
        if (fonteCameraLocal != null) {
            fonteCameraLocal.dispose();
            fonteCameraLocal = null;
        }
        if (auxiliarTexturaCamera != null) {
            auxiliarTexturaCamera.dispose();
            auxiliarTexturaCamera = null;
        }
        cameraAtiva = false;

        if (faixaMicrofoneLocal != null) {
            faixaMicrofoneLocal.dispose();
            faixaMicrofoneLocal = null;
        }
        if (fonteMicrofoneLocal != null) {
            fonteMicrofoneLocal.dispose();
            fonteMicrofoneLocal = null;
        }
        microfoneHabilitado = true;
        if (moduloAudio != null) {
            moduloAudio.setMicrophoneMute(false);
            moduloAudio.setSpeakerMute(false);
        }

        limparMidiaRemota();
        fecharConexaoParAtual();

        gerenciadorDispositivos.encerrarModoComunicacao();
        ServicoChamada.pararServico(contextoApp);

        salaAtual = null;
        iniciador = false;
        jaEntrouAlgumaVez = false;
        candidatosPendentes.clear();
        sinaisPendentes.clear();
        parceiroEntrouPendente = false;
        parceiroRemotoPronto = false;
        processandoSinais = false;
        renegociacaoEnfileirada = false;
        renegociacaoEmAndamento = false;
        videoRemotoHabilitado = null;
        telaRemotaCompartilhada = false;
        perfilRemoto = PerfilUsuario.vazio();

        misturadorAudio.definirSilenciadoTotal(false);
        definirFalandoLocal(false);
        definirFalandoRemoto(false);

        if (renderizadorLocal != null) {
            renderizadorLocal.clearImage();
        }
        if (renderizadorRemoto != null) {
            renderizadorRemoto.clearImage();
        }

        ouvinteUi.aoAtualizarTextoVazioRemoto("Envie o código e espere sua companhia.");
        ouvinteUi.aoIrParaTelaLobby();
    }

    private void limparMidiaRemota() {
        desanexarPiaFalaRemota();
        faixaVozRemota = null;
        faixaTelaRemota = null;
        if (faixaVideoRemotaAtual != null && renderizadorRemoto != null) {
            try {
                faixaVideoRemotaAtual.removeSink(renderizadorRemoto);
            } catch (Exception ignorado) {
            }
        }
        faixaVideoRemotaAtual = null;
        if (renderizadorRemoto != null) {
            renderizadorRemoto.clearImage();
        }
        definirFalandoRemoto(false);
    }

    private void desanexarPiaFalaRemota() {
        if (faixaVozRemota != null && piaFalaRemota != null) {
            try {
                faixaVozRemota.removeSink(piaFalaRemota);
            } catch (Exception ignorado) {
            }
        }
        piaFalaRemota = null;
    }

    private void fecharConexaoParAtual() {
        cancelarTemporizadoresConexao();
        if (canalPerfil != null) {
            try {
                canalPerfil.unregisterObserver();
                canalPerfil.close();
            } catch (Exception ignorado) {
            }
            canalPerfil = null;
        }
        transceptorMicrofone = null;
        transceptorVideo = null;
        transceptorAudioTela = null;
        if (conexaoPar != null) {
            try {
                conexaoPar.close();
                conexaoPar.dispose();
            } catch (Exception ignorado) {
            }
            conexaoPar = null;
        }
    }

    private void cancelarTemporizadoresConexao() {
        cancelarTemporizadorReconexao();
        cancelarTemporizadorFalhaIce();
    }

    private void cancelarTemporizadorReconexao() {
        if (temporizadorReconexao != null) {
            manipuladorPrincipal.removeCallbacks(temporizadorReconexao);
            temporizadorReconexao = null;
        }
    }

    private void cancelarTemporizadorFalhaIce() {
        if (temporizadorFalhaIce != null) {
            manipuladorPrincipal.removeCallbacks(temporizadorFalhaIce);
            temporizadorFalhaIce = null;
        }
    }

    public void liberarTudo() {
        sairDaChamada();
        if (renderizadorLocal != null) {
            renderizadorLocal.release();
            renderizadorLocal = null;
        }
        if (renderizadorRemoto != null) {
            renderizadorRemoto.release();
            renderizadorRemoto = null;
        }
        if (fabricaConexao != null) {
            fabricaConexao.dispose();
            fabricaConexao = null;
        }
        if (moduloAudio != null) {
            moduloAudio.release();
            moduloAudio = null;
        }
        if (contextoEgl != null) {
            contextoEgl.release();
            contextoEgl = null;
        }
    }

    private void executarNaThreadPrincipal(Runnable tarefa) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            tarefa.run();
        } else {
            manipuladorPrincipal.post(tarefa);
        }
    }

    private abstract static class ObservadorSdpBase implements SdpObserver {
        @Override
        public void onCreateSuccess(SessionDescription sdp) {
        }

        @Override
        public void onSetSuccess() {
        }

        @Override
        public void onCreateFailure(String erro) {
        }

        @Override
        public void onSetFailure(String erro) {
        }
    }
}
