package com.example.unme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {

    @Test
    public void persistenciaECompressaoDeFotoDoPerfilFuncionamNoAndroid() throws Exception {
        Context contextoApp = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("com.example.unme", contextoApp.getPackageName());

        Bitmap imagemOriginal = Bitmap.createBitmap(480, 320, Bitmap.Config.ARGB_8888);
        Canvas tela = new Canvas(imagemOriginal);
        tela.drawColor(0xFFC4B4F7);

        String fotoComprimida = GerenciadorPerfil.recortarEComprimirBitmap(imagemOriginal);
        imagemOriginal.recycle();

        assertTrue(fotoComprimida.startsWith("data:image/jpeg;base64,"));
        assertTrue(fotoComprimida.length() <= GerenciadorPerfil.TAMANHO_MAXIMO_FOTO);

        Bitmap decodificada = GerenciadorPerfil.decodificarFotoParaBitmap(fotoComprimida);
        assertNotNull(decodificada);
        assertEquals(256, decodificada.getWidth());
        assertEquals(256, decodificada.getHeight());
        decodificada.recycle();

        GerenciadorPerfil gerenciador = new GerenciadorPerfil(contextoApp);
        gerenciador.salvar(new PerfilUsuario("Alex Novo", fotoComprimida));

        PerfilUsuario recarregado = new GerenciadorPerfil(contextoApp).carregar();
        assertEquals("Alex Novo", recarregado.obterNome());
        assertEquals(fotoComprimida, recarregado.obterFoto());
    }
}
