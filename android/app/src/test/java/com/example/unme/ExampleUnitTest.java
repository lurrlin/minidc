package com.example.unme;

import org.junit.Test;
import org.webrtc.CameraEnumerationAndroid;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ExampleUnitTest {

    @Test
    public void geracaoEValidacaoDeSalasSegueRegrasDoDesktop() {
        String codigo1 = GerenciadorSala.gerarCodigoSala();
        String codigo2 = GerenciadorSala.gerarCodigoSala();

        assertEquals(16, codigo1.length());
        assertEquals(16, codigo2.length());
        assertNotEquals(codigo1, codigo2);

        for (int i = 0; i < codigo1.length(); i++) {
            assertTrue(GerenciadorSala.ALFABETO_CODIGO.indexOf(codigo1.charAt(i)) >= 0);
        }

        assertFalse(GerenciadorSala.validarNomeSala(null));
        assertFalse(GerenciadorSala.validarNomeSala(""));
        assertFalse(GerenciadorSala.validarNomeSala("    "));
        assertFalse(GerenciadorSala.validarNomeSala("x".repeat(41)));

        assertTrue(GerenciadorSala.validarNomeSala("sala-1"));
        assertTrue(GerenciadorSala.validarNomeSala("  " + "a".repeat(40) + "  "));

        assertTrue(GerenciadorSala.validarTipoSinal("offer"));
        assertTrue(GerenciadorSala.validarTipoSinal("answer"));
        assertTrue(GerenciadorSala.validarTipoSinal("candidate"));
        assertTrue(GerenciadorSala.validarTipoSinal("ready"));
        assertTrue(GerenciadorSala.validarTipoSinal("screen-share-start"));
        assertTrue(GerenciadorSala.validarTipoSinal("screen-share-stop"));
        assertTrue(GerenciadorSala.validarTipoSinal("video-state"));
        assertTrue(GerenciadorSala.validarTipoSinal("renegotiate"));
        assertFalse(GerenciadorSala.validarTipoSinal("profile"));
        assertFalse(GerenciadorSala.validarTipoSinal("unknown"));
    }

    @Test
    public void normalizacaoIniciaisERotulosDePerfilPreservamRegras() {
        String nomeLongo = "  " + "Alexia ".repeat(10) + " ";
        String fotoValida = "data:image/jpeg;base64,QUJDRA==";
        String fotoInvalida = "http://exemplo.com/foto.jpg";
        String fotoGigante = "data:image/jpeg;base64," + "A".repeat(60000);

        PerfilUsuario perfilValido = GerenciadorPerfil.normalizar(nomeLongo, fotoValida);
        assertEquals(40, perfilValido.obterNome().length());
        assertEquals(fotoValida, perfilValido.obterFoto());

        assertEquals("", GerenciadorPerfil.normalizar("Bia", fotoInvalida).obterFoto());
        assertEquals("", GerenciadorPerfil.normalizar("Bia", fotoGigante).obterFoto());

        assertEquals("AS", GerenciadorPerfil.obterIniciais("Alex Silva Terceiro", "Você"));
        assertEquals("V", GerenciadorPerfil.obterIniciais("", "Você"));
        assertEquals("SC", GerenciadorPerfil.obterIniciais("   ", "Sua companhia"));

        PerfilUsuario vazio = PerfilUsuario.vazio();
        PerfilUsuario comNome = new PerfilUsuario("Alex", "");

        assertEquals("Você", GerenciadorPerfil.obterRotuloQuadroLocal(vazio, false));
        assertEquals("Sua tela", GerenciadorPerfil.obterRotuloQuadroLocal(vazio, true));
        assertEquals("Alex · você", GerenciadorPerfil.obterRotuloQuadroLocal(comNome, false));
        assertEquals("Alex · tela", GerenciadorPerfil.obterRotuloQuadroLocal(comNome, true));

        assertEquals("Sua companhia", GerenciadorPerfil.obterRotuloQuadroRemoto(vazio, false));
        assertEquals("Sua companhia · tela", GerenciadorPerfil.obterRotuloQuadroRemoto(vazio, true));
        assertEquals("Alex", GerenciadorPerfil.obterRotuloQuadroRemoto(comNome, false));
        assertEquals("Alex · tela", GerenciadorPerfil.obterRotuloQuadroRemoto(comNome, true));
    }

    @Test
    public void protocoloCanalDadosPerfilCompativelComDesktop() {
        PerfilUsuario enviado = new PerfilUsuario("Bia", "data:image/webp;base64,UklGRg==");
        String json = GerenciadorPerfil.criarPayloadCanalDados(enviado);

        PerfilUsuario recebido = GerenciadorPerfil.interpretarPayloadCanalDados(json);
        assertNotNull(recebido);
        assertEquals("Bia", recebido.obterNome());
        assertEquals("data:image/webp;base64,UklGRg==", recebido.obterFoto());

        assertNull(GerenciadorPerfil.interpretarPayloadCanalDados("{\"type\":\"outro\"}"));
        assertNull(GerenciadorPerfil.interpretarPayloadCanalDados("x".repeat(61000)));
    }

    @Test
    public void misturadorAudioIndependenteGanho200EDeafen() {
        MisturadorAudio misturador = new MisturadorAudio();

        misturador.definirVolumeCanal(MisturadorAudio.CANAL_VOZ, 200);
        misturador.definirVolumeCanal(MisturadorAudio.CANAL_TELA, 35);
        misturador.definirVolumeGeral(100);

        assertEquals(2.0, misturador.obterGanhoEfetivoCanal(MisturadorAudio.CANAL_VOZ), 0.0001);
        assertEquals(0.35, misturador.obterGanhoEfetivoCanal(MisturadorAudio.CANAL_TELA), 0.0001);

        // Ativar deafen zera ambos os canais e impede vazamento ao mexer nos sliders
        misturador.definirSilenciadoTotal(true);
        assertEquals(0.0, misturador.obterGanhoEfetivoCanal(MisturadorAudio.CANAL_VOZ), 0.0001);
        assertEquals(0.0, misturador.obterGanhoEfetivoCanal(MisturadorAudio.CANAL_TELA), 0.0001);

        misturador.definirVolumeCanal(MisturadorAudio.CANAL_TELA, 65);
        misturador.definirVolumeGeral(160);
        assertEquals(0.0, misturador.obterGanhoEfetivoCanal(MisturadorAudio.CANAL_VOZ), 0.0001);
        assertEquals(0.0, misturador.obterGanhoEfetivoCanal(MisturadorAudio.CANAL_TELA), 0.0001);

        // Desativar deafen restaura o volume geral e preserva os níveis individuais
        misturador.definirSilenciadoTotal(false);
        assertEquals(2.0, misturador.obterGanhoCanal(MisturadorAudio.CANAL_VOZ), 0.0001);
        assertEquals(0.65, misturador.obterGanhoCanal(MisturadorAudio.CANAL_TELA), 0.0001);
        assertEquals(3.2, misturador.obterGanhoEfetivoCanal(MisturadorAudio.CANAL_VOZ), 0.0001);
        assertEquals(1.04, misturador.obterGanhoEfetivoCanal(MisturadorAudio.CANAL_TELA), 0.0001);
    }

    @Test
    public void processamentoPcm16AmplificaAtenuaEDetectaFala() {
        ByteBuffer buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort(0, (short) 4000);
        buffer.putShort(2, (short) -4000);
        buffer.putShort(4, (short) 20000);
        buffer.putShort(6, (short) -20000);

        double nivelFala = MisturadorAudio.calcularNivelAudio(buffer, 8);
        assertTrue(MisturadorAudio.detectarFala(nivelFala));

        MisturadorAudio.aplicarGanhoPcm16(buffer, 8, 2.0);
        assertEquals(8000, buffer.getShort(0));
        assertEquals(-8000, buffer.getShort(2));
        assertEquals(Short.MAX_VALUE, buffer.getShort(4));
        assertEquals(-32768, buffer.getShort(6));

        MisturadorAudio.aplicarGanhoPcm16(buffer, 8, 0.5);
        assertEquals(4000, buffer.getShort(0));
        assertEquals(-4000, buffer.getShort(2));

        MisturadorAudio.aplicarGanhoPcm16(buffer, 8, 0.0);
        assertEquals(0, buffer.getShort(0));
        assertEquals(0, buffer.getShort(2));
        assertFalse(MisturadorAudio.detectarFala(MisturadorAudio.calcularNivelAudio(buffer, 8)));
    }

    @Test
    public void identificacaoCanalVozUsaMidOuIdFaixaSemCompararReferencias() {
        assertTrue(MotorChamadaWebRtc.identificarEhCanalVoz(
                new String("0"), "track-a", new String("0"), "track-a"));
        assertFalse(MotorChamadaWebRtc.identificarEhCanalVoz(
                new String("2"), "track-b", new String("0"), "track-a"));
        assertTrue(MotorChamadaWebRtc.identificarEhCanalVoz(
                null, new String("track-a"), null, new String("track-a")));
        assertFalse(MotorChamadaWebRtc.identificarEhCanalVoz(
                null, new String("track-b"), null, new String("track-a")));
    }

    @Test
    public void dimensoesTelaPreservamProporcaoEOrientacao() {
        int[] retrato = MotorChamadaWebRtc.calcularDimensoesProporcionaisTela(1080, 2400);
        assertEquals(576, retrato[0]);
        assertEquals(1280, retrato[1]);
        assertEquals(0, retrato[0] % 2);
        assertEquals(0, retrato[1] % 2);

        int[] paisagem = MotorChamadaWebRtc.calcularDimensoesProporcionaisTela(2400, 1080);
        assertEquals(1280, paisagem[0]);
        assertEquals(576, paisagem[1]);
    }

    @Test
    public void selecaoFormatoCameraEscolheMaisProximoDe720p() {
        int[] escolhido = MotorChamadaWebRtc.escolherMelhorFormatoCamera(Arrays.asList(
                new CameraEnumerationAndroid.CaptureFormat(640, 480, 15000, 30000),
                new CameraEnumerationAndroid.CaptureFormat(1280, 720, 15000, 30000),
                new CameraEnumerationAndroid.CaptureFormat(3840, 2160, 15000, 30000)
        ));
        assertEquals(1280, escolhido[0]);
        assertEquals(720, escolhido[1]);
        assertEquals(30, escolhido[2]);

        int[] padrao = MotorChamadaWebRtc.escolherMelhorFormatoCamera(null);
        assertEquals(1280, padrao[0]);
        assertEquals(720, padrao[1]);
        assertEquals(30, padrao[2]);
    }

    @Test
    public void codecAdpcmCodificaEDecodificaPacoteDeAudioDaTela() {
        short[] entrada = new short[CodecAdpcm.AMOSTRAS_POR_QUADRO_20MS];
        for (int i = 0; i < entrada.length; i++) {
            entrada[i] = (short) Math.round(Math.sin(i * 0.1) * 12000.0);
        }
        CodecAdpcm.EstadoCodificador estado = new CodecAdpcm.EstadoCodificador();
        byte[] pacote = CodecAdpcm.codificar(
                entrada,
                entrada.length,
                CodecAdpcm.TAXA_AMOSTRAGEM_PADRAO_HZ,
                42,
                estado
        );
        assertNotNull(pacote);
        assertEquals(
                CodecAdpcm.TAMANHO_CABECALHO_BYTES + (CodecAdpcm.AMOSTRAS_POR_QUADRO_20MS / 2),
                pacote.length
        );

        CodecAdpcm.PacoteDecodificado decodificado = CodecAdpcm.decodificar(pacote);
        assertNotNull(decodificado);
        assertEquals(24000, decodificado.taxaAmostragem);
        assertEquals(42, decodificado.sequencia);
        assertEquals(entrada.length, decodificado.amostras.length);

        long erroMedio = 0;
        for (int i = 0; i < entrada.length; i++) {
            erroMedio += Math.abs(entrada[i] - decodificado.amostras[i]);
        }
        assertTrue((erroMedio / (double) entrada.length) < 1000.0);
        assertNull(CodecAdpcm.decodificar(new byte[]{1, 2, 3}));
    }
}
