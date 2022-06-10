# WebViewLauncher

Simple Android WebView Launcher

## Configuration

`WebViewLauncher.ini` is created in the root of external storage if absent.

The configuration file is being reloaded each 5 seconds.

The format follows:

```ini
[CONFIG]
url = https://example.com/

[WIFI]
ssid = mtkguest
psk =
hidden = false
```

`url` parameter under the `CONFIG` section describes which URL to open in WebView.

`WIFI` section contains WiFi configuration values.

Leave `psk` empty in order to connect to an open network.

