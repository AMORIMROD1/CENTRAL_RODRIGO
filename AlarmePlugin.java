package app.lovable.centralrodrigo;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.OpenableColumns;
import android.provider.Settings;

import androidx.activity.result.ActivityResult;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;

/** Ponte entre o site (JavaScript) e o alarme nativo. */
@CapacitorPlugin(
        name = "AlarmeNativo",
        permissions = {@Permission(strings = {Manifest.permission.POST_NOTIFICATIONS}, alias = "notificacoes")}
)
public class AlarmePlugin extends Plugin {

    private JSObject status() {
        Context c = getContext();
        JSObject r = new JSObject();
        r.put("sdk", Build.VERSION.SDK_INT);
        r.put("notificacoes", AlarmeAgenda.temPermissaoDeNotificar(c));
        boolean exatos = true;
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            exatos = am != null && am.canScheduleExactAlarms();
        }
        r.put("exatos", exatos);
        boolean telaCheia = true;
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            telaCheia = nm != null && nm.canUseFullScreenIntent();
        }
        r.put("telaCheia", telaCheia);
        r.put("sobrepor", Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c));
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        r.put("bateria", pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName()));
        return r;
    }

    @PluginMethod
    public void status(PluginCall call) {
        call.resolve(status());
    }

    @PluginMethod
    public void sincronizar(PluginCall call) {
        try {
            JSArray lista = call.getArray("alarmes", new JSArray());
            AlarmeAgenda.salvarLista(getContext(), lista);
            int n = AlarmeAgenda.sincronizar(getContext(), lista);
            JSObject r = status();
            r.put("agendados", n);
            call.resolve(r);
        } catch (Exception e) {
            call.reject("Falha ao agendar: " + e.getMessage());
        }
    }

    @PluginMethod
    public void pedirNotificacoes(PluginCall call) {
        if (Build.VERSION.SDK_INT < 33 || getPermissionState("notificacoes") == PermissionState.GRANTED) {
            call.resolve(status());
        } else {
            requestPermissionForAlias("notificacoes", call, "aoResponderNotificacoes");
        }
    }

    @PermissionCallback
    private void aoResponderNotificacoes(PluginCall call) {
        call.resolve(status());
    }

    @PluginMethod
    public void abrirConfig(PluginCall call) {
        String tipo = call.getString("tipo", "app");
        String pacote = getContext().getPackageName();
        Uri uri = Uri.parse("package:" + pacote);
        Intent i;
        switch (tipo) {
            case "notificacoes":
                i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pacote);
                break;
            case "exatos":
                i = Build.VERSION.SDK_INT >= 31 ? new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, uri) : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri);
                break;
            case "telaCheia":
                i = Build.VERSION.SDK_INT >= 34 ? new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, uri) : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri);
                break;
            case "sobrepor":
                i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri);
                break;
            case "bateria":
                i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, uri);
                break;
            default:
                i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri);
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            getContext().startActivity(i);
        } catch (Exception e) {
            try {
                getContext().startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e2) {
                call.reject("Não foi possível abrir as configurações.");
                return;
            }
        }
        call.resolve();
    }

    /** Toca daqui a alguns segundos para testar (dá tempo de bloquear a tela). */
    @PluginMethod
    public void testar(PluginCall call) {
        try {
            int segundos = call.getInt("segundos", 8);
            JSONObject a = new JSONObject();
            a.put("id", 999999);
            a.put("pid", call.getInt("pid", 0));
            a.put("titulo", call.getString("titulo", "Teste de alarme"));
            a.put("mensagem", call.getString("mensagem", "Se você está vendo isto, o alarme funciona."));
            a.put("rota", call.getString("rota", "/hoje"));
            a.put("som", call.getString("som", "padrao"));
            a.put("vibrar", call.getBoolean("vibrar", true));
            AlarmeAgenda.agendar(getContext(), a, System.currentTimeMillis() + segundos * 1000L, true);
            call.resolve();
        } catch (Exception e) {
            call.reject("Falha no teste: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- personalização

    @PluginMethod
    public void obterPersonalizacao(PluginCall call) {
        JSObject r = new JSObject();
        JSObject itens = new JSObject();
        try {
            JSONObject t = AlarmePerso.todos(getContext());
            Iterator<String> it = t.keys();
            while (it.hasNext()) {
                String k = it.next();
                JSONObject o = t.optJSONObject(k);
                if (o == null) continue;
                JSObject x = new JSObject();
                x.put("musica", AlarmePerso.caminhoValido(o, "musica") != null ? o.optString("musicaNome", "Música escolhida") : "");
                x.put("fundo", AlarmePerso.caminhoValido(o, "fundo") != null ? o.optString("fundoNome", "Foto escolhida") : "");
                x.put("video", AlarmePerso.caminhoValido(o, "video") != null ? o.optString("videoNome", "Vídeo escolhido") : "");
                x.put("cor", o.optString("cor", ""));
                itens.put(k, x);
            }
        } catch (Exception ignored) {}
        r.put("itens", itens);
        call.resolve(r);
    }

    private JSObject cancelado() {
        JSObject r = new JSObject();
        r.put("cancelado", true);
        return r;
    }

    private String nomeDe(Uri uri) {
        try (Cursor c = getContext().getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int ix = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (ix >= 0) return c.getString(ix);
            }
        } catch (Exception ignored) {}
        return "arquivo";
    }

    private String extensaoDe(String nome, String padrao) {
        int p = nome.lastIndexOf('.');
        if (p < 0 || p == nome.length() - 1) return padrao;
        String e = nome.substring(p + 1).toLowerCase();
        return e.matches("[a-z0-9]{1,5}") ? e : padrao;
    }

    @PluginMethod
    public void escolherMusica(PluginCall call) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        startActivityForResult(call, i, "aoEscolherMusica");
    }

    @ActivityCallback
    private void aoEscolherMusica(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) {
            call.resolve(cancelado());
            return;
        }
        final Uri uri = result.getData().getData();
        final int id = call.getInt("id", 0);
        new Thread(() -> {
            try {
                Context c = getContext();
                String nome = nomeDe(uri);
                File dest = new File(AlarmePerso.pasta(c), "musica_" + id + "." + extensaoDe(nome, "mp3"));
                JSONObject o = AlarmePerso.obter(c, id);
                String antiga = o.optString("musica", "");
                long total = 0;
                try (InputStream in = c.getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(dest)) {
                    if (in == null) throw new Exception("não foi possível abrir o arquivo");
                    byte[] b = new byte[65536];
                    int n;
                    while ((n = in.read(b)) > 0) {
                        total += n;
                        if (total > 60L * 1024 * 1024) throw new Exception("arquivo maior que 60 MB");
                        out.write(b, 0, n);
                    }
                }
                if (!antiga.isEmpty() && !antiga.equals(dest.getAbsolutePath())) AlarmePerso.apagarArquivo(antiga);
                o.put("musica", dest.getAbsolutePath());
                o.put("musicaNome", nome);
                AlarmePerso.salvar(c, id, o);
                JSObject r = new JSObject();
                r.put("nome", nome);
                call.resolve(r);
            } catch (Exception e) {
                call.reject("Não foi possível usar essa música: " + e.getMessage());
            }
        }).start();
    }

    @PluginMethod
    public void removerMusica(PluginCall call) {
        int id = call.getInt("id", 0);
        JSONObject o = AlarmePerso.obter(getContext(), id);
        AlarmePerso.apagarArquivo(o.optString("musica", ""));
        o.remove("musica");
        o.remove("musicaNome");
        AlarmePerso.salvar(getContext(), id, o);
        call.resolve();
    }

    @PluginMethod
    public void escolherFundo(PluginCall call) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(call, i, "aoEscolherFundo");
    }

    @ActivityCallback
    private void aoEscolherFundo(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) {
            call.resolve(cancelado());
            return;
        }
        final Uri uri = result.getData().getData();
        final int id = call.getInt("id", 0);
        new Thread(() -> {
            try {
                Context c = getContext();
                String nome = nomeDe(uri);
                BitmapFactory.Options op = new BitmapFactory.Options();
                op.inJustDecodeBounds = true;
                try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                    BitmapFactory.decodeStream(in, null, op);
                }
                int amostra = 1;
                while (Math.max(op.outWidth, op.outHeight) / amostra > 1800) amostra *= 2;
                BitmapFactory.Options op2 = new BitmapFactory.Options();
                op2.inSampleSize = amostra;
                Bitmap bmp;
                try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                    bmp = BitmapFactory.decodeStream(in, null, op2);
                }
                if (bmp == null) throw new Exception("não é uma imagem válida");
                int giro = 0;
                try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                    int ori = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                    if (ori == ExifInterface.ORIENTATION_ROTATE_90) giro = 90;
                    else if (ori == ExifInterface.ORIENTATION_ROTATE_180) giro = 180;
                    else if (ori == ExifInterface.ORIENTATION_ROTATE_270) giro = 270;
                } catch (Exception ignored) {}
                if (giro != 0) {
                    Matrix m = new Matrix();
                    m.postRotate(giro);
                    bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
                }
                File dest = new File(AlarmePerso.pasta(c), "fundo_" + id + ".jpg");
                try (OutputStream out = new FileOutputStream(dest)) {
                    bmp.compress(Bitmap.CompressFormat.JPEG, 88, out);
                }
                JSONObject o = AlarmePerso.obter(c, id);
                o.put("fundo", dest.getAbsolutePath());
                o.put("fundoNome", nome);
                limparVideo(o);
                o.remove("cor");
                AlarmePerso.salvar(c, id, o);
                JSObject r = new JSObject();
                r.put("nome", nome);
                call.resolve(r);
            } catch (Exception e) {
                call.reject("Não foi possível usar essa foto: " + e.getMessage());
            }
        }).start();
    }

    private void limparVideo(JSONObject o) {
        AlarmePerso.apagarArquivo(o.optString("video", ""));
        o.remove("video");
        o.remove("videoNome");
    }

    @PluginMethod
    public void escolherVideo(PluginCall call) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("video/*");
        startActivityForResult(call, i, "aoEscolherVideo");
    }

    @ActivityCallback
    private void aoEscolherVideo(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) {
            call.resolve(cancelado());
            return;
        }
        final Uri uri = result.getData().getData();
        final int id = call.getInt("id", 0);
        new Thread(() -> {
            File dest = null;
            try {
                Context c = getContext();
                String nome = nomeDe(uri);
                dest = new File(AlarmePerso.pasta(c), "video_" + id + "." + extensaoDe(nome, "mp4"));
                long total = 0;
                try (InputStream in = c.getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(dest)) {
                    if (in == null) throw new Exception("não foi possível abrir o arquivo");
                    byte[] b = new byte[65536];
                    int n;
                    while ((n = in.read(b)) > 0) {
                        total += n;
                        if (total > 50L * 1024 * 1024) throw new Exception("vídeo maior que 50 MB");
                        out.write(b, 0, n);
                    }
                }
                JSONObject o = AlarmePerso.obter(c, id);
                AlarmePerso.apagarArquivo(o.optString("fundo", ""));
                o.remove("fundo");
                o.remove("fundoNome");
                o.remove("cor");
                String antigo = o.optString("video", "");
                if (!antigo.isEmpty() && !antigo.equals(dest.getAbsolutePath())) AlarmePerso.apagarArquivo(antigo);
                o.put("video", dest.getAbsolutePath());
                o.put("videoNome", nome);
                AlarmePerso.salvar(c, id, o);
                JSObject r = new JSObject();
                r.put("nome", nome);
                call.resolve(r);
            } catch (Exception e) {
                if (dest != null) AlarmePerso.apagarArquivo(dest.getAbsolutePath());
                call.reject("Não foi possível usar esse vídeo: " + e.getMessage());
            }
        }).start();
    }

    @PluginMethod
    public void definirFundoCor(PluginCall call) {
        int id = call.getInt("id", 0);
        String cor = call.getString("cor", "");
        try {
            android.graphics.Color.parseColor(cor);
        } catch (Exception e) {
            call.reject("Cor inválida.");
            return;
        }
        JSONObject o = AlarmePerso.obter(getContext(), id);
        AlarmePerso.apagarArquivo(o.optString("fundo", ""));
        o.remove("fundo");
        o.remove("fundoNome");
        limparVideo(o);
        try { o.put("cor", cor); } catch (Exception ignored) {}
        AlarmePerso.salvar(getContext(), id, o);
        call.resolve();
    }

    @PluginMethod
    public void removerFundo(PluginCall call) {
        int id = call.getInt("id", 0);
        JSONObject o = AlarmePerso.obter(getContext(), id);
        AlarmePerso.apagarArquivo(o.optString("fundo", ""));
        o.remove("fundo");
        o.remove("fundoNome");
        limparVideo(o);
        o.remove("cor");
        AlarmePerso.salvar(getContext(), id, o);
        call.resolve();
    }

    @PluginMethod
    public void rotaPendente(PluginCall call) {
        String rota = AlarmeAgenda.prefs(getContext()).getString(AlarmeAgenda.KEY_ROTA, null);
        if (rota != null) AlarmeAgenda.prefs(getContext()).edit().remove(AlarmeAgenda.KEY_ROTA).apply();
        JSObject r = new JSObject();
        r.put("rota", rota);
        call.resolve(r);
    }

    @PluginMethod
    public void parar(PluginCall call) {
        Intent s = new Intent(getContext(), AlarmeService.class);
        s.setAction(AlarmeService.ACAO_PARAR);
        try { getContext().startService(s); } catch (Exception ignored) {}
        call.resolve();
    }
}
