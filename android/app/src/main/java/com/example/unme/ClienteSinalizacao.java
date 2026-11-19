package com.example.unme;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import io.socket.client.IO;
import io.socket.client.Socket;

public class ClienteSinalizacao {

    public static final long TEMPO_LIMITE_VERIFICACAO_MS = 15000L;

    public static final class ResultadoVerificacaoSala {
        public final String nomeSala;
        public final boolean existe;
        public final boolean disponivel;
        public final boolean erro;

        public ResultadoVerificacaoSala(String nomeSala, boolean existe, boolean disponivel, boolean erro) {
            this.nomeSala = nomeSala != null ? nomeSala : "";
            this.existe = existe;
            this.disponivel = disponivel;
            this.erro = erro;
        }
    }

    public interface RetornoVerificacaoSala {
        void aoReceberResultado(ResultadoVerificacaoSala resultado);
    }

    public interface OuvinteSinalizacao {
        void aoConectar();

        void aoDesconectar();

        void aoErroConexao(String mensagemErro);

        void aoErroEntrarSala(String mensagem);

        void aoSalaCheia(String nomeSala);

        void aoEntrarNaSala(String nomeSala, boolean primeiroParticipante);

        void aoParceiroEntrar();

        void aoParceiroSair();

        void aoReceberSinal(JSONObject dadosSinal);
    }

    private final Handler manipuladorPrincipal = new Handler(Looper.getMainLooper());
    private OuvinteSinalizacao ouvinte;
    private Socket soquete;
    private RetornoVerificacaoSala retornoVerificacaoPendente;
    private Runnable temporizadorVerificacao;

    public void definirOuvinte(OuvinteSinalizacao ouvinte) {
        this.ouvinte = ouvinte;
    }

    public boolean conectar(String urlServidor) {
        desconectarInterno(false);
        try {
            IO.Options opcoes = new IO.Options();
            opcoes.transports = new String[]{"websocket", "polling"};
            opcoes.reconnection = true;
            opcoes.reconnectionAttempts = Integer.MAX_VALUE;
            opcoes.reconnectionDelay = 1000;
            opcoes.reconnectionDelayMax = 5000;
            opcoes.forceNew = true;

            soquete = IO.socket(urlServidor, opcoes);
            registrarEventos(soquete);
            soquete.connect();
            return true;
        } catch (URISyntaxException erroUri) {
            executarNaThreadPrincipal(() -> {
                if (ouvinte != null) {
                    ouvinte.aoErroConexao(erroUri.getMessage());
                }
            });
            return false;
        }
    }

    private void registrarEventos(Socket soqueteAtual) {
        soqueteAtual.on("room-check-result", argumentos -> executarNaThreadPrincipal(() -> {
            if (soquete != soqueteAtual) return;
            JSONObject json = extrairJson(argumentos);
            ResultadoVerificacaoSala resultado;
            if (json != null) {
                resultado = new ResultadoVerificacaoSala(
                        json.optString("roomName", ""),
                        json.optBoolean("exists", false),
                        json.optBoolean("available", false),
                        false
                );
            } else {
                resultado = new ResultadoVerificacaoSala("", false, false, true);
            }
            concluirVerificacaoSala(resultado);
        }));

        soqueteAtual.on(Socket.EVENT_CONNECT, argumentos -> executarNaThreadPrincipal(() -> {
            if (soquete != soqueteAtual) return;
            if (ouvinte != null) {
                ouvinte.aoConectar();
            }
        }));

        soqueteAtual.on(Socket.EVENT_DISCONNECT, argumentos -> executarNaThreadPrincipal(() -> {
            if (soquete != soqueteAtual) return;
            if (ouvinte != null) {
                ouvinte.aoDesconectar();
            }
        }));

        soqueteAtual.on(Socket.EVENT_CONNECT_ERROR, argumentos -> executarNaThreadPrincipal(() -> {
            if (soquete != soqueteAtual) return;
            String detalhe = extrairMensagemErro(argumentos);
            if (ouvinte != null) {
                ouvinte.aoErroConexao(detalhe);
            }
        }));

        soqueteAtual.on("join-error", argumentos -> executarNaThreadPrincipal(() -> {
            if (soquete != soqueteAtual) return;
            String mensagem = extrairTexto(argumentos, "Não foi possível entrar na sala.");
            if (ouvinte != null) {
                ouvinte.aoErroEntrarSala(mensagem);
            }
        }));

        soqueteAtual.on("room-full", argumentos -> executarNaThreadPrincipal(() -> {
            if (soquete != soqueteAtual) return;
            String nomeSala = extrairTexto(argumentos, "");
            if (ouvinte != null) {
                ouvinte.aoSalaCheia(nomeSala);
            }
        }));

        soqueteAtual.on("joined-room", argumentos -> executarNaThreadPrincipal(() -> {
            if (soquete != soqueteAtual) return;
            JSONObject json = extrairJson(argumentos);
            if (json == null) return;
            String nomeSala = json.optString("roomName", "");
            boolean primeiroParticipante = json.optBoolean("isFirstPeer", false);
            if (ouvinte != null) {
                ouvinte.aoEntrarNaSala(nomeSala, primeiroParticipante);
            }
        }));

        soqueteAtual.on("peer-joined", argumentos -> executarNaThreadPrincipal(() -> {
            if (soquete != soqueteAtual) return;
            if (ouvinte != null) {
                ouvinte.aoParceiroEntrar();
            }
        }));

        soqueteAtual.on("peer-left", argumentos -> executarNaThreadPrincipal(() -> {
            if (soquete != soqueteAtual) return;
            if (ouvinte != null) {
                ouvinte.aoParceiroSair();
            }
        }));

        soqueteAtual.on("signal", argumentos -> executarNaThreadPrincipal(() -> {
            if (soquete != soqueteAtual) return;
            JSONObject dados = extrairJson(argumentos);
            if (dados != null && ouvinte != null) {
                ouvinte.aoReceberSinal(dados);
            }
        }));
    }

