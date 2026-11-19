package com.example.unme;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.util.Base64;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class GerenciadorPerfil {

    public static final String NOME_PREFERENCIAS = "unme_preferencias";
    public static final String CHAVE_PERFIL = "unme.profile.v1";
    public static final int COMPRIMENTO_MAXIMO_NOME = 40;
    public static final int TAMANHO_MAXIMO_FOTO = 60000;
    public static final long TAMANHO_MAXIMO_ARQUIVO_BYTES = 8L * 1024L * 1024L;
    public static final int DIMENSAO_AVATAR_PX = 256;
    public static final int COR_FUNDO_AVATAR = 0xFF1A1D2A;

    private static final int[] QUALIDADES_COMPRESSAO = {80, 60, 40};
    private static final Set<String> TIPOS_MIME_PERMITIDOS = new HashSet<>(
            Arrays.asList("image/jpeg", "image/png", "image/webp")
    );
    private static final Pattern PADRAO_DATA_URL_FOTO = Pattern.compile(
            "^data:image/(jpeg|png|webp);base64,[A-Za-z0-9+/]+=*$"
    );

    private final SharedPreferences preferencias;

    public GerenciadorPerfil(Context contexto) {
        this.preferencias = contexto.getApplicationContext()
                .getSharedPreferences(NOME_PREFERENCIAS, Context.MODE_PRIVATE);
    }

    public static PerfilUsuario normalizar(String nomeBruto, String fotoBruta) {
        String nomeNormalizado = "";
        if (nomeBruto != null) {
            String limpo = nomeBruto.trim();
            nomeNormalizado = limpo.length() > COMPRIMENTO_MAXIMO_NOME
                    ? limpo.substring(0, COMPRIMENTO_MAXIMO_NOME)
                    : limpo;
        }

        String fotoNormalizada = "";
        if (fotoBruta != null
                && fotoBruta.length() <= TAMANHO_MAXIMO_FOTO
                && PADRAO_DATA_URL_FOTO.matcher(fotoBruta).matches()) {
            fotoNormalizada = fotoBruta;
        }

        return new PerfilUsuario(nomeNormalizado, fotoNormalizada);
    }

    public static PerfilUsuario normalizarJson(JSONObject objetoJson) {
        if (objetoJson == null) {
            return PerfilUsuario.vazio();
        }
        String nome = objetoJson.has("name") && !objetoJson.isNull("name")
                ? objetoJson.optString("name", "")
                : "";
        String foto = objetoJson.has("photo") && !objetoJson.isNull("photo")
                ? objetoJson.optString("photo", "")
                : "";
        return normalizar(nome, foto);
    }

    public static JSONObject converterParaJson(PerfilUsuario perfil) {
        PerfilUsuario normalizado = perfil != null
                ? normalizar(perfil.obterNome(), perfil.obterFoto())
                : PerfilUsuario.vazio();
        JSONObject objeto = new JSONObject();
        try {
            objeto.put("name", normalizado.obterNome());
            objeto.put("photo", normalizado.obterFoto());
        } catch (JSONException ignorado) {
        }
        return objeto;
    }

    public PerfilUsuario carregar() {
        String jsonSalvo = preferencias.getString(CHAVE_PERFIL, "{}");
        try {
            return normalizarJson(new JSONObject(jsonSalvo != null ? jsonSalvo : "{}"));
        } catch (JSONException erro) {
            return PerfilUsuario.vazio();
        }
    }

    public PerfilUsuario salvar(PerfilUsuario valor) {
        PerfilUsuario normalizado = valor != null
                ? normalizar(valor.obterNome(), valor.obterFoto())
                : PerfilUsuario.vazio();
        preferencias.edit()
                .putString(CHAVE_PERFIL, converterParaJson(normalizado).toString())
                .apply();
        return normalizado;
    }

    public static String obterIniciais(String nomePerfil, String textoPadrao) {
        String base = (nomePerfil != null && !nomePerfil.trim().isEmpty())
                ? nomePerfil.trim()
                : (textoPadrao != null && !textoPadrao.trim().isEmpty() ? textoPadrao.trim() : "Você");
        String[] partes = base.split("\\s+");
        StringBuilder construtor = new StringBuilder();
        int limite = Math.min(2, partes.length);
        for (int indice = 0; indice < limite; indice++) {
            String parte = partes[indice];
            if (!parte.isEmpty()) {
                int pontoCodigo = parte.codePointAt(0);
                construtor.appendCodePoint(pontoCodigo);
            }
        }
        return construtor.toString().toUpperCase(Locale.ROOT);
    }

    public static String obterIniciaisFallbackErro(String nomePerfil, String textoPadrao) {
        String base = (nomePerfil != null && !nomePerfil.isEmpty())
                ? nomePerfil
                : (textoPadrao != null ? textoPadrao : "Você");
        String recorte = base.length() > 2 ? base.substring(0, 2) : base;
        return recorte.toUpperCase(Locale.ROOT);
    }

    public static String obterRotuloQuadroLocal(PerfilUsuario perfilLocal, boolean compartilhandoTela) {
        String nome = perfilLocal != null ? perfilLocal.obterNome() : "";
        if (!nome.isEmpty()) {
            return nome + " · " + (compartilhandoTela ? "tela" : "você");
        }
        return compartilhandoTela ? "Sua tela" : "Você";
    }

    public static String obterRotuloQuadroRemoto(PerfilUsuario perfilRemoto, boolean compartilhandoTela) {
        String nomeRemoto = (perfilRemoto != null && perfilRemoto.possuiNome())
                ? perfilRemoto.obterNome()
                : "Sua companhia";
        return compartilhandoTela ? nomeRemoto + " · tela" : nomeRemoto;
    }

    public static boolean validarTipoMimeFoto(String tipoMime) {
        return tipoMime != null && TIPOS_MIME_PERMITIDOS.contains(tipoMime.toLowerCase(Locale.ROOT));
    }

    public static String prepararFoto(Context contexto, Uri uriArquivo) throws Exception {
        if (uriArquivo == null) {
            throw new IllegalArgumentException("Escolha uma imagem JPG, PNG ou WebP.");
        }
        ContentResolver resolvedor = contexto.getContentResolver();
        String tipoMime = resolvedor.getType(uriArquivo);
        if (!validarTipoMimeFoto(tipoMime)) {
            throw new IllegalArgumentException("Escolha uma imagem JPG, PNG ou WebP.");
        }

        byte[] bytesImagem = lerBytesComLimite(resolvedor, uriArquivo);
        Bitmap bitmapOriginal = BitmapFactory.decodeByteArray(bytesImagem, 0, bytesImagem.length);
        if (bitmapOriginal == null || bitmapOriginal.getWidth() <= 0 || bitmapOriginal.getHeight() <= 0) {
            if (bitmapOriginal != null) {
                bitmapOriginal.recycle();
            }
            throw new IllegalArgumentException("Imagem inválida.");
        }

        try {
            return recortarEComprimirBitmap(bitmapOriginal);
        } finally {
            bitmapOriginal.recycle();
        }
    }

    public static String recortarEComprimirBitmap(Bitmap bitmapOriginal) throws Exception {
        if (bitmapOriginal == null || bitmapOriginal.getWidth() <= 0 || bitmapOriginal.getHeight() <= 0) {
            throw new IllegalArgumentException("Imagem inválida.");
        }

        int larguraOriginal = bitmapOriginal.getWidth();
        int alturaOriginal = bitmapOriginal.getHeight();
        int tamanhoRecorte = Math.min(larguraOriginal, alturaOriginal);
        int origemX = (larguraOriginal - tamanhoRecorte) / 2;
        int origemY = (alturaOriginal - tamanhoRecorte) / 2;

        Bitmap bitmapQuadrado = Bitmap.createBitmap(
                DIMENSAO_AVATAR_PX,
                DIMENSAO_AVATAR_PX,
                Bitmap.Config.ARGB_8888
        );
        try {
            Canvas telaPintura = new Canvas(bitmapQuadrado);
            telaPintura.drawColor(COR_FUNDO_AVATAR);
            Paint pincel = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            Rect retanguloOrigem = new Rect(
                    origemX,
                    origemY,
                    origemX + tamanhoRecorte,
                    origemY + tamanhoRecorte
            );
            Rect retanguloDestino = new Rect(0, 0, DIMENSAO_AVATAR_PX, DIMENSAO_AVATAR_PX);
            telaPintura.drawBitmap(bitmapOriginal, retanguloOrigem, retanguloDestino, pincel);

            for (int qualidade : QUALIDADES_COMPRESSAO) {
                ByteArrayOutputStream saidaBytes = new ByteArrayOutputStream();
                bitmapQuadrado.compress(Bitmap.CompressFormat.JPEG, qualidade, saidaBytes);
                String base64 = Base64.encodeToString(saidaBytes.toByteArray(), Base64.NO_WRAP);
                String dataUrl = "data:image/jpeg;base64," + base64;
                if (dataUrl.length() <= TAMANHO_MAXIMO_FOTO) {
                    return dataUrl;
                }
            }
            throw new IllegalArgumentException("Não foi possível reduzir essa imagem. Escolha outra foto.");
        } finally {
            bitmapQuadrado.recycle();
        }
    }

    private static byte[] lerBytesComLimite(ContentResolver resolvedor, Uri uriArquivo) throws Exception {
        try (InputStream fluxoEntrada = resolvedor.openInputStream(uriArquivo)) {
            if (fluxoEntrada == null) {
                throw new IllegalArgumentException("Não foi possível abrir a imagem.");
            }
            ByteArrayOutputStream acumulador = new ByteArrayOutputStream();
            byte[] bloco = new byte[16384];
            long totalLido = 0L;
            int bytesLidos;
            while ((bytesLidos = fluxoEntrada.read(bloco)) != -1) {
                totalLido += bytesLidos;
                if (totalLido > TAMANHO_MAXIMO_ARQUIVO_BYTES) {
                    throw new IllegalArgumentException("A imagem pode ter até 8 MB.");
                }
                acumulador.write(bloco, 0, bytesLidos);
            }
            return acumulador.toByteArray();
        } catch (IOException erroLeitura) {
            throw new IllegalArgumentException("Não foi possível abrir a imagem.");
        }
    }

    public static Bitmap decodificarFotoParaBitmap(String dataUrl) {
        if (dataUrl == null || dataUrl.isEmpty()) {
            return null;
        }
        int indiceVirgula = dataUrl.indexOf(',');
        if (indiceVirgula < 0 || indiceVirgula + 1 >= dataUrl.length()) {
            return null;
        }
        try {
            byte[] bytes = Base64.decode(dataUrl.substring(indiceVirgula + 1), Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (IllegalArgumentException erro) {
            return null;
        }
    }

    public static void renderizarAvatar(
            View containerAvatar,
            ImageView imagemAvatar,
            TextView textoAvatar,
            PerfilUsuario perfil,
            String textoPadrao
    ) {
        PerfilUsuario perfilSeguro = perfil != null ? perfil : PerfilUsuario.vazio();
        String nomeExibicao = perfilSeguro.possuiNome()
                ? perfilSeguro.obterNome()
                : (textoPadrao != null ? textoPadrao : "Você");

        if (containerAvatar != null) {
            containerAvatar.setContentDescription(nomeExibicao);
            containerAvatar.setClipToOutline(true);
        }

        if (perfilSeguro.possuiFoto() && imagemAvatar != null) {
            Bitmap bitmap = decodificarFotoParaBitmap(perfilSeguro.obterFoto());
            if (bitmap != null) {
                imagemAvatar.setImageBitmap(bitmap);
                imagemAvatar.setContentDescription(nomeExibicao);
                imagemAvatar.setClipToOutline(true);
                imagemAvatar.setVisibility(View.VISIBLE);
                if (textoAvatar != null) {
                    textoAvatar.setVisibility(View.GONE);
                }
                return;
            } else {
                imagemAvatar.setImageDrawable(null);
                imagemAvatar.setVisibility(View.GONE);
                if (textoAvatar != null) {
                    textoAvatar.setVisibility(View.VISIBLE);
                    textoAvatar.setText(obterIniciaisFallbackErro(nomeExibicao, textoPadrao));
                }
                return;
            }
        }

        if (imagemAvatar != null) {
            imagemAvatar.setImageDrawable(null);
            imagemAvatar.setVisibility(View.GONE);
        }
        if (textoAvatar != null) {
            textoAvatar.setVisibility(View.VISIBLE);
            textoAvatar.setText(obterIniciais(perfilSeguro.obterNome(), textoPadrao));
        }
    }

    public static String criarPayloadCanalDados(PerfilUsuario perfil) {
        JSONObject envelope = new JSONObject();
        try {
            envelope.put("type", "profile");
            envelope.put("profile", converterParaJson(perfil));
        } catch (JSONException ignorado) {
        }
        return envelope.toString();
    }

    public static PerfilUsuario interpretarPayloadCanalDados(String dadosRecebidos) {
        if (dadosRecebidos == null || dadosRecebidos.length() > TAMANHO_MAXIMO_FOTO + 512) {
            return null;
        }
        try {
            JSONObject mensagem = new JSONObject(dadosRecebidos);
            if ("profile".equals(mensagem.optString("type", ""))) {
                return normalizarJson(mensagem.optJSONObject("profile"));
            }
        } catch (JSONException ignorado) {
        }
        return null;
    }
}
