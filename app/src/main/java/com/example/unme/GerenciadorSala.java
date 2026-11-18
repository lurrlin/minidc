package com.example.unme;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class GerenciadorSala {

    public static final String ALFABETO_CODIGO =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%&*+-=";
    public static final int TAMANHO_CODIGO_GERADO = 16;
    public static final int COMPRIMENTO_MAXIMO_SALA = 40;
    public static final int TAMANHO_MAXIMO_PAYLOAD_SINAL_BYTES = 128 * 1024;

    public static final Set<String> TIPOS_SINAL_VALIDOS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "offer",
                    "answer",
                    "candidate",
                    "ready",
                    "screen-share-start",
                    "screen-share-stop",
                    "video-state",
                    "renegotiate"
            ))
    );

    private static final SecureRandom GERADOR_ALEATORIO = new SecureRandom();

    private GerenciadorSala() {
    }

    public static String gerarCodigoSala() {
        StringBuilder construtor = new StringBuilder(TAMANHO_CODIGO_GERADO);
        int tamanhoAlfabeto = ALFABETO_CODIGO.length();
        for (int indice = 0; indice < TAMANHO_CODIGO_GERADO; indice++) {
            long valorSemSinal = Integer.toUnsignedLong(GERADOR_ALEATORIO.nextInt());
            int posicao = (int) (valorSemSinal % tamanhoAlfabeto);
            construtor.append(ALFABETO_CODIGO.charAt(posicao));
        }
        return construtor.toString();
    }

    public static boolean validarNomeSala(String valor) {
        if (valor == null) {
            return false;
        }
        String limpo = valor.trim();
        return !limpo.isEmpty() && limpo.length() <= COMPRIMENTO_MAXIMO_SALA;
    }

    public static String normalizarNomeSala(String valor) {
        return valor == null ? "" : valor.trim();
    }

    public static boolean validarTipoSinal(String tipo) {
        return tipo != null && TIPOS_SINAL_VALIDOS.contains(tipo);
    }
}
