package app.lovable.centralrodrigo;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;

/** Música e fundo de cada alarme, guardados só neste tablet (por id do alarme). */
public final class AlarmePerso {
    static final String KEY = "personalizacao";

    private AlarmePerso() {}

    static JSONObject todos(Context c) {
        try {
            return new JSONObject(AlarmeAgenda.prefs(c).getString(KEY, "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    static JSONObject obter(Context c, int id) {
        JSONObject o = todos(c).optJSONObject(String.valueOf(id));
        return o == null ? new JSONObject() : o;
    }

    static void salvar(Context c, int id, JSONObject o) {
        try {
            JSONObject t = todos(c);
            if (o.length() == 0) t.remove(String.valueOf(id));
            else t.put(String.valueOf(id), o);
            AlarmeAgenda.prefs(c).edit().putString(KEY, t.toString()).apply();
        } catch (Exception ignored) {}
    }

    static File pasta(Context c) {
        File f = new File(c.getFilesDir(), "alarmes");
        f.mkdirs();
        return f;
    }

    /** Caminho do arquivo do campo, ou null se não existe mais. */
    static String caminhoValido(JSONObject o, String campo) {
        String p = o.optString(campo, "");
        if (p.isEmpty()) return null;
        return new File(p).exists() ? p : null;
    }

    static void apagarArquivo(String caminho) {
        if (caminho == null || caminho.isEmpty()) return;
        try { new File(caminho).delete(); } catch (Exception ignored) {}
    }
}
