package app.lovable.centralrodrigo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Reagenda os alarmes depois de reiniciar o tablet, atualizar o app ou mudar hora/fuso. */
public class AlarmeBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        try {
            AlarmeAgenda.sincronizar(c, AlarmeAgenda.lerLista(c));
        } catch (Exception e) {
            android.util.Log.e("Alarme", "Falha ao reagendar", e);
        }
    }
}
