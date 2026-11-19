package com.example.unme;

import android.content.Context;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.util.AttributeSet;
import android.view.TextureView;

import org.webrtc.EglBase;
import org.webrtc.EglRenderer;
import org.webrtc.GlRectDrawer;
import org.webrtc.RendererCommon;
import org.webrtc.ThreadUtils;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

import java.util.concurrent.CountDownLatch;

public class RenderizadorTexturaVideo extends TextureView
        implements TextureView.SurfaceTextureListener, VideoSink {

    private final EglRenderer renderizadorEgl;
    private RendererCommon.ScalingType tipoEscalaIgual = RendererCommon.ScalingType.SCALE_ASPECT_FILL;
    private RendererCommon.ScalingType tipoEscalaDiferente = RendererCommon.ScalingType.SCALE_ASPECT_FILL;
    private volatile int larguraQuadroGirado = 0;
    private volatile int alturaQuadroGirado = 0;
    private boolean inicializado = false;

    public RenderizadorTexturaVideo(Context contexto) {
        this(contexto, null);
    }

    public RenderizadorTexturaVideo(Context contexto, AttributeSet atributos) {
        super(contexto, atributos);
        this.renderizadorEgl = new EglRenderer("RenderizadorTexturaVideo");
        setSurfaceTextureListener(this);
        setOpaque(false);
    }

    public void inicializar(EglBase.Context contextoCompartilhado) {
        if (inicializado) {
            return;
        }
        inicializado = true;
        renderizadorEgl.init(contextoCompartilhado, EglBase.CONFIG_PLAIN, new GlRectDrawer());
        if (isAvailable() && getSurfaceTexture() != null) {
            renderizadorEgl.createEglSurface(getSurfaceTexture());
        }
    }

    public void definirEspelhamento(boolean espelhar) {
        renderizadorEgl.setMirror(espelhar);
    }

    public void definirTipoEscala(RendererCommon.ScalingType tipoEscala) {
        definirTipoEscala(tipoEscala, tipoEscala);
    }

    public void definirTipoEscala(
            RendererCommon.ScalingType tipoMesmaOrientacao,
            RendererCommon.ScalingType tipoOrientacaoDiferente
    ) {
        this.tipoEscalaIgual = tipoMesmaOrientacao;
        this.tipoEscalaDiferente = tipoOrientacaoDiferente;
        requestLayout();
    }

    public void limparImagem() {
        larguraQuadroGirado = 0;
        alturaQuadroGirado = 0;
        if (inicializado) {
            renderizadorEgl.clearImage();
        }
        post(this::requestLayout);
    }

    public void liberar() {
        if (inicializado) {
            inicializado = false;
            renderizadorEgl.release();
        }
    }

    @Override
    public void onFrame(VideoFrame quadro) {
        int largura = quadro.getRotatedWidth();
        int altura = quadro.getRotatedHeight();
        if (largura != larguraQuadroGirado || altura != alturaQuadroGirado) {
            larguraQuadroGirado = largura;
            alturaQuadroGirado = altura;
            post(this::requestLayout);
        }
        if (inicializado) {
            renderizadorEgl.onFrame(quadro);
        }
    }

    @Override
    protected void onMeasure(int larguraSpec, int alturaSpec) {
        int larguraDisponivel = MeasureSpec.getSize(larguraSpec);
        int alturaDisponivel = MeasureSpec.getSize(alturaSpec);
        if (larguraDisponivel <= 0 || alturaDisponivel <= 0
                || larguraQuadroGirado <= 0 || alturaQuadroGirado <= 0) {
            setMeasuredDimension(larguraDisponivel, alturaDisponivel);
            return;
        }

        boolean quadroPaisagem = larguraQuadroGirado > alturaQuadroGirado;
        boolean vistaPaisagem = larguraDisponivel > alturaDisponivel;
        RendererCommon.ScalingType tipoAplicado = (quadroPaisagem == vistaPaisagem)
                ? tipoEscalaIgual
                : tipoEscalaDiferente;

        float proporcaoQuadro = larguraQuadroGirado / (float) alturaQuadroGirado;
        Point tamanho = RendererCommon.getDisplaySize(
                tipoAplicado,
                proporcaoQuadro,
                larguraDisponivel,
                alturaDisponivel
        );
        setMeasuredDimension(tamanho.x, tamanho.y);
    }

    @Override
    protected void onLayout(boolean mudou, int esquerda, int topo, int direita, int base) {
        int largura = direita - esquerda;
        int altura = base - topo;
        if (inicializado && largura > 0 && altura > 0) {
            renderizadorEgl.setLayoutAspectRatio(largura / (float) altura);
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture textura, int largura, int altura) {
        if (inicializado) {
            renderizadorEgl.createEglSurface(textura);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture textura, int largura, int altura) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture textura) {
        if (inicializado) {
            CountDownLatch barreira = new CountDownLatch(1);
            renderizadorEgl.releaseEglSurface(barreira::countDown);
            ThreadUtils.awaitUninterruptibly(barreira);
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture textura) {
    }
}
