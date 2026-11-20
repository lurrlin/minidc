package com.example.unme;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TesteContratoCompartilhado {

    private static File encontrarPastaProtocolo() {
        File atual = new File("").getAbsoluteFile();
        while (atual != null) {
            File candidato = new File(atual, "protocol");
            if (new File(candidato, "constants.json").isFile()) {
                return candidato;
            }
            atual = atual.getParentFile();
        }
        throw new IllegalStateException("Pasta protocol/ nao encontrada a partir do diretorio atual.");
    }

    private static JSONObject lerJsonProtocolo(String caminhoRelativo) throws Exception {
        File arquivo = new File(encontrarPastaProtocolo(), caminhoRelativo);
        String conteudo = new String(Files.readAllBytes(arquivo.toPath()), StandardCharsets.UTF_8);
        return new JSONObject(conteudo);
    }

    @Test
    public void constantesDeRedeSalaPerfilEMidiaSeguemProtocolo() throws Exception {
        JSONObject constantes = lerJsonProtocolo("constants.json");
        JSONObject rede = constantes.getJSONObject("network");
        JSONObject sala = constantes.getJSONObject("room");
        JSONObject sinalizacao = constantes.getJSONObject("signaling");
        JSONObject perfil = constantes.getJSONObject("profile");
        JSONObject midia = constantes.getJSONObject("media");
        JSONObject audio = midia.getJSONObject("audio");
        JSONObject canalAudioTela = midia.getJSONObject("screenAudioDataChannel");

        assertEquals(rede.getString("defaultSignalingServerUrl"), ConfiguracaoApp.URL_SERVIDOR_SINALIZACAO);
        assertEquals(rede.getInt("iceCandidatePoolSize"), ConfiguracaoApp.TAMANHO_POOL_CANDIDATOS_ICE);

        JSONArray stunEsperado = rede.getJSONArray("defaultStunServers");
        List<String> listaStun = new ArrayList<>();
        for (int i = 0; i < stunEsperado.length(); i++) {
            listaStun.add(stunEsperado.getString(i));
        }
        assertEquals(listaStun, ConfiguracaoApp.SERVIDORES_ICE);

        assertEquals(sala.getString("generatedCodeAlphabet"), GerenciadorSala.ALFABETO_CODIGO);
        assertEquals(sala.getInt("generatedCodeLength"), GerenciadorSala.TAMANHO_CODIGO_GERADO);
        assertEquals(sala.getInt("maxLength"), GerenciadorSala.COMPRIMENTO_MAXIMO_SALA);
        assertEquals(sinalizacao.getInt("maxSignalPayloadBytes"), GerenciadorSala.TAMANHO_MAXIMO_PAYLOAD_SINAL_BYTES);

        assertEquals(perfil.getString("storageKey"), GerenciadorPerfil.CHAVE_PERFIL);
        assertEquals(perfil.getInt("maxNameLength"), GerenciadorPerfil.COMPRIMENTO_MAXIMO_NOME);
        assertEquals(perfil.getInt("maxPhotoDataUrlLength"), GerenciadorPerfil.TAMANHO_MAXIMO_FOTO);
        assertEquals(perfil.getInt("avatarDimensionPx"), GerenciadorPerfil.DIMENSAO_AVATAR_PX);

        assertEquals(audio.getInt("speakingThreshold"), MisturadorAudio.LIMIAR_FALA);
        assertEquals(canalAudioTela.getInt("sampleRateHz"), CodecAdpcm.TAXA_AMOSTRAGEM_PADRAO_HZ);
        assertEquals(canalAudioTela.getInt("headerBytes"), CodecAdpcm.TAMANHO_CABECALHO_BYTES);
    }

    @Test
    public void fixturesDeSalaESinaisAtendemAoContrato() throws Exception {
        JSONObject fixtures = lerJsonProtocolo("fixtures/room-validation.json");

        JSONArray salasValidas = fixtures.getJSONArray("validRoomNames");
        for (int i = 0; i < salasValidas.length(); i++) {
            JSONObject caso = salasValidas.getJSONObject(i);
            String entrada = caso.getString("input");
            assertTrue(GerenciadorSala.validarNomeSala(entrada));
            assertEquals(caso.getString("normalized"), GerenciadorSala.normalizarNomeSala(entrada));
        }

        JSONArray salasInvalidas = fixtures.getJSONArray("invalidRoomNames");
        for (int i = 0; i < salasInvalidas.length(); i++) {
            String entrada = salasInvalidas.isNull(i) ? null : salasInvalidas.getString(i);
            assertFalse(GerenciadorSala.validarNomeSala(entrada));
        }

        JSONArray sinaisValidos = fixtures.getJSONArray("validSignalTypes");
        for (int i = 0; i < sinaisValidos.length(); i++) {
            assertTrue(GerenciadorSala.validarTipoSinal(sinaisValidos.getString(i)));
        }

        JSONArray sinaisInvalidos = fixtures.getJSONArray("invalidSignalTypes");
        for (int i = 0; i < sinaisInvalidos.length(); i++) {
            assertFalse(GerenciadorSala.validarTipoSinal(sinaisInvalidos.getString(i)));
        }
    }

    @Test
    public void fixturesDePerfilAtendemAoContrato() throws Exception {
        JSONObject fixtures = lerJsonProtocolo("fixtures/profile-validation.json");

        JSONArray casosNormalizacao = fixtures.getJSONArray("normalizationCases");
        for (int i = 0; i < casosNormalizacao.length(); i++) {
            JSONObject caso = casosNormalizacao.getJSONObject(i);
            JSONObject entrada = caso.getJSONObject("input");
            JSONObject esperado = caso.getJSONObject("expected");
            PerfilUsuario resultado = GerenciadorPerfil.normalizar(
                    entrada.optString("name", ""),
                    entrada.optString("photo", "")
            );
            assertEquals(caso.getString("label"), esperado.getString("name"), resultado.obterNome());
            assertEquals(caso.getString("label"), esperado.getString("photo"), resultado.obterFoto());
        }

        JSONArray casosIniciais = fixtures.getJSONArray("initialsCases");
        for (int i = 0; i < casosIniciais.length(); i++) {
            JSONObject caso = casosIniciais.getJSONObject(i);
            assertEquals(
                    caso.getString("expected"),
                    GerenciadorPerfil.obterIniciais(caso.getString("name"), caso.getString("fallback"))
            );
        }

        JSONArray casosRotulos = fixtures.getJSONArray("tileLabelCases");
        for (int i = 0; i < casosRotulos.length(); i++) {
            JSONObject caso = casosRotulos.getJSONObject(i);
            PerfilUsuario perfil = new PerfilUsuario(caso.getString("name"), "");
            boolean compartilhando = caso.getBoolean("sharingScreen");
            String obtido = caso.getBoolean("isLocal")
                    ? GerenciadorPerfil.obterRotuloQuadroLocal(perfil, compartilhando)
                    : GerenciadorPerfil.obterRotuloQuadroRemoto(perfil, compartilhando);
            assertEquals(caso.getString("expected"), obtido);
        }
    }

    @Test
    public void fixturesDeMisturadorAudioAtendemAoContrato() throws Exception {
        JSONObject fixtures = lerJsonProtocolo("fixtures/audio-mixer-cases.json");

        JSONArray clampingCases = fixtures.getJSONArray("clampingCases");
        for (int i = 0; i < clampingCases.length(); i++) {
            JSONObject caso = clampingCases.getJSONObject(i);
            assertEquals(
                    caso.getDouble("expected"),
                    MisturadorAudio.limitarPercentual(caso.getDouble("input")),
                    0.0001
            );
        }

        JSONArray cenarios = fixtures.getJSONArray("mixerScenarios");
        for (int i = 0; i < cenarios.length(); i++) {
            JSONObject cenario = cenarios.getJSONObject(i);
            MisturadorAudio misturador = new MisturadorAudio();
            misturador.definirVolumeCanal(MisturadorAudio.CANAL_VOZ, cenario.getDouble("voicePercent"));
            misturador.definirVolumeCanal(MisturadorAudio.CANAL_TELA, cenario.getDouble("screenPercent"));
            misturador.definirVolumeGeral(cenario.getDouble("masterPercent"));
            misturador.definirSilenciadoTotal(cenario.getBoolean("deafened"));

            assertEquals(
                    cenario.getString("name"),
                    cenario.getDouble("expectedEffectiveVoice"),
                    misturador.obterGanhoEfetivoCanal(MisturadorAudio.CANAL_VOZ),
                    0.0001
            );
            assertEquals(
                    cenario.getString("name"),
                    cenario.getDouble("expectedEffectiveScreen"),
                    misturador.obterGanhoEfetivoCanal(MisturadorAudio.CANAL_TELA),
                    0.0001
            );
        }
    }
}
