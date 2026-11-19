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

    public interface OuvinteQuadroAudioSistema {
        void aoCapturarQuadro(short[] amostrasPcm, int quantidadeAmostras, int taxaAmostragem);
    }

    private AudioRecord gravadorSistema;
    private Thread threadCaptura;
    private volatile boolean capturando = false;
    private OuvinteQuadroAudioSistema ouvinteQuadro;
    private int taxaAtual = CodecAdpcm.TAXA_AMOSTRAGEM_PADRAO_HZ;

    @SuppressLint("MissingPermission")
    public boolean iniciar(MediaProjection projecaoMidia, OuvinteQuadroAudioSistema ouvinte) {
        parar();
        if (projecaoMidia == null || ouvinte == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false;
        }

        this.ouvinteQuadro = ouvinte;
        this.taxaAtual = CodecAdpcm.TAXA_AMOSTRAGEM_PADRAO_HZ;

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
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build();

            int tamanhoMinimo = AudioRecord.getMinBufferSize(
                    taxaAtual,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );
            if (tamanhoMinimo <= 0) {
                tamanhoMinimo = CodecAdpcm.AMOSTRAS_POR_QUADRO_20MS * 4;
            }
            int tamanhoBufferGravador = Math.max(tamanhoMinimo * 2, 4096);

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
        final int bytesPorQuadro = CodecAdpcm.AMOSTRAS_POR_QUADRO_20MS * 2;
        byte[] blocoBytes = new byte[bytesPorQuadro];
        short[] amostras = new short[CodecAdpcm.AMOSTRAS_POR_QUADRO_20MS];

        while (capturando && gravadorSistema != null) {
            int acumulado = 0;
            while (acumulado < bytesPorQuadro && capturando && gravadorSistema != null) {
                int lidos;
                try {
                    lidos = gravadorSistema.read(blocoBytes, acumulado, bytesPorQuadro - acumulado);
                } catch (Throwable erro) {
                    lidos = -1;
                }
                if (lidos <= 0) {
                    break;
                }
                acumulado += lidos;
            }

            if (acumulado >= 2 && ouvinteQuadro != null) {
                int totalAmostras = acumulado / 2;
                ByteBuffer.wrap(blocoBytes, 0, totalAmostras * 2)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .asShortBuffer()
                        .get(amostras, 0, totalAmostras);
                ouvinteQuadro.aoCapturarQuadro(amostras, totalAmostras, taxaAtual);
            } else if (acumulado <= 0) {
                break;
            }
        }
    }

    public boolean estaCapturando() {
        return capturando;
    }

    public void parar() {
        capturando = false;
        ouvinteQuadro = null;
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
    }
}
