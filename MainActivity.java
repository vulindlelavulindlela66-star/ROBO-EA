package com.roboea.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.MutableContextWrapper;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class MainActivity extends Activity {
  static WebView web;
  static MutableContextWrapper wrap;
  static MainActivity current;
  static volatile boolean visible = false;
  static ValueCallback<Uri[]> fileCb;
  static String host = "";
  FrameLayout root;
  boolean askedOverlay = false, askedBatt = false;

  public static class Bridge {
    @JavascriptInterface
    public void push(String j) { BotService.update(j); }
  }

  static String readAsset(Context c, String n) {
    try {
      InputStream in = c.getAssets().open(n);
      ByteArrayOutputStream o = new ByteArrayOutputStream();
      byte[] b = new byte[4096];
      int k;
      while ((k = in.read(b)) > 0) o.write(b, 0, k);
      in.close();
      return o.toString("UTF-8");
    } catch (Exception e) { return ""; }
  }

  void makeWeb() {
    wrap = new MutableContextWrapper(getApplicationContext());
    web = new WebView(wrap);
    web.setBackgroundColor(0xFF000000);
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setDatabaseEnabled(true);
    s.setMediaPlaybackRequiresUserGesture(false);
    s.setAllowFileAccess(false);
    CookieManager.getInstance().setAcceptCookie(true);
    String url = readAsset(this, "url.txt").trim();
    try { host = Uri.parse(url).getHost(); } catch (Exception e) { host = ""; }
    web.addJavascriptInterface(new Bridge(), "RoboNative");
    web.setWebViewClient(new WebViewClient() {
      @Override
      public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
        Uri u = r.getUrl();
        String h = u.getHost();
        if (h != null && h.equals(host)) return false;
        String sc = u.getScheme();
        if ("http".equals(sc) || "https".equals(sc) || "tel".equals(sc) || "mailto".equals(sc)) {
          try {
            Intent i = new Intent(Intent.ACTION_VIEW, u);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            v.getContext().getApplicationContext().startActivity(i);
          } catch (Exception e) { }
        }
        return true;
      }
      @Override
      public void onPageFinished(WebView v, String u) {
        v.evaluateJavascript(readAsset(v.getContext().getApplicationContext(), "inject.js"), null);
      }
    });
    web.setWebChromeClient(new WebChromeClient() {
      @Override
      public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams p) {
        MainActivity a = current;
        if (a == null) return false;
        if (fileCb != null) fileCb.onReceiveValue(null);
        fileCb = cb;
        try {
          a.startActivityForResult(p.createIntent(), 11);
        } catch (Exception e) {
          fileCb = null;
          return false;
        }
        return true;
      }
    });
    web.loadUrl(url);
  }

  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    current = this;
    getWindow().setStatusBarColor(0xFF000000);
    getWindow().setNavigationBarColor(0xFF000000);
    root = new FrameLayout(this);
    root.setBackgroundColor(0xFF000000);
    setContentView(root);
    if (web == null) makeWeb();
    wrap.setBaseContext(this);
    ViewGroup p = (ViewGroup) web.getParent();
    if (p != null) p.removeView(web);
    root.addView(web, new FrameLayout.LayoutParams(-1, -1));
    startBot();
    if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[] {"android.permission.POST_NOTIFICATIONS"}, 21);
    }
  }

  void startBot() {
    Intent i = new Intent(this, BotService.class);
    if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
  }

  @Override
  protected void onResume() {
    super.onResume();
    visible = true;
    web.resumeTimers();
    startBot();
    if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this) && !askedOverlay) {
      askedOverlay = true;
      new AlertDialog.Builder(this)
          .setTitle("Floating robot")
          .setMessage("Allow \"Display over other apps\" so the round robot can float over other apps and show your trades.")
          .setPositiveButton("Allow", (d, w) -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))))
          .setNegativeButton("Not now", null)
          .show();
    } else if (Build.VERSION.SDK_INT >= 23 && !askedBatt) {
      PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
      if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
        askedBatt = true;
        new AlertDialog.Builder(this)
            .setTitle("Keep trading in the background")
            .setMessage("Allow ROBO EA to run without battery limits so the phone does not freeze the bot when the screen is off.")
            .setPositiveButton("Allow", (d, w) -> startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()))))
            .setNegativeButton("Not now", null)
            .show();
      }
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    visible = false;
    if (web != null) web.resumeTimers();
  }

  @Override
  protected void onStop() {
    super.onStop();
    if (web != null) web.resumeTimers();
  }

  @Override
  protected void onActivityResult(int rq, int rs, Intent d) {
    if (rq == 11) {
      if (fileCb != null) {
        fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(rs, d));
        fileCb = null;
      }
      return;
    }
    super.onActivityResult(rq, rs, d);
  }

  @Override
  public void onBackPressed() {
    if (web != null && web.canGoBack()) web.goBack(); else moveTaskToBack(true);
  }

  @Override
  protected void onDestroy() {
    current = null;
    visible = false;
    try { root.removeView(web); } catch (Exception e) { }
    if (wrap != null) wrap.setBaseContext(getApplicationContext());
    super.onDestroy();
  }
}
