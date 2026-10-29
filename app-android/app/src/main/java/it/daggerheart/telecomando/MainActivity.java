package it.daggerheart.telecomando;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.webkit.WebViewAssetLoader;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.lang.ref.WeakReference;

/**
 * Il telecomando del personaggio. Mostra la pagina del telecomando pubblicata dal GM (il link personale del PG,
 * ricordato dall'app) e, se la rete non c'è, la copia della pagina contenuta nell'app.
 * La pagina parla con l'app attraverso l'oggetto JavaScript "DHApp" (vedi {@link Ponte}).
 */
public class MainActivity extends Activity {
    static final String ORIGINE_LOCALE = "https://appassets.androidplatform.net";
    static final String PAGINA_LOCALE = ORIGINE_LOCALE + "/assets/index.html";
    static final String CANALE_MESSAGGI = "messaggi";
    static final String CANALE_TAVOLO = "tavolo";
    static WeakReference<MainActivity> attiva = new WeakReference<>(null);

    private WebView web;
    private WebViewAssetLoader caricatore;
    private SharedPreferences prefs;
    private boolean ripiego = false;
    private int prossimaNotifica = 100;
    private volatile boolean inPrimoPiano = false;

    @Override
    protected void onCreate(Bundle stato) {
        super.onCreate(stato);
        attiva = new WeakReference<>(this);
        prefs = getSharedPreferences("telecomando", MODE_PRIVATE);
        creaCanali(this);

        FrameLayout radice = new FrameLayout(this);
        radice.setBackgroundColor(0xFF120E0B);
        web = new WebView(this);
        web.setBackgroundColor(0xFF120E0B);
        radice.addView(web, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(radice);
        // la pagina non finisce sotto la barra di stato, la barra di navigazione o la tastiera
        ViewCompat.setOnApplyWindowInsetsListener(radice, (v, ins) -> {
            Insets b = ins.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(b.left, b.top, b.right, b.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(s.getUserAgentString() + " DHTelecomandoApp/" + versione());

        caricatore = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();
        web.addJavascriptInterface(new Ponte(), "DHApp");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return caricatore.shouldInterceptRequest(r.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("dhtelecomando".equals(u.getScheme())) { gestisciIntent(new Intent(Intent.ACTION_VIEW, u), false); return true; }
                if (consentito(u)) return false;
                apriEsterno(u.toString());   // ogni altro sito si apre nel browser
                return true;
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                // senza rete: la copia della pagina contenuta nell'app, con lo stesso link
                if (r.isForMainFrame() && !ripiego && !r.getUrl().toString().startsWith(ORIGINE_LOCALE)) {
                    ripiego = true;
                    String l = prefs.getString("link", null);
                    v.loadUrl(PAGINA_LOCALE + (l != null && l.contains("#") ? l.substring(l.indexOf('#')) : ""));
                }
            }
        });

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        gestisciIntent(getIntent(), true);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        gestisciIntent(i, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        inPrimoPiano = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        inPrimoPiano = false;
        // la pagina continua a girare in tasca (il servizio "al tavolo" tiene vivo il processo)
        if (web != null) web.resumeTimers();
    }

    @Override
    protected void onDestroy() {
        if (isFinishing()) fermaServizio();
        if (attiva.get() == this) attiva = new WeakReference<>(null);
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else moveTaskToBack(true);   // indietro non chiude il telecomando: lo manda in secondo piano
    }

    // ---- link del personaggio ----
    // il link del tavolo (uno solo per tutti): https://…/#t=<stanza>&k=<chiave>[&b=<broker>]
    static boolean valido(String l) {
        return l != null && l.startsWith("https://") && l.contains("#") && l.contains("t=") && l.contains("k=");
    }

    private String linkDaIntent(Intent i) {
        if (i == null || i.getData() == null) return null;
        Uri d = i.getData();
        String l = "dhtelecomando".equals(d.getScheme()) ? d.getQueryParameter("link") : d.toString();
        return valido(l) ? l : null;
    }

    // ---- QR del tavolo inquadrato con la fotocamera dentro l'app ----
    private void scansiona() {
        IntentIntegrator ii = new IntentIntegrator(this);
        ii.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        ii.setPrompt("Inquadra il QR del tavolo che mostra il GM");
        ii.setBeepEnabled(false);
        ii.setOrientationLocked(false);
        ii.initiateScan();
    }

    @Override
    protected void onActivityResult(int richiesta, int esito, Intent dati) {
        IntentResult r = IntentIntegrator.parseActivityResult(richiesta, esito, dati);
        if (r == null) { super.onActivityResult(richiesta, esito, dati); return; }
        String testo = r.getContents();
        if (testo == null) return;   // annullato
        String l = testo.trim();
        if (l.startsWith("dhtelecomando://")) l = Uri.parse(l).getQueryParameter("link");
        if (valido(l)) {
            prefs.edit().putString("link", l).apply();
            Toast.makeText(this, "Tavolo collegato", Toast.LENGTH_SHORT).show();
            carica(l);
        } else {
            Toast.makeText(this, "Questo non è il QR del tavolo", Toast.LENGTH_LONG).show();
        }
    }

    private void gestisciIntent(Intent i, boolean avvio) {
        String nuovo = linkDaIntent(i);
        if (nuovo != null) {
            prefs.edit().putString("link", nuovo).apply();
            Toast.makeText(this, "Personaggio collegato", Toast.LENGTH_SHORT).show();
            carica(nuovo);
        } else if (avvio) {
            carica(prefs.getString("link", null));
        }
    }

    private void carica(String link) {
        if (web == null) return;
        ripiego = false;
        String dest = link != null ? link : PAGINA_LOCALE;
        String ora = web.getUrl();
        web.loadUrl(dest);
        // stessa pagina con un frammento diverso: va ricaricata, perché la pagina legge il link solo all'avvio
        if (ora != null && senzaFrammento(ora).equals(senzaFrammento(dest))) web.reload();
    }

    private static String senzaFrammento(String u) { int i = u.indexOf('#'); return i < 0 ? u : u.substring(0, i); }

    private boolean consentito(Uri u) {
        if ("appassets.androidplatform.net".equals(u.getHost())) return true;
        String l = prefs.getString("link", null);
        if (l == null) return false;
        Uri b = Uri.parse(l);
        return "https".equals(u.getScheme()) && u.getHost() != null && u.getHost().equals(b.getHost());
    }

    private void apriEsterno(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception e) { Toast.makeText(this, "Nessuna app per aprire il link", Toast.LENGTH_SHORT).show(); }
    }

    String versione() {
        try {
            PackageInfo p = getPackageManager().getPackageInfo(getPackageName(), 0);
            return p.versionName;
        } catch (Exception e) { return "?"; }
    }

    // ---- notifiche ----
    static void creaCanali(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel m = new NotificationChannel(CANALE_MESSAGGI, "Messaggi del GM", NotificationManager.IMPORTANCE_HIGH);
        m.setDescription("Messaggi privati che il GM manda al tuo personaggio");
        m.enableVibration(true);
        nm.createNotificationChannel(m);
        NotificationChannel t = new NotificationChannel(CANALE_TAVOLO, "Al tavolo", NotificationManager.IMPORTANCE_LOW);
        t.setDescription("Notifica fissa mentre il telecomando resta collegato");
        nm.createNotificationChannel(t);
    }

    static PendingIntent apriApp(Context c) {
        Intent i = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void mostraNotifica(String titolo, String testo) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        Notification n = new NotificationCompat.Builder(this, CANALE_MESSAGGI)
                .setSmallIcon(R.drawable.ic_stat_tavolo)
                .setContentTitle(titolo)
                .setContentText(testo)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(testo))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(apriApp(this))
                .build();
        nm.notify(prossimaNotifica++, n);
    }

    private void avviaServizio(String nome) {
        Intent i = new Intent(this, ServizioTavolo.class).putExtra("nome", nome);
        try { startForegroundService(i); } catch (Exception e) { /* l'app è in secondo piano: si riprova alla prossima apertura */ }
    }

    private SharedPreferences dati() { return getSharedPreferences("dati_pagina", MODE_PRIVATE); }

    private void fermaServizio() {
        stopService(new Intent(this, ServizioTavolo.class));
    }

    /** Funzioni che la pagina del telecomando può chiamare: window.DHApp.nomeFunzione(...) */
    class Ponte {
        @JavascriptInterface public boolean nativo() { return true; }
        @JavascriptInterface public String versione() { return MainActivity.this.versione(); }
        @JavascriptInterface public String link() { return prefs.getString("link", ""); }

        // memoria della pagina: la stessa per la pagina pubblicata e per la copia contenuta nell'app (senza rete)
        @JavascriptInterface public String leggi(String k) { return k == null ? null : dati().getString(k, null); }
        @JavascriptInterface public void scrivi(String k, String v) { if (k != null) dati().edit().putString(k, v).apply(); }
        @JavascriptInterface public void togli(String k) { if (k != null) dati().edit().remove(k).apply(); }

        @JavascriptInterface public void salvaLink(String l) {
            final String link = l == null ? "" : l.trim();
            runOnUiThread(() -> {
                if (!valido(link)) { Toast.makeText(MainActivity.this, "Questo non è il link di un personaggio", Toast.LENGTH_LONG).show(); return; }
                prefs.edit().putString("link", link).apply();
                carica(link);
            });
        }

        @JavascriptInterface public void scansiona() {
            runOnUiThread(MainActivity.this::scansiona);
        }

        @JavascriptInterface public void cambiaLink() {
            runOnUiThread(() -> { prefs.edit().remove("link").apply(); fermaServizio(); carica(null); });
        }

        @JavascriptInterface public void servizio(String nome, boolean attivo) {
            runOnUiThread(() -> { if (attivo) avviaServizio(nome); else fermaServizio(); });
        }

        @JavascriptInterface public void notifica(String titolo, String testo) {
            if (inPrimoPiano) return;   // con l'app davanti il messaggio si vede già sullo schermo
            runOnUiThread(() -> mostraNotifica(titolo == null ? "Telecomando" : titolo, testo == null ? "" : testo));
        }

        @JavascriptInterface public void vibra(int ms) {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) v.vibrate(VibrationEffect.createOneShot(Math.max(5, Math.min(1500, ms)), VibrationEffect.DEFAULT_AMPLITUDE));
        }

        @JavascriptInterface public void schermoAcceso(boolean acceso) {
            runOnUiThread(() -> {
                if (acceso) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        @JavascriptInterface public boolean batteriaLibera() {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        }

        @JavascriptInterface public void batteria() {
            runOnUiThread(() -> {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
                } catch (Exception e) {
                    try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); } catch (Exception e2) { /* niente da fare */ }
                }
            });
        }

        @JavascriptInterface public void apriEsterno(String url) {
            runOnUiThread(() -> MainActivity.this.apriEsterno(url));
        }
    }
}
