package app.lovable.centralrodrigo;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONObject;

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
