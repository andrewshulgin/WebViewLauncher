package com.andrewshulgin.webviewlauncher;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import org.ini4j.InvalidFileFormatException;
import org.ini4j.Wini;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class WebViewActivity extends Activity {
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 42;
    String url;
    String ssid;
    String psk;
    boolean hiddenNetwork = false;
    WifiManager wifiManager;
    WifiManager.WifiLock wifiLock;
    PowerManager powerManager;
    PowerManager.WakeLock wakeLock;
    Timer configReadTimer;
    WebView web;

    public boolean checkPermissionsExternalStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return this.checkSelfPermission(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED &&
                    getApplicationContext().checkSelfPermission(
                            android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                            PackageManager.PERMISSION_GRANTED;
        }
        return false;
    }

    public void requestPermissionsForExternalStorage() {
        try {
            ActivityCompat.requestPermissions((Activity) this,
                    new String[]{
                            android.Manifest.permission.READ_EXTERNAL_STORAGE,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    },
                    STORAGE_PERMISSION_REQUEST_CODE);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    void createNewConfig(File iniFile) {
        try {
            if (iniFile.createNewFile())
                Toast.makeText(getApplicationContext(),
                        String.format("Created a new %s", iniFile.getAbsolutePath()),
                        Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(),
                    String.format("Failed to create %s", iniFile.getAbsolutePath()),
                    Toast.LENGTH_LONG).show();
        }
    }

    void processConfig() {
        boolean isUrlUpdated = false;
        Wini ini = null;
        File iniFile = new File(Environment.getExternalStorageDirectory(),
                "WebViewLauncher.ini");
        if (!iniFile.exists())
            createNewConfig(iniFile);

        try {
            ini = new Wini(iniFile);
        } catch (InvalidFileFormatException e) {
            Toast.makeText(getApplicationContext(),
                    String.format("Invalid file format %s", iniFile.getAbsolutePath()),
                    Toast.LENGTH_LONG).show();
            if (iniFile.delete())
                createNewConfig(iniFile);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(),
                    String.format("Failed to open %s", iniFile.getAbsolutePath()),
                    Toast.LENGTH_LONG).show();
        }

        if (ini != null) {
            try {
                if (ini.get("CONFIG", "url") == null)
                    ini.put("CONFIG", "url",
                            getResources().getString(R.string.default_url));
                if (ini.get("WIFI", "ssid") == null)
                    ini.put("WIFI", "ssid", "");
                if (ini.get("WIFI", "psk") == null)
                    ini.put("WIFI", "psk", "");
                if (ini.get("WIFI", "hidden") == null)
                    ini.put("WIFI", "hidden", "false");
                ini.store();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(getApplicationContext(),
                        String.format("Failed to set defaults in %s", iniFile.getAbsolutePath()),
                        Toast.LENGTH_LONG).show();
            }
            if (!Objects.equals(url, ini.get("CONFIG", "url")))
                isUrlUpdated = true;
            url = ini.get("CONFIG", "url");
            ssid = ini.get("WIFI", "ssid");
            psk = ini.get("WIFI", "psk");
            hiddenNetwork = ini.get("WIFI", "hidden").equalsIgnoreCase(
                    "true");

            if (ssid.length() > 0) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();

                if (!wifiManager.isWifiEnabled() ||
                        !wifiInfo.getSSID().equals(String.format("\"%s\"", ssid))) {
                    wifiManager.disableNetwork(wifiInfo.getNetworkId());
                    wifiManager.disconnect();

                    WifiConfiguration wifiConfig = new WifiConfiguration();
                    wifiConfig.SSID = String.format("\"%s\"", ssid);
                    if (psk != null && psk.length() >= 8) {
                        wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                        wifiConfig.preSharedKey = String.format("\"%s\"", psk);
                    } else {
                        wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                    }
                    wifiConfig.hiddenSSID = hiddenNetwork;

                    int netId = wifiManager.addNetwork(wifiConfig);
                    wifiManager.setWifiEnabled(true);
                    wifiManager.disconnect();
                    wifiManager.enableNetwork(netId, true);
                    wifiManager.reconnect();
                }
            }

            if (isUrlUpdated && web != null)
                web.loadUrl(url);
        }

        if (configReadTimer == null) {
            configReadTimer = new Timer();
            configReadTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    new android.os.Handler(Looper.getMainLooper()).postDelayed(
                            WebViewActivity.this::processConfig, 100);
                }
            }, 0, 5000);
        }
    }

    @SuppressLint("WakelockTimeout")
    public void toggleWakeLock(boolean state) {
        runOnUiThread(() -> {
            if (state) {
                wakeLock.acquire();
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                wakeLock.release();
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
    }

    public boolean isExternalPowerConnected() {
        Intent intent = getApplicationContext().registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        return plugged == BatteryManager.BATTERY_PLUGGED_AC ||
                plugged == BatteryManager.BATTERY_PLUGGED_USB;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE && grantResults.length >= 2 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            processConfig();
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "WakelockTimeout"})
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        wifiLock = wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF, "WebViewLauncher::WifiLock");
        wifiLock.acquire();

        powerManager = (PowerManager) getApplicationContext().getSystemService(
                Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "WebViewLauncher::WakeLock");
        wakeLock.acquire();

        if (checkPermissionsExternalStorage())
            processConfig();
        else
            requestPermissionsForExternalStorage();

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        final int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

        getWindow().getDecorView().setSystemUiVisibility(flags);
        final View decorView = getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener(visibility -> {
            if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                decorView.setSystemUiVisibility(flags);
            }
        });

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        setContentView(R.layout.main);

        web = (WebView) findViewById(R.id.webview);
        web.addJavascriptInterface(new WebAppInterface(this), "Android");
        web.setSystemUiVisibility(flags);
        web.clearCache(true);
        web.getSettings().setAppCacheEnabled(false);
        web.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setMediaPlaybackRequiresUserGesture(false);
        web.getSettings().setLoadWithOverviewMode(true);
        web.getSettings().setUseWideViewPort(true);
        web.setLongClickable(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                Log.e("WebViewLauncher", String.format("SSL Error: %s", error.toString()));
                new android.os.Handler(Looper.getMainLooper()).postDelayed(() -> view.loadUrl(url),
                        10000);
                super.onReceivedSslError(view, handler, error);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description,
                                        String failingUrl) {
                Log.e("WebViewLauncher", String.format("WebView Error: %s", description));
                new android.os.Handler(Looper.getMainLooper()).postDelayed(() -> view.loadUrl(url),
                        10000);
                super.onReceivedError(view, errorCode, description, failingUrl);
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                            WebResourceResponse errorResponse) {
                Log.e("WebViewLauncher", String.format("WebView HTTP Error: %s",
                        errorResponse.toString()));
                new android.os.Handler(Looper.getMainLooper()).postDelayed(() -> view.loadUrl(url),
                        10000);
                super.onReceivedHttpError(view, request, errorResponse);
            }
        });
        web.loadUrl(url);
    }
}
