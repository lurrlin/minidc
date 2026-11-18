package com.example.unme;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

public class ServicoChamada extends Service {

    public static final String CANAL_NOTIFICACAO_ID = "unme_chamada_ativa";
    public static final int ID_NOTIFICACAO = 1001;

    public static final String EXTRA_NOME_SALA = "extra_nome_sala";
    public static final String EXTRA_PROJECAO_TELA = "extra_projecao_tela";
    public static final String EXTRA_CAMERA_ATIVA = "extra_camera_ativa";
    public static final String ACAO_ENCERRAR_SERVICO = "com.example.unme.ENCERRAR_SERVICO";

    private static Runnable acaoPendenteProjecao;
    private static final Handler MANIPULADOR_PRINCIPAL = new Handler(Looper.getMainLooper());

    public static void iniciarOuAtualizar(
            Context contexto,
            String nomeSala,
            boolean projecaoTelaAtiva,
            boolean cameraAtiva,
            Runnable aoConfirmarProjecao
    ) {
        acaoPendenteProjecao = aoConfirmarProjecao;
        Intent intencao = new Intent(contexto, ServicoChamada.class);
        intencao.putExtra(EXTRA_NOME_SALA, nomeSala != null ? nomeSala : "");
        intencao.putExtra(EXTRA_PROJECAO_TELA, projecaoTelaAtiva);
        intencao.putExtra(EXTRA_CAMERA_ATIVA, cameraAtiva);
        try {
            ContextCompat.startForegroundService(contexto, intencao);
        } catch (Exception erro) {
            if (acaoPendenteProjecao != null) {
                Runnable pendente = acaoPendenteProjecao;
                acaoPendenteProjecao = null;
                MANIPULADOR_PRINCIPAL.post(pendente);
            }
        }
    }

    public static void pararServico(Context contexto) {
        acaoPendenteProjecao = null;
        try {
            contexto.stopService(new Intent(contexto, ServicoChamada.class));
        } catch (Exception ignorado) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        criarCanalNotificacao();
    }

    @Override
    public int onStartCommand(Intent intencao, int flags, int idInicio) {
        if (intencao != null && ACAO_ENCERRAR_SERVICO.equals(intencao.getAction())) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        String nomeSala = intencao != null ? intencao.getStringExtra(EXTRA_NOME_SALA) : "";
        boolean projecaoTela = intencao != null && intencao.getBooleanExtra(EXTRA_PROJECAO_TELA, false);
        boolean cameraAtiva = intencao != null && intencao.getBooleanExtra(EXTRA_CAMERA_ATIVA, false);

        Notification notificacao = construirNotificacao(nomeSala, projecaoTela);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                int tiposServico = obterTiposServicoPermitidos(projecaoTela, cameraAtiva);
                if (tiposServico != 0) {
                    startForeground(ID_NOTIFICACAO, notificacao, tiposServico);
                } else {
                    startForeground(ID_NOTIFICACAO, notificacao);
                }
            } else {
                startForeground(ID_NOTIFICACAO, notificacao);
            }
        } catch (Exception ignorado) {
        }

        if (acaoPendenteProjecao != null) {
            Runnable acao = acaoPendenteProjecao;
            acaoPendenteProjecao = null;
            MANIPULADOR_PRINCIPAL.post(acao);
        }

        return START_NOT_STICKY;
    }

    private int obterTiposServicoPermitidos(boolean projecaoTela, boolean cameraAtiva) {
        int tipos = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                tipos |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            }
            if (cameraAtiva && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                tipos |= ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && projecaoTela) {
            tipos |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
        }
        return tipos;
    }

    private Notification construirNotificacao(String nomeSala, boolean compartilhandoTela) {
        Intent abrirApp = new Intent(this, MainActivity.class);
        abrirApp.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flagsPending = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pendenteAbrir = PendingIntent.getActivity(this, 0, abrirApp, flagsPending);

        String titulo = compartilhandoTela ? "Unme · Compartilhando tela" : "Unme · Chamada ativa";
        String descricao = (nomeSala != null && !nomeSala.isEmpty())
                ? "Sala: " + nomeSala
                : "Conectado ao seu espaço a dois";

        return new NotificationCompat.Builder(this, CANAL_NOTIFICACAO_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(titulo)
                .setContentText(descricao)
                .setContentIntent(pendenteAbrir)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .build();
    }

    private void criarCanalNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(
                    CANAL_NOTIFICACAO_ID,
                    "Chamada ativa do Unme",
                    NotificationManager.IMPORTANCE_LOW
            );
            canal.setDescription("Mantém o áudio e o compartilhamento de tela ativos durante a chamada.");
            canal.setShowBadge(false);
            NotificationManager gerenciador = getSystemService(NotificationManager.class);
            if (gerenciador != null) {
                gerenciador.createNotificationChannel(canal);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
