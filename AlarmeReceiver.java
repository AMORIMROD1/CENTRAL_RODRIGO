package app.lovable.centralrodrigo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

/** Recebe o aviso do Android na hora do alarme e liga o serviço que toca. */
public class AlarmeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Bundle extras = i.getExtras();
        Intent s = new Intent(c, AlarmeService.class);
        s.setAction(AlarmeService.ACAO_TOCAR);
        if (extras != null) s.putExtras(extras);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s);
            else c.startService(s);
        } catch (Exception e) {
            android.util.Log.e("Alarme", "Falha ao iniciar serviço do alarme", e);
        }
        if (!i.getBooleanExtra("soneca", false)) {
            AlarmeAgenda.reagendar(c, i.getIntExtra("id", 0));
        }
    }
}
