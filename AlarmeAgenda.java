package app.lovable.centralrodrigo;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

/** Agenda os alarmes no AlarmManager do Android (como um app de despertador). */
public final class AlarmeAgenda {
    static final String PREFS = "alarme_nativo";
    static final String KEY_LISTA = "lista";
    static final String KEY_IDS = "ids";
    static final String KEY_ROTA = "rota_pendente";
    static final String ACAO_DISPARO = "app.lovable.centralrodrigo.ALARME_DISPARO";
    static final int SONECA_MIN = 10;
    static final int SONECA_OFFSET = 900000000;

    private AlarmeAgenda() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void salvarLista(Context c, JSONArray lista) {
        prefs(c).edit().putString(KEY_LISTA, lista.toString()).apply();
    }

    static JSONArray lerLista(Context c) {
        try {
            return new JSONArray(prefs(c).getString(KEY_LISTA, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static boolean contemDia(JSONArray dias, int dia) {
        for (int i = 0; i < dias.length(); i++) {
            if (dias.optInt(i, -1) == dia) return true;
        }
        return false;
    }

    /** Próximo horário (ms) depois de "depoisDe", ou -1 se não há dias marcados. */
    static long proximo(JSONObject a, long depoisDe) {
        JSONArray dias = a.optJSONArray("dias");
        if (dias == null || dias.length() == 0) return -1;
        int hora = a.optInt("hora", 7);
        int minuto = a.optInt("minuto", 0);
        for (int i = 0; i < 8; i++) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(depoisDe);
            c.add(Calendar.DAY_OF_MONTH, i);
            c.set(Calendar.HOUR_OF_DAY, hora);
            c.set(Calendar.MINUTE, minuto);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            // Calendar.DAY_OF_WEEK: domingo = 1; no app domingo = 0.
            if (c.getTimeInMillis() > depoisDe && contemDia(dias, c.get(Calendar.DAY_OF_WEEK) - 1)) {
                return c.getTimeInMillis();
            }
        }
        return -1;
    }

    private static PendingIntent intentDisparo(Context c, JSONObject a, boolean soneca) {
        int id = a.optInt("id", 0);
        Intent i = new Intent(c, AlarmeReceiver.class);
        i.setAction(ACAO_DISPARO);
        i.putExtra("id", id);
        i.putExtra("pid", a.optInt("pid", id));
        i.putExtra("titulo", a.optString("titulo", "Alarme"));
        i.putExtra("mensagem", a.optString("mensagem", ""));
        i.putExtra("rota", a.optString("rota", "/hoje"));
        i.putExtra("som", a.optString("som", "padrao"));
        i.putExtra("vibrar", a.optBoolean("vibrar", true));
        i.putExtra("soneca", soneca);
        int codigo = soneca ? SONECA_OFFSET + id : id;
        return PendingIntent.getBroadcast(c, codigo, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void agendar(Context c, JSONObject a, long quando, boolean soneca) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent abrir = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (abrir == null) abrir = new Intent(c, MainActivity.class);
        PendingIntent mostrar = PendingIntent.getActivity(c, 0, abrir, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        // setAlarmClock: tratado pelo Android como despertador (exato, ignora Soneca de bateria/Doze).
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(quando, mostrar), intentDisparo(c, a, soneca));
    }

    static void cancelar(Context c, int id) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        JSONObject vazio = new JSONObject();
        try { vazio.put("id", id); } catch (Exception ignored) {}
        am.cancel(intentDisparo(c, vazio, false));
        am.cancel(intentDisparo(c, vazio, true));
    }

    /** Faz o Android ficar exatamente com os alarmes ativos da lista. Retorna quantos foram agendados. */
    static int sincronizar(Context c, JSONArray lista) {
        try {
            JSONArray antigos = new JSONArray(prefs(c).getString(KEY_IDS, "[]"));
            for (int i = 0; i < antigos.length(); i++) cancelar(c, antigos.optInt(i));
        } catch (Exception ignored) {}
        JSONArray ids = new JSONArray();
        int n = 0;
        long agora = System.currentTimeMillis();
        for (int i = 0; i < lista.length(); i++) {
            JSONObject a = lista.optJSONObject(i);
            if (a == null || !a.optBoolean("ativo", true)) continue;
            long t = proximo(a, agora);
            if (t < 0) continue;
            agendar(c, a, t, false);
            ids.put(a.optInt("id"));
            n++;
        }
        prefs(c).edit().putString(KEY_IDS, ids.toString()).apply();
        return n;
    }

    /** Depois de tocar, marca o próximo dia do mesmo alarme. */
    static void reagendar(Context c, int id) {
        JSONArray lista = lerLista(c);
        for (int i = 0; i < lista.length(); i++) {
            JSONObject a = lista.optJSONObject(i);
            if (a != null && a.optInt("id") == id && a.optBoolean("ativo", true)) {
                long t = proximo(a, System.currentTimeMillis() + 60000);
                if (t > 0) agendar(c, a, t, false);
                return;
            }
        }
    }

    static boolean temPermissaoDeNotificar(Context c) {
        android.app.NotificationManager nm = (android.app.NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.areNotificationsEnabled();
    }

    @SuppressWarnings("unused")
    static int sdk() { return Build.VERSION.SDK_INT; }
}
