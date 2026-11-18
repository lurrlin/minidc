package com.example.unme;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.webrtc.RendererCommon;
import org.webrtc.SurfaceViewRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements MotorChamadaWebRtc.OuvinteInterfaceChamada {

    // Raiz e telas principais
    private View containerRaiz;
    private View telaLobby;
    private View telaChamada;

    // Lobby
    private View containerAcoesLobby;
    private View formularioEntrarSala;
    private View containerSalaCriada;
    private EditText campoCodigoSala;
    private AppCompatButton botaoCriarSala;
    private AppCompatButton botaoMostrarEntrar;
    private AppCompatButton botaoEntrarSala;
    private AppCompatButton botaoVoltarLobby;
    private AppCompatButton botaoCopiarSalaLobby;
    private TextView textoCodigoCriado;
    private TextView textoStatusLobby;
    private View botaoEditarPerfil;
    private View containerAvatarLobby;
    private ImageView imagemAvatarLobby;
    private TextView textoAvatarLobby;
    private TextView textoNomePerfilLobby;

    // Cabeçalho da chamada
    private View cabecalhoChamada;
    private View pontoConexao;
    private TextView textoRotuloSala;
    private TextView textoStatusChamada;
    private TextView textoCodigoSalaAtiva;
    private TextView botaoCopiarSalaAtiva;
    private View botaoAbrirConfiguracoes;

    // Palco e quadros de vídeo
    private LinearLayout palcoVideos;
    private View espacadorQuadros;
    private FrameLayout quadroRemoto;
    private SurfaceViewRenderer videoRemoto;
    private View containerAvatarRemoto;
    private ImageView imagemAvatarRemoto;
    private TextView textoAvatarRemoto;
    private TextView textoVazioRemoto;
    private TextView textoRotuloQuadroRemoto;
    private TextView botaoVolumeRemoto;
    private TextView botaoTelaCheia;
    private View menuContextoRemoto;
    private SeekBar barraVolumeVoz;
    private TextView textoValorVolumeVoz;
    private SeekBar barraVolumeTela;
    private TextView textoValorVolumeTela;

    private FrameLayout quadroLocal;
    private SurfaceViewRenderer videoLocal;
    private View containerAvatarLocal;
    private ImageView imagemAvatarLocal;
    private TextView textoAvatarLocal;
    private TextView textoRotuloQuadroLocal;

    // Barra inferior de controles
    private View barraControles;
    private View botaoMudo;
    private ImageView iconeMudo;
    private TextView rotuloMudo;
    private View botaoCamera;
    private ImageView iconeCamera;
    private TextView rotuloCamera;
    private View botaoCompartilhar;
    private ImageView iconeCompartilhar;
    private TextView rotuloCompartilhar;
    private View botaoOuvir;
    private ImageView iconeOuvir;
    private TextView rotuloOuvir;
    private View botaoSair;

    // Modal de Perfil
    private View overlayModalPerfil;
    private View cardModalPerfil;
    private View botaoFecharPerfil;
    private View containerPreviaAvatar;
    private ImageView imagemPreviaAvatar;
    private TextView textoPreviaAvatar;
    private AppCompatButton botaoEscolherFoto;
    private View botaoRemoverFoto;
    private EditText campoNomePerfil;
    private TextView textoStatusPerfil;
    private AppCompatButton botaoSalvarPerfil;

    // Modal de Compartilhamento
    private View overlayModalCompartilhar;
    private View cardModalCompartilhar;
    private View botaoFecharCompartilhar;
    private CheckBox caixaIncluirAudioSistema;
    private View botaoFonteTelaAparelho;
    private AppCompatButton botaoPararCompartilhamento;

    // Modal de Configurações
    private View overlayModalConfiguracoes;
    private View cardModalConfiguracoes;
    private View botaoFecharConfiguracoes;
    private Spinner seletorMicrofone;
    private Spinner seletorSaidaAudio;
    private SeekBar barraVolumeEntrada;
    private TextView textoValorVolumeEntrada;
    private SeekBar barraVolumeSaida;
    private TextView textoValorVolumeSaida;
    private CheckBox caixaReducaoRuido;
    private TextView textoEstadoReducaoRuido;
    private CheckBox caixaCancelamentoEco;
    private TextView textoEstadoCancelamentoEco;
    private CheckBox caixaGanhoAutomatico;
    private TextView textoEstadoGanhoAutomatico;
    private TextView textoStatusProcessamentoAudio;

    // Toast
    private TextView textoToast;

    // Gerenciadores e estado
    private final Handler manipuladorUi = new Handler(Looper.getMainLooper());
    private final ExecutorService executorFundo = Executors.newSingleThreadExecutor();
    private GerenciadorPerfil gerenciadorPerfil;
    private MotorChamadaWebRtc motorChamada;

    private PerfilUsuario perfilLocal = PerfilUsuario.vazio();
    private String fotoRascunho = "";
    private int revisaoPerfil = 0;

    private Runnable temporizadorToast;
    private ObjectAnimator animadorPulsoConexao;
    private boolean sincronizandoControlesConfiguracao = false;
    private List<GerenciadorDispositivosAudio.OpcaoDispositivo> listaMicrofonesAtual = new ArrayList<>();
    private List<GerenciadorDispositivosAudio.OpcaoDispositivo> listaSaidasAtual = new ArrayList<>();

    // Layout do palco: 0 = normal, 1 = remoto expandido, 2 = local expandido
    private int quadroExpandido = 0;
    private boolean emTelaCheia = false;
    private boolean possuiVideoRemotoAtivo = false;
    private boolean telaRemotaEmCompartilhamento = false;
    private boolean falandoLocalVisivel = false;
    private boolean falandoRemotoVisivel = false;

    // Ações pendentes de permissão
    private String salaPendentePermissao = null;
    private boolean verificarSalaPendente = false;
    private boolean incluirAudioPendenteProjecao = false;

    private ActivityResultLauncher<String[]> lancadorPermissoesChamada;
    private ActivityResultLauncher<String> lancadorPermissaoCamera;
    private ActivityResultLauncher<String[]> lancadorEscolherFoto;
    private ActivityResultLauncher<Intent> lancadorCapturaTela;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        gerenciadorPerfil = new GerenciadorPerfil(this);
        perfilLocal = gerenciadorPerfil.carregar();

        vincularElementosVisuais();
        configurarMargensSistema();
        registrarLancadoresAtividade();

        motorChamada = new MotorChamadaWebRtc(this, this);
        motorChamada.vincularRenderizadores(videoLocal, videoRemoto);

        configurarAcoesLobby();
        configurarAcoesPerfil();
        configurarAcoesChamada();
        configurarAcoesCompartilhamento();
        configurarAcoesConfiguracoes();
        configurarBotaoVoltar();

        renderizarPerfilNoLobby();
        aoAtualizarPerfis(PerfilUsuario.vazio(), false, false);
        aplicarLayoutPalco();
    }

    private void vincularElementosVisuais() {
        containerRaiz = findViewById(R.id.main);
        telaLobby = findViewById(R.id.tela_lobby);
        telaChamada = findViewById(R.id.tela_chamada);

        containerAcoesLobby = findViewById(R.id.container_acoes_lobby);
        formularioEntrarSala = findViewById(R.id.formulario_entrar_sala);
        containerSalaCriada = findViewById(R.id.container_sala_criada);
        campoCodigoSala = findViewById(R.id.campo_codigo_sala);
        botaoCriarSala = findViewById(R.id.botao_criar_sala);
        botaoMostrarEntrar = findViewById(R.id.botao_mostrar_entrar);
        botaoEntrarSala = findViewById(R.id.botao_entrar_sala);
        botaoVoltarLobby = findViewById(R.id.botao_voltar_lobby);
        botaoCopiarSalaLobby = findViewById(R.id.botao_copiar_sala_lobby);
        textoCodigoCriado = findViewById(R.id.texto_codigo_criado);
        textoStatusLobby = findViewById(R.id.texto_status_lobby);
        botaoEditarPerfil = findViewById(R.id.botao_editar_perfil);
        containerAvatarLobby = findViewById(R.id.container_avatar_lobby);
        imagemAvatarLobby = findViewById(R.id.imagem_avatar_lobby);
        textoAvatarLobby = findViewById(R.id.texto_avatar_lobby);
        textoNomePerfilLobby = findViewById(R.id.texto_nome_perfil_lobby);

        cabecalhoChamada = findViewById(R.id.cabecalho_chamada);
        pontoConexao = findViewById(R.id.ponto_conexao);
        textoRotuloSala = findViewById(R.id.texto_rotulo_sala);
        textoStatusChamada = findViewById(R.id.texto_status_chamada);
        textoCodigoSalaAtiva = findViewById(R.id.texto_codigo_sala_ativa);
        botaoCopiarSalaAtiva = findViewById(R.id.botao_copiar_sala_ativa);
        botaoAbrirConfiguracoes = findViewById(R.id.botao_abrir_configuracoes);

        palcoVideos = findViewById(R.id.palco_videos);
        espacadorQuadros = findViewById(R.id.espacador_quadros);
        quadroRemoto = findViewById(R.id.quadro_remoto);
        videoRemoto = findViewById(R.id.video_remoto);
        containerAvatarRemoto = findViewById(R.id.container_avatar_remoto);
        imagemAvatarRemoto = findViewById(R.id.imagem_avatar_remoto);
        textoAvatarRemoto = findViewById(R.id.texto_avatar_remoto);
        textoVazioRemoto = findViewById(R.id.texto_vazio_remoto);
        textoRotuloQuadroRemoto = findViewById(R.id.texto_rotulo_quadro_remoto);
        botaoVolumeRemoto = findViewById(R.id.botao_volume_remoto);
        botaoTelaCheia = findViewById(R.id.botao_tela_cheia);
        menuContextoRemoto = findViewById(R.id.menu_contexto_remoto);
        barraVolumeVoz = findViewById(R.id.barra_volume_voz);
        textoValorVolumeVoz = findViewById(R.id.texto_valor_volume_voz);
        barraVolumeTela = findViewById(R.id.barra_volume_tela);
        textoValorVolumeTela = findViewById(R.id.texto_valor_volume_tela);

        quadroLocal = findViewById(R.id.quadro_local);
        videoLocal = findViewById(R.id.video_local);
        containerAvatarLocal = findViewById(R.id.container_avatar_local);
        imagemAvatarLocal = findViewById(R.id.imagem_avatar_local);
        textoAvatarLocal = findViewById(R.id.texto_avatar_local);
        textoRotuloQuadroLocal = findViewById(R.id.texto_rotulo_quadro_local);

        barraControles = findViewById(R.id.barra_controles);
        botaoMudo = findViewById(R.id.botao_mudo);
        iconeMudo = findViewById(R.id.icone_mudo);
        rotuloMudo = findViewById(R.id.rotulo_mudo);
        botaoCamera = findViewById(R.id.botao_camera);
        iconeCamera = findViewById(R.id.icone_camera);
        rotuloCamera = findViewById(R.id.rotulo_camera);
        botaoCompartilhar = findViewById(R.id.botao_compartilhar);
        iconeCompartilhar = findViewById(R.id.icone_compartilhar);
        rotuloCompartilhar = findViewById(R.id.rotulo_compartilhar);
        botaoOuvir = findViewById(R.id.botao_ouvir);
        iconeOuvir = findViewById(R.id.icone_ouvir);
        rotuloOuvir = findViewById(R.id.rotulo_ouvir);
        botaoSair = findViewById(R.id.botao_sair);

        overlayModalPerfil = findViewById(R.id.overlay_modal_perfil);
        cardModalPerfil = findViewById(R.id.card_modal_perfil);
        botaoFecharPerfil = findViewById(R.id.botao_fechar_perfil);
        containerPreviaAvatar = findViewById(R.id.container_previa_avatar);
        imagemPreviaAvatar = findViewById(R.id.imagem_previa_avatar);
        textoPreviaAvatar = findViewById(R.id.texto_previa_avatar);
        botaoEscolherFoto = findViewById(R.id.botao_escolher_foto);
        botaoRemoverFoto = findViewById(R.id.botao_remover_foto);
        campoNomePerfil = findViewById(R.id.campo_nome_perfil);
        textoStatusPerfil = findViewById(R.id.texto_status_perfil);
        botaoSalvarPerfil = findViewById(R.id.botao_salvar_perfil);

        overlayModalCompartilhar = findViewById(R.id.overlay_modal_compartilhar);
        cardModalCompartilhar = findViewById(R.id.card_modal_compartilhar);
        botaoFecharCompartilhar = findViewById(R.id.botao_fechar_compartilhar);
        caixaIncluirAudioSistema = findViewById(R.id.caixa_incluir_audio_sistema);
        botaoFonteTelaAparelho = findViewById(R.id.botao_fonte_tela_aparelho);
        botaoPararCompartilhamento = findViewById(R.id.botao_parar_compartilhamento);

        overlayModalConfiguracoes = findViewById(R.id.overlay_modal_configuracoes);
        cardModalConfiguracoes = findViewById(R.id.card_modal_configuracoes);
        botaoFecharConfiguracoes = findViewById(R.id.botao_fechar_configuracoes);
        seletorMicrofone = findViewById(R.id.seletor_microfone);
        seletorSaidaAudio = findViewById(R.id.seletor_saida_audio);
        barraVolumeEntrada = findViewById(R.id.barra_volume_entrada);
        textoValorVolumeEntrada = findViewById(R.id.texto_valor_volume_entrada);
        barraVolumeSaida = findViewById(R.id.barra_volume_saida);
        textoValorVolumeSaida = findViewById(R.id.texto_valor_volume_saida);
        caixaReducaoRuido = findViewById(R.id.caixa_reducao_ruido);
        textoEstadoReducaoRuido = findViewById(R.id.texto_estado_reducao_ruido);
        caixaCancelamentoEco = findViewById(R.id.caixa_cancelamento_eco);
        textoEstadoCancelamentoEco = findViewById(R.id.texto_estado_cancelamento_eco);
        caixaGanhoAutomatico = findViewById(R.id.caixa_ganho_automatico);
        textoEstadoGanhoAutomatico = findViewById(R.id.texto_estado_ganho_automatico);
        textoStatusProcessamentoAudio = findViewById(R.id.texto_status_processamento_audio);

        textoToast = findViewById(R.id.texto_toast);

        quadroRemoto.setClipToOutline(true);
        quadroLocal.setClipToOutline(true);
    }

    private void configurarMargensSistema() {
        ViewCompat.setOnApplyWindowInsetsListener(containerRaiz, (vista, margensJanela) -> {
            if (emTelaCheia) {
                vista.setPadding(0, 0, 0, 0);
                return margensJanela;
            }
            Insets barrasSistema = margensJanela.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime()
            );
            vista.setPadding(barrasSistema.left, barrasSistema.top, barrasSistema.right, barrasSistema.bottom);
            return margensJanela;
        });
    }

    private void registrarLancadoresAtividade() {
        lancadorPermissoesChamada = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                mapaResultados -> {
                    boolean temAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                            == PackageManager.PERMISSION_GRANTED;
                    String salaAlvo = salaPendentePermissao;
                    boolean verificar = verificarSalaPendente;
                    salaPendentePermissao = null;
                    verificarSalaPendente = false;

                    if (temAudio && salaAlvo != null) {
                        motorChamada.iniciarEntradaSala(salaAlvo, verificar);
                    } else {
                        aoMostrarToast(
                                "Nao foi possivel acessar camera/microfone. Verifique as permissoes.",
                                "error",
                                6000
                        );
                    }
                }
        );

        lancadorPermissaoCamera = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                concedida -> {
                    if (concedida) {
                        motorChamada.alternarCamera();
                    } else {
                        aoMostrarToast("Nao foi possivel ligar a camera.", "error", 4000);
                    }
                }
        );

        lancadorEscolherFoto = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uriFoto -> {
                    if (uriFoto == null) {
                        return;
                    }
                    final int revisaoAtual = ++revisaoPerfil;
                    botaoSalvarPerfil.setEnabled(false);
                    definirStatusPerfil(getString(R.string.perfil_status_preparando), false);

                    executorFundo.execute(() -> {
                        try {
                            String fotoPreparada = GerenciadorPerfil.prepararFoto(this, uriFoto);
                            manipuladorUi.post(() -> {
                                if (revisaoAtual != revisaoPerfil) return;
                                fotoRascunho = fotoPreparada;
                                renderizarPreviaPerfil();
                                definirStatusPerfil(getString(R.string.perfil_status_pronta), false);
                                botaoSalvarPerfil.setEnabled(true);
                            });
                        } catch (Exception erro) {
                            manipuladorUi.post(() -> {
                                if (revisaoAtual != revisaoPerfil) return;
                                String mensagem = erro.getMessage() != null
                                        ? erro.getMessage()
                                        : "Não foi possível abrir a imagem.";
                                definirStatusPerfil(mensagem, true);
                                botaoSalvarPerfil.setEnabled(true);
                            });
                        }
                    });
                }
        );

        lancadorCapturaTela = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                resultado -> {
                    if (resultado.getResultCode() == Activity.RESULT_OK && resultado.getData() != null) {
                        motorChamada.iniciarCompartilhamentoTela(
                                resultado.getData(),
                                incluirAudioPendenteProjecao
                        );
                    }
                }
        );
    }

    // ---------------------------------------------------------------------
    // Lobby e Perfil
    // ---------------------------------------------------------------------

    private void configurarAcoesLobby() {
        botaoCriarSala.setOnClickListener(v -> {
            String codigoSala = GerenciadorSala.gerarCodigoSala();
            textoCodigoCriado.setText(codigoSala);
            containerSalaCriada.setVisibility(View.VISIBLE);
            solicitarPermissoesEEntrar(codigoSala, false);
        });

        botaoMostrarEntrar.setOnClickListener(v -> {
            formularioEntrarSala.setVisibility(View.VISIBLE);
            containerAcoesLobby.setVisibility(View.GONE);
            campoCodigoSala.requestFocus();
            mostrarTeclado(campoCodigoSala);
        });

        botaoVoltarLobby.setOnClickListener(v -> {
            ocultarTeclado();
            redefinirModoLobby();
        });

        Runnable acaoSubmeterSala = () -> {
            String codigoDigitado = campoCodigoSala.getText().toString().trim();
            if (!GerenciadorSala.validarNomeSala(codigoDigitado)) {
                aoMudarStatusLobby("Nome de sala invalido.", "error");
                return;
            }
            ocultarTeclado();
            solicitarPermissoesEEntrar(codigoDigitado, true);
        };

        botaoEntrarSala.setOnClickListener(v -> acaoSubmeterSala.run());
        campoCodigoSala.setOnEditorActionListener((vista, acaoId, evento) -> {
            if (acaoId == EditorInfo.IME_ACTION_GO || acaoId == EditorInfo.IME_ACTION_DONE) {
                acaoSubmeterSala.run();
                return true;
            }
            return false;
        });

        botaoCopiarSalaLobby.setOnClickListener(v -> copiarTextoAreaTransferencia(
                textoCodigoCriado.getText().toString()
        ));
    }

    private void solicitarPermissoesEEntrar(String nomeSala, boolean verificarExistencia) {
        boolean temMicrofone = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;

        List<String> permissoesParaPedir = new ArrayList<>();
        if (!temMicrofone) {
            permissoesParaPedir.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            permissoesParaPedir.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            permissoesParaPedir.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (!temMicrofone) {
            salaPendentePermissao = nomeSala;
            verificarSalaPendente = verificarExistencia;
            lancadorPermissoesChamada.launch(permissoesParaPedir.toArray(new String[0]));
        } else {
            if (!permissoesParaPedir.isEmpty()) {
                // Permissões auxiliares opcionais sem bloquear se já houver microfone
                salaPendentePermissao = nomeSala;
                verificarSalaPendente = verificarExistencia;
                lancadorPermissoesChamada.launch(permissoesParaPedir.toArray(new String[0]));
            } else {
                motorChamada.iniciarEntradaSala(nomeSala, verificarExistencia);
            }
        }
    }

    private void redefinirModoLobby() {
        formularioEntrarSala.setVisibility(View.GONE);
        containerAcoesLobby.setVisibility(View.VISIBLE);
        containerSalaCriada.setVisibility(View.GONE);
        campoCodigoSala.setText("");
        textoCodigoCriado.setText("");
    }

    private void configurarAcoesPerfil() {
        botaoEditarPerfil.setOnClickListener(v -> abrirModalPerfil());
        botaoFecharPerfil.setOnClickListener(v -> fecharModalPerfil());
        overlayModalPerfil.setOnClickListener(v -> fecharModalPerfil());
        cardModalPerfil.setOnClickListener(v -> {
            // Consome clique interno para não fechar o modal
        });

        campoNomePerfil.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderizarPreviaPerfil();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        botaoEscolherFoto.setOnClickListener(v -> lancadorEscolherFoto.launch(
                new String[]{"image/jpeg", "image/png", "image/webp"}
        ));

        botaoRemoverFoto.setOnClickListener(v -> {
            revisaoPerfil++;
            fotoRascunho = "";
            botaoSalvarPerfil.setEnabled(true);
            renderizarPreviaPerfil();
        });

        botaoSalvarPerfil.setOnClickListener(v -> {
            if (!botaoSalvarPerfil.isEnabled()) {
                return;
            }
            try {
                perfilLocal = gerenciadorPerfil.salvar(
                        new PerfilUsuario(campoNomePerfil.getText().toString(), fotoRascunho)
                );
                renderizarPerfilNoLobby();
                motorChamada.notificarAlteracaoPerfilLocal();
                fecharModalPerfil();
            } catch (Exception erro) {
                definirStatusPerfil("Não foi possível salvar o perfil neste dispositivo. Tente novamente.", true);
            }
        });
    }

    private void abrirModalPerfil() {
        revisaoPerfil++;
        campoNomePerfil.setText(perfilLocal.obterNome());
        campoNomePerfil.setSelection(campoNomePerfil.getText().length());
        fotoRascunho = perfilLocal.obterFoto();
        definirStatusPerfil(getString(R.string.perfil_status_padrao), false);
        botaoSalvarPerfil.setEnabled(true);
        renderizarPreviaPerfil();
        overlayModalPerfil.setVisibility(View.VISIBLE);
        campoNomePerfil.requestFocus();
    }

    private void fecharModalPerfil() {
        revisaoPerfil++;
        ocultarTeclado();
        overlayModalPerfil.setVisibility(View.GONE);
    }

    private void renderizarPreviaPerfil() {
        PerfilUsuario rascunho = new PerfilUsuario(
                campoNomePerfil.getText().toString().trim(),
                fotoRascunho
        );
        GerenciadorPerfil.renderizarAvatar(
                containerPreviaAvatar,
                imagemPreviaAvatar,
                textoPreviaAvatar,
                rascunho,
                "Você"
        );
    }

    private void renderizarPerfilNoLobby() {
        GerenciadorPerfil.renderizarAvatar(
                containerAvatarLobby,
                imagemAvatarLobby,
                textoAvatarLobby,
                perfilLocal,
                "Você"
        );
        textoNomePerfilLobby.setText(
                perfilLocal.possuiNome() ? perfilLocal.obterNome() : getString(R.string.perfil_padrao_lobby)
        );
    }

    private void definirStatusPerfil(String mensagem, boolean erro) {
        textoStatusPerfil.setText(mensagem);
        textoStatusPerfil.setTextColor(ContextCompat.getColor(
                this,
                erro ? R.color.cor_perigo_suave : R.color.cor_texto_secundario
        ));
    }

    // ---------------------------------------------------------------------
    // Controles da Chamada, Tela Cheia e Menu de Volume Independente
    // ---------------------------------------------------------------------

    private void configurarAcoesChamada() {
        botaoCopiarSalaAtiva.setOnClickListener(v -> copiarTextoAreaTransferencia(
                textoCodigoSalaAtiva.getText().toString()
        ));

        botaoAbrirConfiguracoes.setOnClickListener(v -> abrirModalConfiguracoes());

        quadroRemoto.setOnClickListener(v -> {
            if (menuContextoRemoto.getVisibility() == View.VISIBLE) {
                fecharMenuVolumeRemoto();
                return;
            }
            if (!emTelaCheia) {
                quadroExpandido = (quadroExpandido == 1) ? 0 : 1;
                aplicarLayoutPalco();
            }
        });

        quadroRemoto.setOnLongClickListener(v -> {
            alternarMenuVolumeRemoto();
            return true;
        });

        botaoVolumeRemoto.setOnClickListener(v -> alternarMenuVolumeRemoto());

        menuContextoRemoto.setOnClickListener(v -> {
            // Impede que toques no menu de volume redimensionem o quadro
        });

        quadroLocal.setOnClickListener(v -> {
            fecharMenuVolumeRemoto();
            if (!emTelaCheia) {
                quadroExpandido = (quadroExpandido == 2) ? 0 : 2;
                aplicarLayoutPalco();
            }
        });

        botaoTelaCheia.setOnClickListener(v -> definirTelaCheia(!emTelaCheia));

        barraVolumeVoz.setOnSeekBarChangeListener(new OuvinteSeekBarSimples() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progresso, boolean doUsuario) {
                textoValorVolumeVoz.setText(String.format(Locale.ROOT, "%d%%", progresso));
                motorChamada.definirVolumeVozRemota(progresso);
            }
        });

        barraVolumeTela.setOnSeekBarChangeListener(new OuvinteSeekBarSimples() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progresso, boolean doUsuario) {
                textoValorVolumeTela.setText(String.format(Locale.ROOT, "%d%%", progresso));
                motorChamada.definirVolumeTransmissaoRemota(progresso);
            }
        });

        botaoMudo.setOnClickListener(v -> {
            boolean mutado = motorChamada.alternarMudoMicrofone();
            atualizarVisualBotaoMudo(mutado);
        });

        botaoCamera.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                motorChamada.alternarCamera();
            } else {
                lancadorPermissaoCamera.launch(Manifest.permission.CAMERA);
            }
        });

        botaoCompartilhar.setOnClickListener(v -> abrirModalCompartilhar());

        botaoOuvir.setOnClickListener(v -> {
            boolean silenciado = motorChamada.alternarSilenciarAudioRecebido();
            atualizarVisualBotaoOuvir(silenciado);
        });

        botaoSair.setOnClickListener(v -> {
            if (emTelaCheia) {
                definirTelaCheia(false);
            }
            motorChamada.sairDaChamada();
        });
    }

    private void alternarMenuVolumeRemoto() {
        boolean mostrar = menuContextoRemoto.getVisibility() != View.VISIBLE;
        menuContextoRemoto.setVisibility(mostrar ? View.VISIBLE : View.GONE);
    }

    private void fecharMenuVolumeRemoto() {
        menuContextoRemoto.setVisibility(View.GONE);
    }

    private void definirTelaCheia(boolean ativar) {
        emTelaCheia = ativar;
        WindowInsetsControllerCompat controladorBarras =
                WindowCompat.getInsetsController(getWindow(), containerRaiz);
        if (controladorBarras != null) {
            if (ativar) {
                controladorBarras.setSystemBarsBehavior(
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
                controladorBarras.hide(WindowInsetsCompat.Type.systemBars());
            } else {
                controladorBarras.show(WindowInsetsCompat.Type.systemBars());
            }
        }

        botaoTelaCheia.setText(ativar ? R.string.botao_sair_tela_cheia : R.string.botao_tela_cheia);
        videoRemoto.setScalingType(
                (ativar || telaRemotaEmCompartilhamento)
                        ? RendererCommon.ScalingType.SCALE_ASPECT_FIT
                        : RendererCommon.ScalingType.SCALE_ASPECT_FILL
        );
        ViewCompat.requestApplyInsets(containerRaiz);
        aplicarLayoutPalco();
    }

    private void aplicarLayoutPalco() {
        if (palcoVideos == null) {
            return;
        }
        boolean modoPaisagem = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;

        if (emTelaCheia) {
            cabecalhoChamada.setVisibility(View.GONE);
            barraControles.setVisibility(View.GONE);
            espacadorQuadros.setVisibility(View.GONE);
            quadroLocal.setVisibility(View.GONE);
            palcoVideos.setPadding(0, 0, 0, 0);

            LinearLayout.LayoutParams paramsRemoto = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            quadroRemoto.setLayoutParams(paramsRemoto);
            return;
        }

        cabecalhoChamada.setVisibility(View.VISIBLE);
        barraControles.setVisibility(View.VISIBLE);
        espacadorQuadros.setVisibility(View.VISIBLE);
        quadroLocal.setVisibility(View.VISIBLE);
        palcoVideos.setPadding(dpParaPx(14), 0, dpParaPx(14), dpParaPx(10));
        palcoVideos.setOrientation(modoPaisagem ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

        LinearLayout.LayoutParams paramsRemoto;
        LinearLayout.LayoutParams paramsLocal;

        if (modoPaisagem) {
            if (quadroExpandido == 1) {
                paramsRemoto = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                paramsLocal = new LinearLayout.LayoutParams(dpParaPx(165), dpParaPx(150), 0f);
                paramsLocal.gravity = Gravity.BOTTOM;
            } else if (quadroExpandido == 2) {
                paramsRemoto = new LinearLayout.LayoutParams(dpParaPx(165), dpParaPx(150), 0f);
                paramsRemoto.gravity = Gravity.BOTTOM;
                paramsLocal = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            } else {
                paramsRemoto = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                paramsLocal = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            }
        } else {
            if (quadroExpandido == 1) {
                paramsRemoto = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
                paramsLocal = new LinearLayout.LayoutParams(dpParaPx(165), dpParaPx(132), 0f);
                paramsLocal.gravity = Gravity.END;
            } else if (quadroExpandido == 2) {
                paramsRemoto = new LinearLayout.LayoutParams(dpParaPx(165), dpParaPx(132), 0f);
                paramsRemoto.gravity = Gravity.END;
                paramsLocal = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            } else {
                paramsRemoto = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
                paramsLocal = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            }
        }

        quadroRemoto.setLayoutParams(paramsRemoto);
        quadroLocal.setLayoutParams(paramsLocal);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        aplicarLayoutPalco();
    }

    // ---------------------------------------------------------------------
    // Modal de Compartilhamento de Tela
    // ---------------------------------------------------------------------

    private void configurarAcoesCompartilhamento() {
        botaoFecharCompartilhar.setOnClickListener(v -> fecharModalCompartilhar());
        overlayModalCompartilhar.setOnClickListener(v -> fecharModalCompartilhar());
        cardModalCompartilhar.setOnClickListener(v -> {
        });

        botaoFonteTelaAparelho.setOnClickListener(v -> {
            incluirAudioPendenteProjecao = caixaIncluirAudioSistema.isChecked();
            fecharModalCompartilhar();
            MediaProjectionManager gerenciadorProjecao =
                    (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (gerenciadorProjecao != null) {
                lancadorCapturaTela.launch(gerenciadorProjecao.createScreenCaptureIntent());
            } else {
                aoMostrarToast("Nao foi possivel iniciar o compartilhamento de tela.", "error", 4000);
            }
        });

        botaoPararCompartilhamento.setOnClickListener(v -> {
            fecharModalCompartilhar();
            motorChamada.pararCompartilhamentoTela();
        });
    }

    private void abrirModalCompartilhar() {
        fecharMenuVolumeRemoto();
        caixaIncluirAudioSistema.setChecked(motorChamada.estaCompartilhandoAudioTela());
        botaoPararCompartilhamento.setVisibility(
                motorChamada.estaCompartilhandoTela() ? View.VISIBLE : View.GONE
        );
        overlayModalCompartilhar.setVisibility(View.VISIBLE);
    }

    private void fecharModalCompartilhar() {
        overlayModalCompartilhar.setVisibility(View.GONE);
    }

    // ---------------------------------------------------------------------
    // Modal de Configurações de Áudio e Voz
    // ---------------------------------------------------------------------

    private void configurarAcoesConfiguracoes() {
        botaoFecharConfiguracoes.setOnClickListener(v -> fecharModalConfiguracoes());
        overlayModalConfiguracoes.setOnClickListener(v -> fecharModalConfiguracoes());
        cardModalConfiguracoes.setOnClickListener(v -> {
        });

        seletorMicrofone.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int posicao, long id) {
                if (sincronizandoControlesConfiguracao || posicao < 0 || posicao >= listaMicrofonesAtual.size()) {
                    return;
                }
                String idEscolhido = listaMicrofonesAtual.get(posicao).id;
                String idAtual = motorChamada.obterGerenciadorDispositivos().obterIdMicrofoneSelecionado();
                if (!idEscolhido.equals(idAtual)) {
                    if (!motorChamada.trocarMicrofone(idEscolhido)) {
                        atualizarListasDispositivosModal();
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        seletorSaidaAudio.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int posicao, long id) {
                if (sincronizandoControlesConfiguracao || posicao < 0 || posicao >= listaSaidasAtual.size()) {
                    return;
                }
                String idEscolhido = listaSaidasAtual.get(posicao).id;
                String idAtual = motorChamada.obterGerenciadorDispositivos().obterIdSaidaSelecionada();
                if (!idEscolhido.equals(idAtual)) {
                    boolean trocou = motorChamada.obterGerenciadorDispositivos().aplicarSaidaAudio(idEscolhido);
                    if (!trocou) {
                        aoMostrarToast("Nao foi possivel trocar a saida de audio.", "error", 4000);
                        atualizarListasDispositivosModal();
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        barraVolumeEntrada.setOnSeekBarChangeListener(new OuvinteSeekBarSimples() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progresso, boolean doUsuario) {
                textoValorVolumeEntrada.setText(String.format(Locale.ROOT, "%d%%", progresso));
                motorChamada.definirVolumeEntradaMicrofone(progresso);
            }
        });

        barraVolumeSaida.setOnSeekBarChangeListener(new OuvinteSeekBarSimples() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progresso, boolean doUsuario) {
                textoValorVolumeSaida.setText(String.format(Locale.ROOT, "%d%%", progresso));
                motorChamada.definirVolumeGeralSaida(progresso);
            }
        });

        View.OnClickListener ouvinteOpcaoVoz = v -> aplicarAlteracaoProcessamentoAudio();
        caixaReducaoRuido.setOnClickListener(ouvinteOpcaoVoz);
        caixaCancelamentoEco.setOnClickListener(ouvinteOpcaoVoz);
        caixaGanhoAutomatico.setOnClickListener(ouvinteOpcaoVoz);
    }

    private void abrirModalConfiguracoes() {
        fecharMenuVolumeRemoto();
        textoStatusProcessamentoAudio.setText("");
        atualizarListasDispositivosModal();
        sincronizarEstadosProcessamentoAudio();
        overlayModalConfiguracoes.setVisibility(View.VISIBLE);
    }

    private void fecharModalConfiguracoes() {
        overlayModalConfiguracoes.setVisibility(View.GONE);
    }

    private void atualizarListasDispositivosModal() {
        sincronizandoControlesConfiguracao = true;
        try {
            GerenciadorDispositivosAudio gerenciador = motorChamada.obterGerenciadorDispositivos();
            listaMicrofonesAtual = gerenciador.listarMicrofones();
            listaSaidasAtual = gerenciador.listarSaidasAudio();

            ArrayAdapter<GerenciadorDispositivosAudio.OpcaoDispositivo> adaptadorMic =
                    new ArrayAdapter<>(this, R.layout.item_seletor_audio, listaMicrofonesAtual);
            adaptadorMic.setDropDownViewResource(R.layout.item_seletor_audio_dropdown);
            seletorMicrofone.setAdapter(adaptadorMic);

            String micSelecionado = gerenciador.obterIdMicrofoneSelecionado();
            for (int i = 0; i < listaMicrofonesAtual.size(); i++) {
                if (listaMicrofonesAtual.get(i).id.equals(micSelecionado)) {
                    seletorMicrofone.setSelection(i, false);
                    break;
                }
            }

            ArrayAdapter<GerenciadorDispositivosAudio.OpcaoDispositivo> adaptadorSaida =
                    new ArrayAdapter<>(this, R.layout.item_seletor_audio, listaSaidasAtual);
            adaptadorSaida.setDropDownViewResource(R.layout.item_seletor_audio_dropdown);
            seletorSaidaAudio.setAdapter(adaptadorSaida);

            String saidaSelecionada = gerenciador.obterIdSaidaSelecionada();
            for (int i = 0; i < listaSaidasAtual.size(); i++) {
                if (listaSaidasAtual.get(i).id.equals(saidaSelecionada)) {
                    seletorSaidaAudio.setSelection(i, false);
                    break;
                }
            }
        } finally {
            sincronizandoControlesConfiguracao = false;
        }
    }

    private void aplicarAlteracaoProcessamentoAudio() {
        textoStatusProcessamentoAudio.setText("Aplicando ao microfone…");
        textoStatusProcessamentoAudio.setTextColor(ContextCompat.getColor(this, R.color.cor_texto_secundario));

        boolean pediuRuido = caixaReducaoRuido.isChecked();
        boolean pediuEco = caixaCancelamentoEco.isChecked();
        boolean pediuGanho = caixaGanhoAutomatico.isChecked();

        boolean aplicou = motorChamada.aplicarOpcoesProcessamentoAudio(pediuRuido, pediuEco, pediuGanho);
        if (aplicou) {
            textoStatusProcessamentoAudio.setText("Opções aplicadas ao microfone e à voz enviada.");
            textoStatusProcessamentoAudio.setTextColor(ContextCompat.getColor(this, R.color.cor_texto_secundario));
        } else {
            textoStatusProcessamentoAudio.setText(
                    "O dispositivo não aplicou essa opção. Mantivemos a configuração anterior."
            );
            textoStatusProcessamentoAudio.setTextColor(ContextCompat.getColor(this, R.color.cor_perigo_suave));
        }
        sincronizarEstadosProcessamentoAudio();
    }

    private void sincronizarEstadosProcessamentoAudio() {
        atualizarLinhaOpcaoVoz(
                caixaReducaoRuido,
                textoEstadoReducaoRuido,
                motorChamada.opcaoProcessamentoDisponivel("noiseSuppression"),
                motorChamada.obterReducaoRuido()
        );
        atualizarLinhaOpcaoVoz(
                caixaCancelamentoEco,
                textoEstadoCancelamentoEco,
                motorChamada.opcaoProcessamentoDisponivel("echoCancellation"),
                motorChamada.obterCancelamentoEco()
        );
        atualizarLinhaOpcaoVoz(
                caixaGanhoAutomatico,
                textoEstadoGanhoAutomatico,
                motorChamada.opcaoProcessamentoDisponivel("autoGainControl"),
                motorChamada.obterGanhoAutomatico()
        );
    }

    private void atualizarLinhaOpcaoVoz(
            CheckBox caixa,
            TextView rotuloEstado,
            boolean disponivel,
            boolean ativo
    ) {
        caixa.setEnabled(disponivel);
        if (disponivel) {
            caixa.setChecked(ativo);
            rotuloEstado.setText(ativo ? R.string.estado_ativo : R.string.estado_desligado);
            rotuloEstado.setTextColor(ContextCompat.getColor(
                    this,
                    ativo ? R.color.cor_ao_vivo : R.color.cor_texto_apagado
            ));
        } else {
            rotuloEstado.setText(R.string.estado_indisponivel);
            rotuloEstado.setTextColor(ContextCompat.getColor(this, R.color.cor_texto_apagado));
        }
    }

    // ---------------------------------------------------------------------
    // Retornos do MotorChamadaWebRtc (OuvinteInterfaceChamada)
    // ---------------------------------------------------------------------

    @Override
    public void aoMudarStatusLobby(String mensagem, String tom) {
        textoStatusLobby.setText(mensagem != null ? mensagem : "");
        int cor;
        if ("error".equals(tom)) {
            cor = R.color.cor_perigo_suave;
        } else if ("ok".equals(tom)) {
            cor = R.color.cor_ao_vivo;
        } else {
            cor = R.color.cor_texto_secundario;
        }
        textoStatusLobby.setTextColor(ContextCompat.getColor(this, cor));
    }

    @Override
    public void aoMudarEstadoEntrando(boolean entrando) {
        botaoCriarSala.setEnabled(!entrando);
        botaoMostrarEntrar.setEnabled(!entrando);
        botaoEntrarSala.setEnabled(!entrando);
        float opacidade = entrando ? 0.5f : 1.0f;
        botaoCriarSala.setAlpha(opacidade);
        botaoMostrarEntrar.setAlpha(opacidade);
        botaoEntrarSala.setAlpha(opacidade);
    }

    @Override
    public void aoIrParaTelaChamada(String nomeSala) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        telaLobby.setVisibility(View.GONE);
        telaChamada.setVisibility(View.VISIBLE);
        textoRotuloSala.setText(nomeSala);
        textoCodigoSalaAtiva.setText(nomeSala);
        aplicarLayoutPalco();
    }

    @Override
    public void aoIrParaTelaLobby() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (emTelaCheia) {
            definirTelaCheia(false);
        }
        quadroExpandido = 0;
        possuiVideoRemotoAtivo = false;
        telaRemotaEmCompartilhamento = false;
        falandoLocalVisivel = false;
        falandoRemotoVisivel = false;

        fecharMenuVolumeRemoto();
        fecharModalConfiguracoes();
        fecharModalCompartilhar();

        atualizarVisualBotaoMudo(false);
        atualizarVisualBotaoCamera(false);
        atualizarVisualBotaoCompartilhar(false);
        atualizarVisualBotaoOuvir(false);
        atualizarBordaQuadroLocal();
        atualizarBordaQuadroRemoto();
        aoAtualizarEstadoVideoLocal(false, false, false);
        aoAtualizarEstadoVideoRemoto(false, false);

        telaChamada.setVisibility(View.GONE);
        telaLobby.setVisibility(View.VISIBLE);
        aoMudarEstadoEntrando(false);
        aoMudarStatusLobby("", "info");
        redefinirModoLobby();
        renderizarPerfilNoLobby();
        aoAtualizarPerfis(PerfilUsuario.vazio(), false, false);
    }

    @Override
    public void aoMudarEstadoConexao(String estado) {
        pararAnimacaoPulsoConexao();
        if ("connected".equals(estado)) {
            pontoConexao.setBackgroundResource(R.drawable.ponto_conectado);
            textoStatusChamada.setText(R.string.status_conectado);
        } else if ("lost".equals(estado)) {
            pontoConexao.setBackgroundResource(R.drawable.ponto_perdido);
            textoStatusChamada.setText(R.string.status_conexao_instavel);
        } else {
            pontoConexao.setBackgroundResource(R.drawable.ponto_conectando);
            textoStatusChamada.setText(R.string.status_conectando);
            iniciarAnimacaoPulsoConexao();
        }
    }

    @Override
    public void aoAtualizarTextoStatusChamada(String texto) {
        textoStatusChamada.setText(texto != null ? texto : "");
    }

    @Override
    public void aoAtualizarTextoVazioRemoto(String texto) {
        textoVazioRemoto.setText(texto != null ? texto : "");
    }

    @Override
    public void aoMostrarToast(String mensagem, String tom, int duracaoMs) {
        if (temporizadorToast != null) {
            manipuladorUi.removeCallbacks(temporizadorToast);
        }
        textoToast.setText(mensagem != null ? mensagem : "");
        boolean erro = "error".equals(tom);
        textoToast.setBackgroundResource(erro ? R.drawable.fundo_toast_erro : R.drawable.fundo_toast_info);
        textoToast.setTextColor(ContextCompat.getColor(
                this,
                erro ? R.color.cor_perigo_suave : R.color.cor_texto
        ));
        textoToast.setVisibility(View.VISIBLE);

        temporizadorToast = () -> textoToast.setVisibility(View.GONE);
        manipuladorUi.postDelayed(temporizadorToast, Math.max(1500, duracaoMs));
    }

    @Override
    public void aoAtualizarPerfis(
            PerfilUsuario perfilRemoto,
            boolean compartilhandoTelaLocal,
            boolean compartilhandoTelaRemota
    ) {
        GerenciadorPerfil.renderizarAvatar(
                containerAvatarLocal,
                imagemAvatarLocal,
                textoAvatarLocal,
                perfilLocal,
                "Você"
        );
        GerenciadorPerfil.renderizarAvatar(
                containerAvatarRemoto,
                imagemAvatarRemoto,
                textoAvatarRemoto,
                perfilRemoto,
                "Sua companhia"
        );

        textoRotuloQuadroLocal.setText(
                GerenciadorPerfil.obterRotuloQuadroLocal(perfilLocal, compartilhandoTelaLocal)
        );
        textoRotuloQuadroRemoto.setText(
                GerenciadorPerfil.obterRotuloQuadroRemoto(perfilRemoto, compartilhandoTelaRemota)
        );
    }

    @Override
    public void aoAtualizarEstadoVideoLocal(boolean possuiVideo, boolean compartilhandoTela, boolean cameraAtiva) {
        videoLocal.setVisibility(possuiVideo ? View.VISIBLE : View.GONE);
        containerAvatarLocal.setVisibility(possuiVideo ? View.GONE : View.VISIBLE);
        atualizarVisualBotaoCamera(cameraAtiva);
        atualizarVisualBotaoCompartilhar(compartilhandoTela);
        textoRotuloQuadroLocal.setText(
                GerenciadorPerfil.obterRotuloQuadroLocal(perfilLocal, compartilhandoTela)
        );
    }

    @Override
    public void aoAtualizarEstadoVideoRemoto(boolean possuiVideo, boolean compartilhandoTela) {
        possuiVideoRemotoAtivo = possuiVideo;
        telaRemotaEmCompartilhamento = compartilhandoTela;
        videoRemoto.setVisibility(possuiVideo ? View.VISIBLE : View.GONE);
        containerAvatarRemoto.setVisibility(possuiVideo ? View.GONE : View.VISIBLE);
        textoVazioRemoto.setVisibility(possuiVideo ? View.GONE : View.VISIBLE);
        videoRemoto.setScalingType(
                (emTelaCheia || compartilhandoTela)
                        ? RendererCommon.ScalingType.SCALE_ASPECT_FIT
                        : RendererCommon.ScalingType.SCALE_ASPECT_FILL
        );
    }

    @Override
    public void aoMudarFalaLocal(boolean falando) {
        falandoLocalVisivel = falando;
        atualizarBordaQuadroLocal();
    }

    @Override
    public void aoMudarFalaRemota(boolean falando) {
        falandoRemotoVisivel = falando;
        atualizarBordaQuadroRemoto();
    }

    @Override
    public PerfilUsuario obterPerfilLocalAtual() {
        return perfilLocal;
    }

    // ---------------------------------------------------------------------
    // Auxiliares Visuais da Barra de Controles e Quadros
    // ---------------------------------------------------------------------

    private void atualizarVisualBotaoMudo(boolean mutado) {
        botaoMudo.setBackgroundResource(
                mutado ? R.drawable.fundo_botao_dock_perigo : R.drawable.fundo_botao_dock_inativo
        );
        iconeMudo.setImageResource(mutado ? R.drawable.ic_mic_desligado : R.drawable.ic_mic_ligado);
        rotuloMudo.setTextColor(ContextCompat.getColor(
                this,
                mutado ? R.color.cor_perigo_suave : R.color.cor_texto_secundario
        ));
    }

    private void atualizarVisualBotaoCamera(boolean ligada) {
        botaoCamera.setBackgroundResource(
                ligada ? R.drawable.fundo_botao_dock_ativo : R.drawable.fundo_botao_dock_inativo
        );
        iconeCamera.setImageResource(ligada ? R.drawable.ic_cam_ligada : R.drawable.ic_cam_desligada);
        rotuloCamera.setTextColor(ContextCompat.getColor(
                this,
                ligada ? R.color.cor_destaque : R.color.cor_texto_secundario
        ));
    }

    private void atualizarVisualBotaoCompartilhar(boolean compartilhando) {
        botaoCompartilhar.setBackgroundResource(
                compartilhando ? R.drawable.fundo_botao_dock_ativo : R.drawable.fundo_botao_dock_inativo
        );
        rotuloCompartilhar.setTextColor(ContextCompat.getColor(
                this,
                compartilhando ? R.color.cor_destaque : R.color.cor_texto_secundario
        ));
    }

    private void atualizarVisualBotaoOuvir(boolean silenciado) {
        botaoOuvir.setBackgroundResource(
                silenciado ? R.drawable.fundo_botao_dock_perigo : R.drawable.fundo_botao_dock_inativo
        );
        iconeOuvir.setImageResource(
                silenciado ? R.drawable.ic_ouvir_desligado : R.drawable.ic_ouvir_ligado
        );
        rotuloOuvir.setText(silenciado ? R.string.controle_silenciado : R.string.controle_ouvir);
        rotuloOuvir.setTextColor(ContextCompat.getColor(
                this,
                silenciado ? R.color.cor_perigo_suave : R.color.cor_texto_secundario
        ));
    }

    private void atualizarBordaQuadroLocal() {
        quadroLocal.setBackgroundResource(
                falandoLocalVisivel
                        ? R.drawable.fundo_quadro_local_falando
                        : R.drawable.fundo_quadro_local_padrao
        );
    }

    private void atualizarBordaQuadroRemoto() {
        quadroRemoto.setBackgroundResource(
                falandoRemotoVisivel
                        ? R.drawable.fundo_quadro_falando
                        : R.drawable.fundo_quadro_padrao
        );
    }

    private void iniciarAnimacaoPulsoConexao() {
        animadorPulsoConexao = ObjectAnimator.ofFloat(pontoConexao, View.ALPHA, 1f, 0.3f);
        animadorPulsoConexao.setDuration(700L);
        animadorPulsoConexao.setRepeatCount(ValueAnimator.INFINITE);
        animadorPulsoConexao.setRepeatMode(ValueAnimator.REVERSE);
        animadorPulsoConexao.start();
    }

    private void pararAnimacaoPulsoConexao() {
        if (animadorPulsoConexao != null) {
            animadorPulsoConexao.cancel();
            animadorPulsoConexao = null;
        }
        pontoConexao.setAlpha(1f);
    }

    // ---------------------------------------------------------------------
    // Navegação (Botão Voltar = Escape do Desktop) e Utilitários
    // ---------------------------------------------------------------------

    private void configurarBotaoVoltar() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (overlayModalPerfil.getVisibility() == View.VISIBLE) {
                    fecharModalPerfil();
                } else if (overlayModalConfiguracoes.getVisibility() == View.VISIBLE) {
                    fecharModalConfiguracoes();
                } else if (overlayModalCompartilhar.getVisibility() == View.VISIBLE) {
                    fecharModalCompartilhar();
                } else if (menuContextoRemoto.getVisibility() == View.VISIBLE) {
                    fecharMenuVolumeRemoto();
                } else if (emTelaCheia) {
                    definirTelaCheia(false);
                } else if (telaLobby.getVisibility() == View.VISIBLE
                        && formularioEntrarSala.getVisibility() == View.VISIBLE) {
                    redefinirModoLobby();
                } else if (telaChamada.getVisibility() == View.VISIBLE) {
                    moveTaskToBack(true);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });
    }

    private void copiarTextoAreaTransferencia(String texto) {
        if (texto == null || texto.isEmpty()) {
            aoMostrarToast("Nao foi possivel copiar o codigo.", "error", 4000);
            return;
        }
        try {
            ClipboardManager areaTransferencia =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (areaTransferencia != null) {
                areaTransferencia.setPrimaryClip(ClipData.newPlainText("Código da sala Unme", texto));
                aoMostrarToast("Codigo copiado.", "info", 2500);
            } else {
                aoMostrarToast("Nao foi possivel copiar o codigo.", "error", 4000);
            }
        } catch (Exception erro) {
            aoMostrarToast("Nao foi possivel copiar o codigo.", "error", 4000);
        }
    }

    private void mostrarTeclado(View vista) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(vista, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void ocultarTeclado() {
        View foco = getCurrentFocus();
        if (foco != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(foco.getWindowToken(), 0);
            }
        }
    }

    private int dpParaPx(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        ));
    }

    @Override
    protected void onDestroy() {
        pararAnimacaoPulsoConexao();
        if (temporizadorToast != null) {
            manipuladorUi.removeCallbacks(temporizadorToast);
        }
        executorFundo.shutdownNow();
        if (motorChamada != null) {
            motorChamada.liberarTudo();
        }
        super.onDestroy();
    }

    private abstract static class OuvinteSeekBarSimples implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
