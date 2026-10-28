package it.daggerheart.telecomando;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

/**
 * Servizio "al tavolo": una notifica fissa che tiene vivo il telecomando mentre il telefono è in tasca o con lo schermo
 * spento, così il collegamento con il GM resta aperto e i messaggi arrivano subito. "Lascia il tavolo" lo chiude.
 */
public class ServizioTavolo extends Service {
    static final int ID = 1;
    static final String ESCI = "it.daggerheart.telecomando.ESCI";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ESCI.equals(intent.getAction())) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            MainActivity a = MainActivity.attiva.get();
            if (a != null) a.finishAndRemoveTask();
            return START_NOT_STICKY;
        }
        MainActivity.creaCanali(this);
        String nome = intent != null ? intent.getStringExtra("nome") : null;
        PendingIntent esci = PendingIntent.getService(this, 1, new Intent(this, ServizioTavolo.class).setAction(ESCI), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new NotificationCompat.Builder(this, MainActivity.CANALE_TAVOLO)
                .setSmallIcon(R.drawable.ic_stat_tavolo)
                .setContentTitle(nome != null && !nome.isEmpty() ? nome + " è al tavolo" : "Al tavolo")
                .setContentText("Il telecomando resta collegato anche con il telefono in tasca")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(MainActivity.apriApp(this))
                .addAction(0, "Lascia il tavolo", esci)
                .build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(ID, n);
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // l'app è stata chiusa dall'elenco delle app recenti: si lascia il tavolo
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
