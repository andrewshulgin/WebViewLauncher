package com.andrewshulgin.webviewlauncher;

import android.webkit.JavascriptInterface;

public class WebAppInterface {
    WebViewActivity activity;

    WebAppInterface(WebViewActivity a) {
        activity = a;
    }

    @JavascriptInterface
    public void toggleWakeLock(boolean state) {
        activity.toggleWakeLock(state);
    }

    @JavascriptInterface
    public boolean isExternalPowerConnected() {
        return activity.isExternalPowerConnected();
    }
}
