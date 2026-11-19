package com.example.unme;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ConfiguracaoApp {

    public static final String URL_SERVIDOR_SINALIZACAO = "https://minidc-vn9a.onrender.com";

    public static final List<String> SERVIDORES_ICE = Collections.unmodifiableList(
            Arrays.asList(
                    "stun:stun.l.google.com:19302",
                    "stun:stun1.l.google.com:19302"
            )
    );

    public static final int TAMANHO_POOL_CANDIDATOS_ICE = 10;

    private ConfiguracaoApp() {
    }
}
