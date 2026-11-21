package com.example.unme;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MisturadorAudio {

    public static final String CANAL_VOZ = "voz";
    public static final String CANAL_TELA = "tela";
    public static final int LIMIAR_FALA = 14;

    private double percentualGeral = 100.0;
    private double ganhoVoz = 1.0;
    private double ganhoTela = 1.0;
    private double ganhoEntrada = 1.0;
    private boolean silenciadoTotal = false;

    public static double limitarPercentual(double percentual) {
        if (Double.isNaN(percentual)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(200.0, percentual));
    }

    public synchronized void definirVolumeGeral(double percentual) {
        this.percentualGeral = limitarPercentual(percentual);
    }

    public synchronized double obterVolumeGeralPercentual() {
        return percentualGeral;
    }

    public synchronized double obterGanhoGeral() {
        return silenciadoTotal ? 0.0 : (percentualGeral / 100.0);
    }

    public synchronized void definirVolumeCanal(String canal, double percentual) {
        double ganho = limitarPercentual(percentual) / 100.0;
        if (CANAL_VOZ.equals(canal) || "voice".equals(canal)) {
            this.ganhoVoz = ganho;
        } else if (CANAL_TELA.equals(canal) || "screen".equals(canal)) {
            this.ganhoTela = ganho;
        } else {
            throw new IllegalArgumentException("Canal de áudio inválido.");
        }
    }

    public synchronized double obterGanhoCanal(String canal) {
        if (CANAL_VOZ.equals(canal) || "voice".equals(canal)) {
            return ganhoVoz;
        }
        if (CANAL_TELA.equals(canal) || "screen".equals(canal)) {
            return ganhoTela;
        }
        throw new IllegalArgumentException("Canal de áudio inválido.");
    }

    public synchronized double obterGanhoEfetivoCanal(String canal) {
        double ganhoIndividual = obterGanhoCanal(canal);
        if (silenciadoTotal) {
            return 0.0;
        }
        return ganhoIndividual * (percentualGeral / 100.0);
    }

    public synchronized void definirSilenciadoTotal(boolean valor) {
        this.silenciadoTotal = valor;
    }

    public synchronized boolean estaSilenciadoTotal() {
        return silenciadoTotal;
    }

    public synchronized void definirVolumeEntrada(double percentual) {
        this.ganhoEntrada = limitarPercentual(percentual) / 100.0;
    }

    public synchronized double obterGanhoEntrada() {
        return ganhoEntrada;
    }

    public static void aplicarGanhoPcm16(ByteBuffer bufferPcm, int quantidadeBytes, double ganho) {
        if (bufferPcm == null || quantidadeBytes <= 1 || Math.abs(ganho - 1.0) < 0.0001) {
            return;
        }
        ByteOrder ordemOriginal = bufferPcm.order();
        bufferPcm.order(ByteOrder.LITTLE_ENDIAN);
        int posicaoInicial = bufferPcm.position();
        int totalAmostras = quantidadeBytes / 2;

        if (ganho <= 0.00001) {
            for (int indice = 0; indice < quantidadeBytes; indice++) {
                bufferPcm.put(posicaoInicial + indice, (byte) 0);
            }
            bufferPcm.order(ordemOriginal);
            return;
        }

        for (int indice = 0; indice < totalAmostras; indice++) {
            int deslocamento = posicaoInicial + (indice * 2);
            short amostra = bufferPcm.getShort(deslocamento);
            int amplificada = (int) Math.round(amostra * ganho);
            if (amplificada > Short.MAX_VALUE) {
                amplificada = Short.MAX_VALUE;
            } else if (amplificada < Short.MIN_VALUE) {
                amplificada = Short.MIN_VALUE;
            }
            bufferPcm.putShort(deslocamento, (short) amplificada);
        }
        bufferPcm.order(ordemOriginal);
    }

    public static void aplicarGanhoPcm16Array(byte[] bytesPcm, int quantidadeBytes, double ganho) {
        if (bytesPcm == null || quantidadeBytes <= 1) {
            return;
        }
        int limite = Math.min(bytesPcm.length, quantidadeBytes);
        ByteBuffer buffer = ByteBuffer.wrap(bytesPcm, 0, limite);
        aplicarGanhoPcm16(buffer, limite, ganho);
    }

    public static double calcularNivelAudio(ByteBuffer bufferPcm, int quantidadeBytes) {
        if (bufferPcm == null || quantidadeBytes <= 1) {
            return 0.0;
        }
        ByteOrder ordemOriginal = bufferPcm.order();
        bufferPcm.order(ByteOrder.LITTLE_ENDIAN);
        int posicaoInicial = bufferPcm.position();
        int totalAmostras = quantidadeBytes / 2;
        if (totalAmostras == 0) {
            bufferPcm.order(ordemOriginal);
            return 0.0;
        }

        double somaQuadrados = 0.0;
        for (int indice = 0; indice < totalAmostras; indice++) {
            short amostra = bufferPcm.getShort(posicaoInicial + (indice * 2));
            double normalizada = amostra / 32768.0;
            somaQuadrados += normalizada * normalizada;
        }
        bufferPcm.order(ordemOriginal);
        double rms = Math.sqrt(somaQuadrados / totalAmostras);
        // Converte RMS para escala 0..255 equivalente ao getByteFrequencyData do Web Audio API
        return Math.min(255.0, rms * 650.0);
    }

    public static boolean detectarFala(double nivelSuavizado) {
        return nivelSuavizado > LIMIAR_FALA;
    }
}
