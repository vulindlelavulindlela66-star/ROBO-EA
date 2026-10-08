package com.roboea.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class BotService extends Service {
  static volatile String latest = "";
  static volatile long lastPush = 0;

  static void update(String j) {
    latest = j;
    lastPush = System.currentTimeMillis();
  }

  static final DecimalFormat PX = new DecimalFormat("0.#####", DecimalFormatSymbols.getInstance(Locale.US));

  WindowManager wm;
  LinearLayout box, panel, list;
  ImageView bubble;
  TextView title, status, acct, stopBtn;
  ImageView avatar;
  WindowManager.LayoutParams lp;
  Handler h = new Handler(Looper.getMainLooper());
  PowerManager.WakeLock wl;
  boolean open = false, added = false, run = false, conn = false, stopArmed = false;
  String shown = "", imgKey = "", imgData = "", name = "ROBO EA";
  int panelW = 0;
  Bitmap srcBmp;
  int srcHash = -1;
  JSONObject cur = null;

  Runnable tick = new Runnable() {
    public void run() {
      try { refresh(); } catch (Throwable t) { }
      h.postDelayed(this, 1500);
    }
  };

  int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

  @Override
  public IBinder onBind(Intent i) { return null; }

  @Override
  public void onCreate() {
    super.onCreate();
    if (Build.VERSION.SDK_INT >= 26) {
      NotificationChannel ch = new NotificationChannel("roboea", "ROBO EA running", NotificationManager.IMPORTANCE_LOW);
      ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
    }
    PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
    wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "roboea:bot");
    wl.setReferenceCounted(false);
    wl.acquire();
    h.post(tick);
  }

  @Override
  public int onStartCommand(Intent in, int flags, int id) {
    Intent oi = new Intent(this, MainActivity.class);
    oi.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
    PendingIntent pi = PendingIntent.getActivity(this, 0, oi,
        PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
    Notification.Builder nb = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "roboea") : new Notification.Builder(this);
    Notification n = nb.setContentTitle("ROBO EA is running")
        .setContentText("Tap to open. Trades are managed in the background.")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentIntent(pi)
        .setOngoing(true)
        .build();
    startForeground(1, n);
    ensureBubble();
    return START_STICKY;
  }

  void ensureBubble() {
    if (added) return;
    if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) return;
    try {
      build();
      added = true;
    } catch (Throwable t) { added = false; }
  }

  TextView tv(String s, int sp, int color, boolean bold) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(sp);
    t.setTextColor(color);
    if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    return t;
  }

  TextView btn(String s, int color) {
    TextView t = tv(s, 13, color, true);
    t.setGravity(Gravity.CENTER);
    t.setPadding(dp(8), dp(9), dp(8), dp(9));
    GradientDrawable g = new GradientDrawable();
    g.setColor(0xFF1C1F24);
    g.setCornerRadius(dp(10));
    g.setStroke(dp(1), color);
    t.setBackground(g);
    return t;
  }

  void build() {
    wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    box = new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    bubble = new ImageView(this);
    box.addView(bubble, new LinearLayout.LayoutParams(dp(62), dp(62)));

    panel = new LinearLayout(this);
    panel.setOrientation(LinearLayout.VERTICAL);
    GradientDrawable g = new GradientDrawable();
    g.setColor(0xF2101214);
    g.setCornerRadius(dp(16));
    g.setStroke(dp(1), 0xFF2DFFA0);
    panel.setBackground(g);
    panel.setPadding(dp(12), dp(10), dp(12), dp(10));
    LinearLayout head = new LinearLayout(this);
    head.setOrientation(LinearLayout.HORIZONTAL);
    head.setGravity(Gravity.CENTER_VERTICAL);
    head.setPadding(dp(8), dp(8), dp(8), dp(8));
    GradientDrawable hg = new GradientDrawable();
    hg.setColor(0xFF171A1F);
    hg.setCornerRadius(dp(34));
    head.setBackground(hg);
    avatar = new ImageView(this);
    head.addView(avatar, new LinearLayout.LayoutParams(dp(44), dp(44)));
    LinearLayout tcol = new LinearLayout(this);
    tcol.setOrientation(LinearLayout.VERTICAL);
    title = tv("ROBO EA", 15, 0xFFFFFFFF, true);
    title.setSingleLine(true);
    title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    status = tv("", 13, 0xFFB5BAC1, false);
    status.setSingleLine(true);
    status.setEllipsize(android.text.TextUtils.TruncateAt.END);
    tcol.addView(title);
    tcol.addView(status);
    LinearLayout.LayoutParams tcl = new LinearLayout.LayoutParams(0, -2, 1f);
    tcl.leftMargin = dp(10);
    head.addView(tcol, tcl);
    TextView closeBtn = tv("\u2715", 16, 0xFFFFFFFF, true);
    closeBtn.setGravity(Gravity.CENTER);
    GradientDrawable cg = new GradientDrawable();
    cg.setColor(0xFF2A2E35);
    cg.setShape(GradientDrawable.OVAL);
    closeBtn.setBackground(cg);
    head.addView(closeBtn, new LinearLayout.LayoutParams(dp(38), dp(38)));
    closeBtn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) { toggle(); }
    });
    panel.addView(head);
    acct = tv("", 12, 0xFFB5BAC1, false);
    acct.setPadding(dp(6), dp(8), dp(6), dp(2));
    panel.addView(acct);
    ScrollView sv = new ScrollView(this);
    list = new LinearLayout(this);
    list.setOrientation(LinearLayout.VERTICAL);
    sv.addView(list);
    panel.addView(sv, new LinearLayout.LayoutParams(-1, dp(270)));

    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setPadding(0, dp(10), 0, 0);
    TextView openBtn = btn("Open app", 0xFF2DFFA0);
    stopBtn = btn("Stop bot", 0xFFFF5470);
    LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, -2, 1f);
    bl.rightMargin = dp(6);
    row.addView(openBtn, bl);
    row.addView(stopBtn, new LinearLayout.LayoutParams(0, -2, 1f));
    panel.addView(row);
    openBtn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        Intent i = new Intent(BotService.this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(i);
      }
    });
    stopBtn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        if (!stopArmed) {
          stopArmed = true;
          stopBtn.setText("Tap again to stop");
          h.postDelayed(new Runnable() {
            public void run() { stopArmed = false; stopBtn.setText("Stop bot"); }
          }, 3000);
          return;
        }
        stopArmed = false;
        stopBtn.setText("Stop bot");
        WebView w = MainActivity.web;
        if (w != null) {
          w.evaluateJavascript("try{state.robots.forEach(function(r){r.running=false});saveR();botCmd(false);keepAwake(false);render();}catch(e){}", null);
        }
      }
    });
    panel.setVisibility(View.GONE);
    panelW = Math.min(dp(330), getResources().getDisplayMetrics().widthPixels - dp(16));
    LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(panelW, -2);
    pl.topMargin = dp(6);
    box.addView(panel, pl);

    lp = new WindowManager.LayoutParams(-2, -2,
        Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT);
    lp.gravity = Gravity.TOP | Gravity.START;
    lp.x = dp(8);
    lp.y = dp(160);

    bubble.setOnTouchListener(new View.OnTouchListener() {
      float dx, dy;
      int sx, sy;
      boolean moved;
      public boolean onTouch(View v, MotionEvent e) {
        switch (e.getAction()) {
          case MotionEvent.ACTION_DOWN:
            sx = lp.x; sy = lp.y; dx = e.getRawX(); dy = e.getRawY(); moved = false;
            return true;
          case MotionEvent.ACTION_MOVE:
            float mx = e.getRawX() - dx, my = e.getRawY() - dy;
            if (Math.abs(mx) > dp(6) || Math.abs(my) > dp(6)) moved = true;
            if (moved) {
              lp.x = Math.max(0, sx + (int) mx);
              lp.y = Math.max(0, sy + (int) my);
              try { wm.updateViewLayout(box, lp); } catch (Exception ex) { }
            }
            return true;
          case MotionEvent.ACTION_UP:
            if (!moved) toggle();
            return true;
        }
        return false;
      }
    });
    wm.addView(box, lp);
  }

  void toggle() {
    open = !open;
    panel.setVisibility(open ? View.VISIBLE : View.GONE);
    if (open) {
      fill(System.currentTimeMillis() - lastPush > 15000);
      DisplayMetrics m = getResources().getDisplayMetrics();
      int need = dp(62 + 6 + 440);
      if (lp.y + need > m.heightPixels) lp.y = Math.max(0, m.heightPixels - need);
      if (lp.x + panelW > m.widthPixels) lp.x = Math.max(0, m.widthPixels - panelW);
    }
    try { wm.updateViewLayout(box, lp); } catch (Exception e) { }
  }

  void parse(String j) {
    try {
      cur = new JSONObject(j);
      run = cur.optBoolean("run");
      conn = cur.optBoolean("conn");
      name = cur.optString("name", "ROBO EA");
      String im = cur.optString("img", "");
      if (im.startsWith("data:image")) imgData = im;
    } catch (Exception e) { cur = null; }
  }

  void refresh() {
    if (!added) ensureBubble();
    if (!added) return;
    boolean hide = MainActivity.visible;
    box.setVisibility(hide ? View.GONE : View.VISIBLE);
    if (hide && open) { open = false; panel.setVisibility(View.GONE); }
    boolean stale = System.currentTimeMillis() - lastPush > 15000;
    String j = latest;
    if (!j.equals(shown)) { shown = j; parse(j); }
    int ring = !run ? 0xFF8A8F98 : (stale ? 0xFFFFB020 : 0xFF2DFFA0);
    if (srcBmp == null || srcHash != imgData.hashCode()) {
      srcHash = imgData.hashCode();
      srcBmp = null;
      if (imgData.length() > 0) {
        try {
          byte[] d = Base64.decode(imgData.substring(imgData.indexOf(',') + 1), Base64.DEFAULT);
          srcBmp = BitmapFactory.decodeByteArray(d, 0, d.length);
        } catch (Throwable t) { srcBmp = null; }
      }
      if (srcBmp == null) srcBmp = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
      imgKey = "";
    }
    String key = ring + ":" + srcHash;
    if (!key.equals(imgKey) && srcBmp != null) {
      imgKey = key;
      bubble.setImageBitmap(circle(srcBmp, dp(62), ring));
      avatar.setImageBitmap(circle(srcBmp, dp(44), ring));
    }
    if (open) fill(stale);
  }

  static Bitmap circle(Bitmap src, int size, int ring) {
    Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(out);
    float r = size / 2f, rw = size * 0.07f;
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    p.setColor(0xFF111111);
    c.drawCircle(r, r, r, p);
    int w = src.getWidth(), hh = src.getHeight(), m = Math.min(w, hh);
    Bitmap sq = Bitmap.createBitmap(src, (w - m) / 2, (hh - m) / 2, m, m);
    Bitmap sc = Bitmap.createScaledBitmap(sq, size, size, true);
    Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
    sp.setShader(new BitmapShader(sc, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
    c.drawCircle(r, r, r - rw, sp);
    Paint rp = new Paint(Paint.ANTI_ALIAS_FLAG);
    rp.setStyle(Paint.Style.STROKE);
    rp.setStrokeWidth(rw);
    rp.setColor(ring);
    c.drawCircle(r, r, r - rw / 2, rp);
    return out;
  }

  static String m(double v) { return Double.isNaN(v) ? "-" : String.format(Locale.US, "%.2f", v); }
  static String sgn(double v) { return Double.isNaN(v) ? "" : (v >= 0 ? "+" : "") + String.format(Locale.US, "%.2f", v); }
  static String px(double v) { return Double.isNaN(v) ? "-" : PX.format(v); }

  LinearLayout card() {
    LinearLayout c = new LinearLayout(this);
    c.setOrientation(LinearLayout.VERTICAL);
    c.setPadding(dp(16), dp(14), dp(16), dp(14));
    GradientDrawable g = new GradientDrawable();
    g.setColor(0xFF171A1F);
    g.setCornerRadius(dp(18));
    c.setBackground(g);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.topMargin = dp(8);
    c.setLayoutParams(p);
    return c;
  }

  TextView label(String s) {
    TextView t = tv(s, 11, 0xFFB5BAC1, true);
    t.setLetterSpacing(0.12f);
    return t;
  }

  static int sideColor(String side) { return "BUY".equalsIgnoreCase(side) ? 0xFF2DFFA0 : 0xFFFF5470; }

  static String levels(double entry, double sl, double tp) {
    StringBuilder d = new StringBuilder();
    if (!Double.isNaN(entry) && entry > 0) d.append("Entry ").append(px(entry));
    if (!Double.isNaN(sl) && sl > 0) { if (d.length() > 0) d.append(" \u00B7 "); d.append("SL ").append(px(sl)); }
    if (!Double.isNaN(tp) && tp > 0) { if (d.length() > 0) d.append(" \u00B7 "); d.append("TP ").append(px(tp)); }
    return d.toString();
  }

  void fill(boolean stale) {
    int openN = 0;
    JSONArray tr = cur == null ? null : cur.optJSONArray("trades");
    if (tr != null) for (int i = 0; i < tr.length(); i++) {
      JSONObject t = tr.optJSONObject(i);
      if (t != null && !"closed".equals(t.optString("st"))) openN++;
    }
    title.setText(name);
    status.setText(stale ? "No updates from the app" : (!run ? "Stopped" : (openN > 0 ? "Managing " + openN + " open trade" + (openN > 1 ? "s" : "") : "Watching markets")));
    status.setTextColor(stale ? 0xFFFFB020 : 0xFFB5BAC1);
    list.removeAllViews();
    if (cur == null) { acct.setText("Waiting for the app. Open ROBO EA once."); return; }
    String c = cur.optString("cur", "");
    if (conn) acct.setText("Balance " + m(cur.optDouble("bal")) + " " + c + "   \u00B7   Equity " + m(cur.optDouble("eq")) + "   \u00B7   P/L " + sgn(cur.optDouble("pl")));
    else acct.setText("MetaTrader account not connected");
    int count = 0;
    if (tr != null) for (int i = 0; i < tr.length(); i++) {
      JSONObject t = tr.optJSONObject(i);
      if (t != null && !"closed".equals(t.optString("st"))) { addTrade(t, false); count++; }
    }
    JSONArray sg = cur.optJSONArray("sigs");
    if (sg != null) for (int i = 0; i < sg.length(); i++) {
      JSONObject s = sg.optJSONObject(i);
      if (s != null) { addSignal(s); count++; }
    }
    int closedN = 0;
    if (tr != null) for (int i = 0; i < tr.length() && closedN < 3; i++) {
      JSONObject t = tr.optJSONObject(i);
      if (t != null && "closed".equals(t.optString("st"))) { addTrade(t, true); closedN++; count++; }
    }
    if (count == 0) {
      LinearLayout e = card();
      e.addView(label("AI TRADE ANALYSIS"));
      e.addView(tv(run ? "Watching markets. No setup yet." : "Bot is stopped.", 14, 0xFFE6E8EB, false));
      list.addView(e);
    }
  }

  void addTrade(JSONObject t, boolean closed) {
    LinearLayout c = card();
    c.addView(label(closed ? "CLOSED TRADE" : "OPEN TRADE"));
    String side = t.optString("side");
    c.addView(tv(t.optString("s") + " " + side.toUpperCase(Locale.US), 22, closed ? 0xFF9AA0A6 : sideColor(side), true));
    String lv = levels(t.optDouble("entry"), t.optDouble("sl"), t.optDouble("tp"));
    if (lv.length() > 0) c.addView(tv(lv, 14, 0xFFFFFFFF, true));
    double pl = t.optDouble("pl");
    String line = "P/L " + sgn(pl);
    if ("locked".equals(t.optString("st"))) line += "   \uD83D\uDD12 SL above entry, profit locked";
    TextView p = tv(line, 13, (Double.isNaN(pl) || pl >= 0) ? 0xFF2DFFA0 : 0xFFFF5470, false);
    p.setPadding(0, dp(4), 0, 0);
    c.addView(p);
    String note = t.optString("note", "");
    if (note.length() > 0 && !"null".equals(note)) c.addView(tv(note, 12, 0xFF8A8F98, false));
    list.addView(c);
  }

  void addSignal(JSONObject s) {
    LinearLayout c = card();
    c.addView(label("AI TRADE ANALYSIS"));
    String side = s.optString("side", "");
    if (side.length() > 0 && !"null".equals(side)) {
      c.addView(tv(s.optString("s") + " " + side.toUpperCase(Locale.US), 22, sideColor(side), true));
      String lv = levels(s.optDouble("entry"), s.optDouble("sl"), s.optDouble("tp"));
      if (lv.length() > 0) c.addView(tv(lv, 14, 0xFFFFFFFF, true));
    } else {
      c.addView(tv(s.optString("s"), 20, 0xFFFFFFFF, true));
    }
    TextView g = tv(s.optString("sig"), 12, 0xFF8A8F98, false);
    g.setPadding(0, dp(4), 0, 0);
    c.addView(g);
    list.addView(c);
  }

  @Override
  public void onDestroy() {
    h.removeCallbacks(tick);
    try { if (added) wm.removeView(box); } catch (Exception e) { }
    try { if (wl != null && wl.isHeld()) wl.release(); } catch (Exception e) { }
    super.onDestroy();
  }
}
