package com.example.unme;

import android.annotation.SuppressLint;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.os.Build;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class CapturadorAudioSistema {

    private AudioRecord gravadorSistema;
    private Thread threadCaptura;
    private volatile boolean capturando = false;
    private final Object travaBuffer = new Object();
    private byte[] bufferCircular;
    private int cabecaLeitura = 0;
    private int caudaEscrita = 0;
    private int bytesDisponiveis = 0;
    private int taxaAtual = 48000;
    private int canaisAtuais = 1;

    @SuppressLint("MissingPermission")
    public boolean iniciar(MediaProjection projecaoMidia, int taxaAmostragem, int quantidadeCanais) {
        parar();
        if (projecaoMidia == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false;
        }

        this.taxaAtual = taxaAmostragem > 0 ? taxaAmostragem : 48000;
        this.canaisAtuais = quantidadeCanais == 2 ? 2 : 1;
        int mascaraCanais = canaisAtuais == 2
                ? AudioFormat.CHANNEL_IN_STEREO
                : AudioFormat.CHANNEL_IN_MONO;

        try {
            AudioPlaybackCaptureConfiguration configuracaoCaptura =
                    new AudioPlaybackCaptureConfiguration.Builder(projecaoMidia)
                            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                            .addMatchingUsage(AudioAttributes.USAGE_GAME)
                            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                            .build();

            AudioFormat formatoAudio = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(taxaAtual)
                    .setChannelMask(mascaraCanais)
                    .build();

            int tamanhoMinimo = AudioRecord.getMinBufferSize(
                    taxaAtual,
                    mascaraCanais,
                    AudioFormat.ENCODING_PCM_16BIT
            );
            if (tamanhoMinimo <= 0) {
                tamanhoMinimo = taxaAtual * canaisAtuais * 2 / 10;
            }
            int tamanhoBufferGravador = Math.max(tamanhoMinimo * 2, 8192);

            gravadorSistema = new AudioRecord.Builder()
                    .setAudioFormat(formatoAudio)
                    .setBufferSizeInBytes(tamanhoBufferGravador)
                    .setAudioPlaybackCaptureConfig(configuracaoCaptura)
                    .build();

            if (gravadorSistema.getState() != AudioRecord.STATE_INITIALIZED) {
                gravadorSistema.release();
                gravadorSistema = null;
                return false;
            }

            synchronized (travaBuffer) {
                bufferCircular = new byte[taxaAtual * canaisAtuais * 2]; // 1 segundo de janela máxima
                cabecaLeitura = 0;
                caudaEscrita = 0;
                bytesDisponiveis = 0;
            }

            capturando = true;
            gravadorSistema.startRecording();
            threadCaptura = new Thread(this::executarLoopCaptura, "UnmeAudioSistema");
            threadCaptura.start();
            return true;
        } catch (Throwable erro) {
            parar();
            return false;
        }
    }

    private void executarLoopCaptura() {
        byte[] blocoLeitura = new byte[1920];
        while (capturando && gravadorSistema != null) {
            int lidos;
            try {
                lidos = gravadorSistema.read(blocoLeitura, 0, blocoLeitura.length);
            } catch (Throwable erro) {
                break;
            }
            if (lidos > 0) {
                escreverNoBuffer(blocoLeitura, lidos);
            }
        }
    }

    private void escreverNoBuffer(byte[] dados, int quantidade) {
        synchronized (travaBuffer) {
            if (bufferCircular == null || bufferCircular.length == 0) {
                return;
            }
            int capacidade = bufferCircular.length;
            for (int i = 0; i < quantidade; i++) {
                bufferCircular[caudaEscrita] = dados[i];
                caudaEscrita = (caudaEscrita + 1) % capacidade;
                if (bytesDisponiveis < capacidade) {
                    bytesDisponiveis++;
                } else {
                    cabecaLeitura = (cabecaLeitura + 1) % capacidade;
                }
            }
        }
    }

    public void misturarNoBufferGravado(ByteBuffer bufferDestino, int quantidadeBytes) {
        if (!capturando || bufferDestino == null || quantidadeBytes <= 1) {
            return;
        }
        byte[] temporario = new byte[quantidadeBytes];
        int lidos = 0;
        synchronized (travaBuffer) {
            if (bytesDisponiveis < quantidadeBytes || bufferCircular == null) {
                return;
            }
            int capacidade = bufferCircular.length;
            while (lidos < quantidadeBytes) {
                temporario[lidos++] = bufferCircular[cabecaLeitura];
                cabecaLeitura = (cabecaLeitura + 1) % capacidade;
                bytesDisponiveis--;
            }
        }

        ByteOrder ordemOriginal = bufferDestino.order();
        bufferDestino.order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer bufferSistema = ByteBuffer.wrap(temporario).order(ByteOrder.LITTLE_ENDIAN);
        int posicaoInicial = bufferDestino.position();
        int totalAmostras = quantidadeBytes / 2;

        for (int i = 0; i < totalAmostras; i++) {
            int deslocamento = posicaoInicial + (i * 2);
            short amostraVoz = bufferDestino.getShort(deslocamento);
            short amostraSistema = bufferSistema.getShort(i * 2);
            int soma = amostraVoz + amostraSistema;
            if (soma > Short.MAX_VALUE) {
                soma = Short.MAX_VALUE;
            } else if (soma < Short.MIN_VALUE) {
                soma = Short.MIN_VALUE;
            }
            bufferDestino.putShort(deslocamento, (short) soma);
        }
        bufferDestino.order(ordemOriginal);
    }

    public boolean estaCapturando() {
        return capturando;
    }

    public void parar() {
        capturando = false;
        Thread threadAntiga = threadCaptura;
        threadCaptura = null;
        if (gravadorSistema != null) {
            try {
                gravadorSistema.stop();
            } catch (Throwable ignorado) {
            }
            try {
                gravadorSistema.release();
            } catch (Throwable ignorado) {
            }
            gravadorSistema = null;
        }
        if (threadAntiga != null) {
            try {
                threadAntiga.join(300);
            } catch (InterruptedException ignorado) {
                Thread.currentThread().interrupt();
            }
        }
        synchronized (travaBuffer) {
            bufferCircular = null;
            bytesDisponiveis = 0;
            cabecaLeitura = 0;
            caudaEscrita = 0;
        }
    }
}