    public void verificarSala(String nomeSala, RetornoVerificacaoSala retorno) {
        cancelarTemporizadorVerificacao();
        retornoVerificacaoPendente = retorno;

        if (soquete == null) {
            concluirVerificacaoSala(new ResultadoVerificacaoSala(nomeSala, false, false, true));
            return;
        }

        temporizadorVerificacao = () -> concluirVerificacaoSala(
                new ResultadoVerificacaoSala(nomeSala, false, false, true)
        );
        manipuladorPrincipal.postDelayed(temporizadorVerificacao, TEMPO_LIMITE_VERIFICACAO_MS);
        soquete.emit("check-room", nomeSala);
    }

    public void cancelarVerificacaoPendenteComErro() {
        concluirVerificacaoSala(new ResultadoVerificacaoSala("", false, false, true));
    }

    private void concluirVerificacaoSala(ResultadoVerificacaoSala resultado) {
        cancelarTemporizadorVerificacao();
        RetornoVerificacaoSala retorno = retornoVerificacaoPendente;
        retornoVerificacaoPendente = null;
        if (retorno != null) {
            retorno.aoReceberResultado(resultado);
        }
    }

    private void cancelarTemporizadorVerificacao() {
        if (temporizadorVerificacao != null) {
            manipuladorPrincipal.removeCallbacks(temporizadorVerificacao);
            temporizadorVerificacao = null;
        }
    }

    public void entrarNaSala(String nomeSala) {
        if (soquete == null || !GerenciadorSala.validarNomeSala(nomeSala)) {
            return;
        }
        soquete.emit("join-room", GerenciadorSala.normalizarNomeSala(nomeSala));
    }

    public void enviarSinal(String nomeSala, JSONObject dados) {
        if (soquete == null || nomeSala == null || dados == null) {
            return;
        }
        String tipo = dados.optString("type", "");
        if (!GerenciadorSala.validarTipoSinal(tipo)) {
            return;
        }
        if (dados.toString().getBytes(StandardCharsets.UTF_8).length > GerenciadorSala.TAMANHO_MAXIMO_PAYLOAD_SINAL_BYTES) {
            return;
        }
        try {
            JSONObject pacote = new JSONObject();
            pacote.put("roomName", nomeSala);
            pacote.put("data", dados);
            soquete.emit("signal", pacote);
        } catch (JSONException ignorado) {
        }
    }

    public void sairDaSala() {
        if (soquete != null) {
            soquete.emit("leave-room");
        }
    }

    public void desconectar() {
        desconectarInterno(true);
    }

    private void desconectarInterno(boolean falharVerificacao) {
        if (falharVerificacao && retornoVerificacaoPendente != null) {
            concluirVerificacaoSala(new ResultadoVerificacaoSala("", false, false, true));
        } else {
            cancelarTemporizadorVerificacao();
            retornoVerificacaoPendente = null;
        }
        if (soquete != null) {
            soquete.off();
            soquete.disconnect();
            soquete = null;
        }
    }

    private void executarNaThreadPrincipal(Runnable tarefa) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            tarefa.run();
        } else {
            manipuladorPrincipal.post(tarefa);
        }
    }

    private static JSONObject extrairJson(Object[] argumentos) {
        if (argumentos == null || argumentos.length == 0 || argumentos[0] == null) {
            return null;
        }
        if (argumentos[0] instanceof JSONObject) {
            return (JSONObject) argumentos[0];
        }
        if (argumentos[0] instanceof String) {
            try {
                return new JSONObject((String) argumentos[0]);
            } catch (JSONException ignorado) {
                return null;
            }
        }
        return null;
    }

    private static String extrairTexto(Object[] argumentos, String padrao) {
        if (argumentos == null || argumentos.length == 0 || argumentos[0] == null) {
            return padrao;
        }
        return String.valueOf(argumentos[0]);
    }

    private static String extrairMensagemErro(Object[] argumentos) {
        if (argumentos == null || argumentos.length == 0 || argumentos[0] == null) {
            return "verifique sua internet.";
        }
        Object primeiro = argumentos[0];
        if (primeiro instanceof Throwable) {
            String msg = ((Throwable) primeiro).getMessage();
            return (msg != null && !msg.isEmpty()) ? msg : "verifique sua internet.";
        }
        if (primeiro instanceof JSONObject) {
            String msg = ((JSONObject) primeiro).optString("message", "");
            return !msg.isEmpty() ? msg : "verifique sua internet.";
        }
        String texto = String.valueOf(primeiro);
        return !texto.isEmpty() ? texto : "verifique sua internet.";
    }
}
