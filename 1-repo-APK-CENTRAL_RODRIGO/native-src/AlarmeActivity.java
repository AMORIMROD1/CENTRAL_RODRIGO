package app.lovable.centralrodrigo;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Tela cheia do alarme, exibida por cima da tela de bloqueio. */
public class AlarmeActivity extends Activity {
    private String rota = "/hoje";
    private MediaPlayer videoPlayer;

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        if (Build.VERSION.SDK_INT >= 26) {
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null) km.requestDismissKeyguard(this, null);
        }
        montar(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        montar(intent);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void montar(Intent i) {
        liberarVideo();
        String titulo = i.getStringExtra("titulo");
        String mensagem = i.getStringExtra("mensagem");
        String r = i.getStringExtra("rota");
        rota = r == null ? "/hoje" : r;

        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setGravity(Gravity.CENTER);
        // Fundo escolhido para este alarme: foto (com película escura para ler o texto) ou cor.
        int pid = i.getIntExtra("pid", i.getIntExtra("id", 0));
        JSONObject perso = AlarmePerso.obter(this, pid);
        FrameLayout quadro = new FrameLayout(this);
        int corFundo = Color.parseColor("#1f2616");
        try {
            String c = perso.optString("cor", "");
            if (!c.isEmpty()) corFundo = Color.parseColor(c);
        } catch (Exception ignored) {}
        quadro.setBackgroundColor(corFundo);
        String video = AlarmePerso.caminhoValido(perso, "video");
        boolean temVideo = false;
        if (video != null) {
            try {
                adicionarVideo(quadro, video);
                temVideo = true;
                View pelicula = new View(this);
                pelicula.setBackgroundColor(Color.parseColor("#88000000"));
                quadro.addView(pelicula, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            } catch (Throwable ignored) {}
        }
        String foto = AlarmePerso.caminhoValido(perso, "fundo");
        if (!temVideo && foto != null) {
            try {
                Bitmap bmp = BitmapFactory.decodeFile(foto);
                if (bmp != null) {
                    ImageView img = new ImageView(this);
                    img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    img.setImageBitmap(bmp);
                    quadro.addView(img, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                    View pelicula = new View(this);
                    pelicula.setBackgroundColor(Color.parseColor("#88000000"));
                    quadro.addView(pelicula, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                }
            } catch (Throwable ignored) {}
        }
        raiz.setPadding(dp(32), dp(64), dp(32), dp(48));

        TextView hora = new TextView(this);
        hora.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        hora.setTextColor(Color.WHITE);
        hora.setTextSize(72);
        hora.setTypeface(Typeface.DEFAULT_BOLD);
        hora.setGravity(Gravity.CENTER);
        raiz.addView(hora);

        TextView t = new TextView(this);
        t.setText(titulo == null ? "Alarme" : titulo);
        t.setTextColor(Color.WHITE);
        t.setTextSize(32);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(16), 0, dp(8));
        raiz.addView(t);

        if (mensagem != null && !mensagem.isEmpty()) {
            TextView m = new TextView(this);
            m.setText(mensagem);
            m.setTextColor(Color.parseColor("#d7dcc8"));
            m.setTextSize(20);
            m.setGravity(Gravity.CENTER);
            raiz.addView(m);
        }

        View espaco = new View(this);
        raiz.addView(espaco, new LinearLayout.LayoutParams(1, dp(48)));

        raiz.addView(botao("Parar e abrir o app", "#6b7f4e", true), parametrosBotao());
        raiz.addView(botao("Soneca " + AlarmeAgenda.SONECA_MIN + " min", "#3a4326", false), parametrosBotao());
        quadro.addView(raiz, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(quadro);
    }

    /** Vídeo de fundo: em loop, sem som, preenchendo a tela (se falhar, fica a cor de fundo). */
    private void adicionarVideo(FrameLayout quadro, final String caminho) {
        final TextureView tv = new TextureView(this);
        tv.setAlpha(0f);
        tv.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
                try {
                    liberarVideo();
                    videoPlayer = new MediaPlayer();
                    videoPlayer.setSurface(new Surface(st));
                    videoPlayer.setDataSource(caminho);
                    videoPlayer.setVolume(0f, 0f);
                    videoPlayer.setLooping(true);
                    videoPlayer.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
                        @Override
                        public void onVideoSizeChanged(MediaPlayer mp, int vw, int vh) {
                            if (vw <= 0 || vh <= 0) return;
                            int telaW = getResources().getDisplayMetrics().widthPixels;
                            int telaH = getResources().getDisplayMetrics().heightPixels;
                            float k = Math.max((float) telaW / vw, (float) telaH / vh);
                            tv.setLayoutParams(new FrameLayout.LayoutParams(Math.round(vw * k), Math.round(vh * k), Gravity.CENTER));
                            tv.setAlpha(1f);
                        }
                    });
                    videoPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                        @Override
                        public boolean onError(MediaPlayer mp, int what, int extra) {
                            tv.setAlpha(0f);
                            return true;
                        }
                    });
                    videoPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                        @Override
                        public void onPrepared(MediaPlayer mp) {
                            mp.start();
                        }
                    });
                    videoPlayer.prepareAsync();
                } catch (Exception e) {
                    android.util.Log.e("Alarme", "Falha ao tocar vídeo de fundo", e);
                    tv.setAlpha(0f);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {}

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                liberarVideo();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture st) {}
        });
        quadro.addView(tv, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void liberarVideo() {
        try { if (videoPlayer != null) videoPlayer.release(); } catch (Exception ignored) {}
        videoPlayer = null;
    }

    @Override
    protected void onDestroy() {
        liberarVideo();
        super.onDestroy();
    }

    private LinearLayout.LayoutParams parametrosBotao() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(420), dp(72));
        lp.setMargins(0, dp(8), 0, dp(8));
        return lp;
    }

    private Button botao(String texto, String cor, final boolean abrir) {
        Button bt = new Button(this);
        bt.setText(texto);
        bt.setTextColor(Color.WHITE);
        bt.setTextSize(20);
        bt.setAllCaps(false);
        GradientDrawable fundo = new GradientDrawable();
        fundo.setColor(Color.parseColor(cor));
        fundo.setCornerRadius(dp(16));
        bt.setBackground(fundo);
        bt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (abrir) pararEAbrir(); else soneca();
            }
        });
        return bt;
    }

    private void enviar(String acao) {
        Intent s = new Intent(this, AlarmeService.class);
        s.setAction(acao);
        try { startService(s); } catch (Exception ignored) {}
    }

    private void soneca() {
        enviar(AlarmeService.ACAO_SONECA);
        finish();
    }

    private void pararEAbrir() {
        enviar(AlarmeService.ACAO_PARAR);
        AlarmeAgenda.prefs(this).edit().putString(AlarmeAgenda.KEY_ROTA, rota).apply();
        Intent abrir = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (abrir == null) abrir = new Intent(this, MainActivity.class);
        abrir.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(abrir);
        finish();
    }
}
