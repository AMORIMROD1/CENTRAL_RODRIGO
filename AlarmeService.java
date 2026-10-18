package app.lovable.centralrodrigo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

import org.json.JSONObject;

/** Toca o alarme (volume de ALARME, em loop), vibra e mostra a tela cheia. */
public class AlarmeService extends Service {
    static final String ACAO_TOCAR = "app.lovable.centralrodrigo.TOCAR";
    static final String ACAO_PARAR = "app.lovable.centralrodrigo.PARAR";
    static final String ACAO_SONECA = "app.lovable.centralrodrigo.SONECA";
    private static final String CANAL = "alarme_tocando_v1";
    private static final int NOTIF_ID = 4242;
    private static final long LIMITE_MS = 5 * 60 * 1000L;

    private MediaPlayer player;
    private Vibrator vibrador;
    private PowerManager.WakeLock wakeLock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Bundle atual;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String acao = intent == null ? null : intent.getAction();
        if (ACAO_PARAR.equals(acao)) {
            parar();
            return START_NOT_STICKY;
        }
        if (ACAO_SONECA.equals(acao)) {
            soneca();
            return START_NOT_STICKY;
        }
        if (intent == null || intent.getExtras() == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        tocar(intent.getExtras());
        return START_NOT_STICKY;
    }

    private void tocar(Bundle extras) {
        liberarRecursos();
        atual = extras;
        String titulo = extras.getString("titulo", "Alarme");
        String mensagem = extras.getString("mensagem", "");

        Intent telaIntent = new Intent(this, AlarmeActivity.class);
        telaIntent.putExtras(extras);
        telaIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tela = PendingIntent.getActivity(this, 1, telaIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            NotificationChannel canal = new NotificationChannel(CANAL, "Alarme tocando", NotificationManager.IMPORTANCE_HIGH);
            canal.setSound(null, null);
            canal.enableVibration(false);
            canal.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(canal);
        }
        Notification.Builder nb = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CANAL) : new Notification.Builder(this);
        nb.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(titulo)
                .setContentText(mensagem.isEmpty() ? "Toque para abrir" : mensagem)
                .setCategory(Notification.CATEGORY_ALARM)
                .setOngoing(true)
                .setContentIntent(tela)
                .setFullScreenIntent(tela, true)
                .setVisibility(Notification.VISIBILITY_PUBLIC);
        if (Build.VERSION.SDK_INT < 26) nb.setPriority(Notification.PRIORITY_MAX);
        nb.addAction(new Notification.Action.Builder(null, "Soneca", acaoServico(ACAO_SONECA, 2)).build());
        nb.addAction(new Notification.Action.Builder(null, "Parar", acaoServico(ACAO_PARAR, 3)).build());

        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, nb.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIF_ID, nb.build());
            }
        } catch (Exception e) {
            android.util.Log.e("Alarme", "startForeground falhou", e);
        }

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "central:alarme");
            wakeLock.acquire(LIMITE_MS);
        }

        if (!"silencioso".equals(extras.getString("som", "padrao"))) iniciarSom(extras.getString("som", "padrao"), extras.getInt("pid", extras.getInt("id", 0)));
        if (extras.getBoolean("vibrar", true)) iniciarVibracao();

        // Com a permissão "exibir sobre outros apps" o Android deixa abrir a tela direto.
        try { startActivity(telaIntent); } catch (Exception ignored) {}

        handler.postDelayed(this::parar, LIMITE_MS);
    }

    private PendingIntent acaoServico(String acao, int codigo) {
        Intent i = new Intent(this, AlarmeService.class);
        i.setAction(acao);
        return PendingIntent.getService(this, codigo, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void iniciarSom(String som, int pid) {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                int max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                int minimo = Math.round(max * ("alarme".equals(som) ? 0.8f : 0.5f));
                if (am.getStreamVolume(AudioManager.STREAM_ALARM) < minimo) {
                    am.setStreamVolume(AudioManager.STREAM_ALARM, minimo, 0);
                }
            }
            AudioAttributes atrib = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            // Música escolhida para este alarme (se houver); senão, o toque padrão.
            String musica = AlarmePerso.caminhoValido(AlarmePerso.obter(this, pid), "musica");
            if (musica != null) {
                try {
                    player = new MediaPlayer();
                    player.setAudioAttributes(atrib);
                    player.setDataSource(musica);
                    player.setLooping(true);
                    player.prepare();
                    player.start();
                    return;
                } catch (Exception e) {
                    android.util.Log.e("Alarme", "Música escolhida falhou, usando toque padrão", e);
                    try { if (player != null) player.release(); } catch (Exception ignored) {}
                    player = null;
                }
            }
            Uri uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            player = new MediaPlayer();
            player.setAudioAttributes(atrib);
            player.setDataSource(this, uri);
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception e) {
            android.util.Log.e("Alarme", "Falha ao tocar som", e);
        }
    }

    @SuppressWarnings("deprecation")
    private void iniciarVibracao() {
        try {
            vibrador = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrador == null || !vibrador.hasVibrator()) return;
            long[] padrao = {0, 700, 500};
            if (Build.VERSION.SDK_INT >= 26) vibrador.vibrate(VibrationEffect.createWaveform(padrao, 0));
            else vibrador.vibrate(padrao, 0);
        } catch (Exception e) {
            android.util.Log.e("Alarme", "Falha ao vibrar", e);
        }
    }

    private void soneca() {
        try {
            if (atual != null) {
                JSONObject a = new JSONObject();
                a.put("id", atual.getInt("id", 0));
                a.put("pid", atual.getInt("pid", atual.getInt("id", 0)));
                a.put("titulo", atual.getString("titulo", "Alarme"));
                a.put("mensagem", atual.getString("mensagem", ""));
                a.put("rota", atual.getString("rota", "/hoje"));
                a.put("som", atual.getString("som", "padrao"));
                a.put("vibrar", atual.getBoolean("vibrar", true));
                AlarmeAgenda.agendar(this, a, System.currentTimeMillis() + AlarmeAgenda.SONECA_MIN * 60000L, true);
            }
        } catch (Exception e) {
            android.util.Log.e("Alarme", "Falha na soneca", e);
        }
        parar();
    }

    private void liberarRecursos() {
        handler.removeCallbacksAndMessages(null);
        try { if (player != null) { player.stop(); } } catch (Exception ignored) {}
        try { if (player != null) { player.release(); } } catch (Exception ignored) {}
        player = null;
        try { if (vibrador != null) vibrador.cancel(); } catch (Exception ignored) {}
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) {}
        wakeLock = null;
    }

    private void parar() {
        liberarRecursos();
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        liberarRecursos();
        super.onDestroy();
    }
}
