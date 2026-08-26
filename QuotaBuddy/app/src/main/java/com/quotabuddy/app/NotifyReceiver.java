package com.quotabuddy.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.time.LocalDate;

/** Controlla localmente (nessun server) se ci sono pagamenti in ritardo e mostra una notifica.
 * Si ri-arma da solo ad ogni controllo e al riavvio del telefono, leggendo l'intervallo scelto nelle Impostazioni. */
public class NotifyReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "quotabuddy_late";
    private static final int REQUEST_CODE = 900;

    @Override public void onReceive(Context ctx, Intent intent) {
        SharedPreferences prefs = ctx.getSharedPreferences("quotabuddy_prefs", Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("notify_enabled", false);
        if (!enabled) return;
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) { schedule(ctx); return; }

        Db db = new Db(ctx);
        Ledger ledger = new Ledger(db);
        LocalDate today = LocalDate.now();
        int lateCount = 0; long lateTotal = 0;
        for (Models.Subscription s : db.subscriptions(false)) {
            Models.SubscriptionSummary z = ledger.summary(s, today);
            lateCount += z.lateMembers; lateTotal += z.outstandingCents;
        }
        if (lateCount > 0) {
            createChannel(ctx);
            String msg = (lateCount == 1 ? "1 persona è in ritardo" : lateCount + " persone sono in ritardo")
                    + " con un pagamento (mancano " + String.format(java.util.Locale.ITALY, "%.2f", lateTotal / 100.0) + " €)";
            Notification n = new Notification.Builder(ctx, CHANNEL_ID)
                    .setContentTitle("QuotaBuddy")
                    .setContentText(msg)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setAutoCancel(true)
                    .build();
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(REQUEST_CODE, n);
        }
        schedule(ctx);
    }

    private void createChannel(Context ctx) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel c = new NotificationChannel(CHANNEL_ID, "Pagamenti in ritardo", NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(c);
        }
    }

    public static void schedule(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences("quotabuddy_prefs", Context.MODE_PRIVATE);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(ctx, NotifyReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, REQUEST_CODE, i, flags);
        if (am == null) return;
        am.cancel(pi);
        boolean enabled = prefs.getBoolean("notify_enabled", false);
        if (!enabled) return;
        int days = Math.max(1, prefs.getInt("notify_interval_days", 3));
        long intervalMs = days * 24L * 60L * 60L * 1000L;
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + intervalMs, intervalMs, pi);
    }
}
