package com.example.unme;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class CodecAdpcm {

    public static final int VERSAO_PACOTE = 1;
    public static final int ID_CODEC_IMA = 1;
    public static final int TAMANHO_CABECALHO_BYTES = 12;
    public static final int TAXA_AMOSTRAGEM_PADRAO_HZ = 24000;
    public static final int AMOSTRAS_POR_QUADRO_20MS = 480;

    private static final int[] TABELA_INDICE = {
            -1, -1, -1, -1, 2, 4, 6, 8,
            -1, -1, -1, -1, 2, 4, 6, 8
    };

    private static final int[] TABELA_PASSO = {
            7, 8, 9, 10, 11, 12, 13, 14, 16, 17,
            19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
            50, 55, 60, 66, 73, 80, 88, 97, 107, 118,
            130, 143, 157, 173, 190, 209, 230, 253, 279, 307,
            337, 371, 408, 449, 494, 544, 598, 658, 724, 796,
            876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066,
            2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358,
            5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899,
            15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767
    };

    public static final class EstadoCodificador {
        public int preditor = 0;
        public int indicePasso = 0;

        public void reiniciar() {
            preditor = 0;
            indicePasso = 0;
        }
    }

    public static final class PacoteDecodificado {
        public final int taxaAmostragem;
        public final int sequencia;
        public final short[] amostras;

        public PacoteDecodificado(int taxaAmostragem, int sequencia, short[] amostras) {
            this.taxaAmostragem = taxaAmostragem;
            this.sequencia = sequencia;
            this.amostras = amostras;
        }
    }

    private CodecAdpcm() {
    }

    public static byte[] codificar(
            short[] amostras,
            int quantidadeAmostras,
            int taxaAmostragem,
            int sequencia,
            EstadoCodificador estado
    ) {
        if (amostras == null || quantidadeAmostras <= 0 || estado == null) {
            return null;
        }
        int limite = Math.min(amostras.length, quantidadeAmostras);
        int bytesPayload = (limite + 1) / 2;
        byte[] pacote = new byte[TAMANHO_CABECALHO_BYTES + bytesPayload];
        ByteBuffer buffer = ByteBuffer.wrap(pacote).order(ByteOrder.LITTLE_ENDIAN);

        buffer.put(0, (byte) VERSAO_PACOTE);
        buffer.put(1, (byte) ID_CODEC_IMA);
        buffer.putShort(2, (short) Math.max(80, Math.min(960, taxaAmostragem / 100)));
        buffer.putShort(4, (short) (sequencia & 0xFFFF));
        buffer.putShort(6, (short) limitarPcm(estado.preditor));
        buffer.put(8, (byte) limitarIndice(estado.indicePasso));
        buffer.put(9, (byte) 1);
        buffer.putShort(10, (short) (limite & 0xFFFF));

        int preditor = limitarPcm(estado.preditor);
        int indicePasso = limitarIndice(estado.indicePasso);

        for (int i = 0; i < limite; i++) {
            int passo = TABELA_PASSO[indicePasso];
            int diferenca = amostras[i] - preditor;
            int nibble = 0;
            if (diferenca < 0) {
                nibble = 8;
                diferenca = -diferenca;
            }
            int diferencaPredita = passo >> 3;
            if (diferenca >= passo) {
                nibble |= 4;
                diferenca -= passo;
                diferencaPredita += passo;
            }
            passo >>= 1;
            if (diferenca >= passo) {
                nibble |= 2;
                diferenca -= passo;
                diferencaPredita += passo;
            }
            passo >>= 1;
            if (diferenca >= passo) {
                nibble |= 1;
                diferencaPredita += passo;
            }

            if ((nibble & 8) != 0) {
                preditor -= diferencaPredita;
            } else {
                preditor += diferencaPredita;
            }
            preditor = limitarPcm(preditor);
            indicePasso = limitarIndice(indicePasso + TABELA_INDICE[nibble]);

            int posicaoByte = TAMANHO_CABECALHO_BYTES + (i >> 1);
            if ((i & 1) == 0) {
                pacote[posicaoByte] = (byte) (nibble & 0x0F);
            } else {
                pacote[posicaoByte] |= (byte) ((nibble & 0x0F) << 4);
            }
        }

        estado.preditor = preditor;
        estado.indicePasso = indicePasso;
        return pacote;
    }

    public static PacoteDecodificado decodificar(byte[] pacote) {
        if (pacote == null || pacote.length <= TAMANHO_CABECALHO_BYTES) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(pacote).order(ByteOrder.LITTLE_ENDIAN);
        int versao = buffer.get(0) & 0xFF;
        int codecId = buffer.get(1) & 0xFF;
        int taxaDiv100 = buffer.getShort(2) & 0xFFFF;
        int sequencia = buffer.getShort(4) & 0xFFFF;
        int preditor = buffer.getShort(6);
        int indicePasso = limitarIndice(buffer.get(8) & 0xFF);
        int canais = buffer.get(9) & 0xFF;
        int quantidadeAmostras = buffer.getShort(10) & 0xFFFF;

        if (versao != VERSAO_PACOTE || codecId != ID_CODEC_IMA || canais != 1 || quantidadeAmostras <= 0) {
            return null;
        }
        int bytesNecessarios = TAMANHO_CABECALHO_BYTES + ((quantidadeAmostras + 1) / 2);
        if (pacote.length < bytesNecessarios) {
            return null;
        }

        int taxaAmostragem = Math.max(8000, Math.min(96000, taxaDiv100 * 100));
        short[] saida = new short[quantidadeAmostras];

        for (int i = 0; i < quantidadeAmostras; i++) {
            int byteCodificado = pacote[TAMANHO_CABECALHO_BYTES + (i >> 1)] & 0xFF;
            int nibble = ((i & 1) == 0) ? (byteCodificado & 0x0F) : ((byteCodificado >> 4) & 0x0F);

            int passo = TABELA_PASSO[indicePasso];
            int diferenca = passo >> 3;
            if ((nibble & 4) != 0) diferenca += passo;
            if ((nibble & 2) != 0) diferenca += (passo >> 1);
            if ((nibble & 1) != 0) diferenca += (passo >> 2);

            if ((nibble & 8) != 0) {
                preditor -= diferenca;
            } else {
                preditor += diferenca;
            }
            preditor = limitarPcm(preditor);
            indicePasso = limitarIndice(indicePasso + TABELA_INDICE[nibble]);
            saida[i] = (short) preditor;
        }

        return new PacoteDecodificado(taxaAmostragem, sequencia, saida);
    }

    private static int limitarPcm(int valor) {
        if (valor > Short.MAX_VALUE) return Short.MAX_VALUE;
        if (valor < Short.MIN_VALUE) return Short.MIN_VALUE;
        return valor;
    }

    private static int limitarIndice(int indice) {
        if (indice < 0) return 0;
        if (indice >= TABELA_PASSO.length) return TABELA_PASSO.length - 1;
        return indice;
    }
}
