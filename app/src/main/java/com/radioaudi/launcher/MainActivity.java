package com.radioaudi.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class MainActivity extends Activity {
    static final int RED = Color.parseColor("#E4291C");
    static final String[][] DEFAULTS = {
        {"yt", "com.google.android.youtube"},
        {"chrome", "com.android.chrome"},
        {"store", "com.android.vending"},
        {"maps", "com.google.android.apps.maps"}
    };

    SharedPreferences prefs;
    FrameLayout root;
    Typeface audi;
    boolean showingApps;
    TextView nowText;
    TextView btText;
    ImageView playIcon;
    ImageView btPlayIcon;
    long musicHold;
    long btHold;
    boolean musicHoldPlay;
    boolean btHoldPlay;
    final Handler handler = new Handler();
    final Runnable tick = new Runnable() {
        public void run() {
            updateNow();
            handler.postDelayed(this, 2000);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("lanzador", MODE_PRIVATE);
        try {
            audi = Typeface.createFromAsset(getAssets(), "fonts/Michroma-Regular.ttf");
        } catch (Exception e) {
            audi = Typeface.DEFAULT_BOLD;
        }
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);
        showHome();
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        showHome();
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(tick);
        if (!showingApps) handler.post(tick);
    }

    @Override protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    @Override public void onBackPressed() {
        if (showingApps) showHome();
    }

    int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(sp);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    View line(boolean vertical) {
        View v = new View(this);
        v.setBackgroundColor(RED);
        v.setLayoutParams(vertical ? new LinearLayout.LayoutParams(dp(1), -1) : new LinearLayout.LayoutParams(-1, dp(1)));
        return v;
    }

    LinearLayout.LayoutParams weight(float w, boolean horizontalParent) {
        return horizontalParent ? new LinearLayout.LayoutParams(0, -1, w) : new LinearLayout.LayoutParams(-1, 0, w);
    }

    View btn(int res, String desc, int box, int ic, boolean ring, String label, final Runnable tap, final Runnable hold) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        FrameLayout f = new FrameLayout(this);
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(box), dp(box)));
        if (ring) {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setStroke(dp(1), RED);
            f.setBackground(g);
        }
        ImageView i = new ImageView(this);
        i.setImageResource(res);
        f.addView(i, new FrameLayout.LayoutParams(dp(ic), dp(ic), Gravity.CENTER));
        col.addView(f);
        if (label != null) {
            TextView t = text(label, 13);
            t.setPadding(0, dp(2), 0, 0);
            col.addView(t);
        }
        col.setContentDescription(desc);
        col.setClickable(true);
        col.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { tap.run(); } });
        if (hold != null) {
            col.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) { hold.run(); return true; }
            });
        }
        return col;
    }

    View pad(View v) {
        v.setPadding(dp(8), 0, dp(8), 0);
        return v;
    }

    Runnable opener(final String key) { return new Runnable() { public void run() { open(key); } }; }
    Runnable chooser(final String key) { return new Runnable() { public void run() { choose(key); } }; }
    Runnable toggler(final boolean bt) { return new Runnable() { public void run() { toggle(bt); } }; }
    Runnable skipper(final boolean bt, final boolean next) { return new Runnable() { public void run() { skip(bt, next); } }; }
    Runnable volume(final int dir) { return new Runnable() { public void run() { adjustVolume(dir); } }; }

    LinearLayout mediaPanel(String title, final String key, final boolean bt) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setGravity(Gravity.CENTER);
        p.setPadding(dp(8), 0, dp(8), 0);

        TextView t = text(title + "  ›", 15);
        t.setTypeface(audi);
        t.setLetterSpacing(0.1f);
        t.setPadding(dp(16), dp(6), dp(16), dp(6));
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { open(key); } });
        t.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) { choose(key); return true; }
        });
        p.addView(t);

        TextView now = text("", 15);
        now.setSingleLine(true);
        now.setEllipsize(TextUtils.TruncateAt.END);
        now.setPadding(dp(8), dp(4), dp(8), dp(4));
        now.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!hasNotifAccess()) openNotifAccess();
            }
        });
        p.addView(now);

        LinearLayout ctrl = new LinearLayout(this);
        ctrl.setGravity(Gravity.CENTER);
        ctrl.setPadding(0, dp(12), 0, 0);
        ctrl.addView(pad(btn(R.drawable.ic_prev, "Anterior", 52, 28, false, null, skipper(bt, false), null)));
        View play = pad(btn(R.drawable.ic_play, "Reproducir o pausar", 64, 30, true, null, toggler(bt), chooser(key)));
        ImageView icon = (ImageView) ((ViewGroup) ((ViewGroup) play).getChildAt(0)).getChildAt(0);
        ctrl.addView(play);
        ctrl.addView(pad(btn(R.drawable.ic_next, "Siguiente", 52, 28, false, null, skipper(bt, true), null)));
        p.addView(ctrl);

        if (bt) { btText = now; btPlayIcon = icon; } else { nowText = now; playIcon = icon; }
        return p;
    }

    void showHome() {
        showingApps = false;
        root.removeAllViews();

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.VERTICAL);
        dock.addView(btn(R.drawable.ic_radio, "Radio", 52, 28, false, null, opener("radio"), chooser("radio")), weight(1, false));
        dock.addView(btn(R.drawable.ic_bt, "Bluetooth", 52, 28, false, null, opener("bt"), chooser("bt")), weight(1, false));
        dock.addView(btn(R.drawable.ic_apps, "Aplicaciones", 52, 26, true, null, new Runnable() { public void run() { showApps(); } }, null), weight(1, false));
        dock.addView(btn(R.drawable.ic_settings, "Ajustes", 52, 28, false, null, new Runnable() {
            public void run() { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        }, null), weight(1, false));
        main.addView(dock, new LinearLayout.LayoutParams(dp(76), -1));
        main.addView(line(true));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.addView(mediaPanel("MÚSICA", "music", false), weight(1, true));
        top.addView(line(true));
        top.addView(mediaPanel("BLUETOOTH", "bt", true), weight(1, true));
        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        TextView b1 = text("ENJOY THE RIDE", 11);
        b1.setTypeface(audi);
        b1.setLetterSpacing(0.15f);
        b1.setGravity(Gravity.CENTER);
        banner.addView(b1, new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout badge = new LinearLayout(this);
        badge.setOrientation(LinearLayout.HORIZONTAL);
        badge.setGravity(Gravity.CENTER);
        ImageView rings = new ImageView(this);
        rings.setImageResource(R.drawable.ic_rings);
        TextView b2 = text("A3 2.0T DSG", 11);
        b2.setTypeface(audi);
        b2.setLetterSpacing(0.15f);
        b2.setTextColor(RED);
        b2.setPadding(dp(12), 0, 0, 0);
        badge.addView(rings, new LinearLayout.LayoutParams(dp(60), dp(22)));
        badge.addView(b2);
        banner.addView(badge, new LinearLayout.LayoutParams(0, -1, 1f));
        content.addView(banner, weight(0.45f, false));
        content.addView(top, weight(2.55f, false));
        content.addView(line(false));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout apps4 = new LinearLayout(this);
        apps4.setOrientation(LinearLayout.HORIZONTAL);
        apps4.addView(btn(R.drawable.ic_yt, "YouTube", 34, 28, false, "YouTube", opener("yt"), chooser("yt")), weight(1, true));
        apps4.addView(btn(R.drawable.ic_chrome, "Chrome", 34, 28, false, "Chrome", opener("chrome"), chooser("chrome")), weight(1, true));
        apps4.addView(btn(R.drawable.ic_pin, "Mapas", 34, 28, false, "Mapas", opener("maps"), chooser("maps")), weight(1, true));
        apps4.addView(btn(R.drawable.ic_store, "Play Store", 34, 28, false, "Play Store", opener("store"), chooser("store")), weight(1, true));
        bottom.addView(apps4, weight(3.1f, true));
        bottom.addView(line(true));

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER);
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm a");
        clock.setFormat24Hour("h:mm a");
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(26);
        clock.setGravity(Gravity.CENTER);
        clock.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) { showDiag(); return true; }
        });
        tools.addView(clock);
        bottom.addView(tools, weight(1.9f, true));

        content.addView(bottom, weight(0.8f, false));
        main.addView(content, weight(1, true));
        root.addView(main, new FrameLayout.LayoutParams(-1, -1));

        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    void showApps() {
        showingApps = true;
        handler.removeCallbacks(tick);
        nowText = null;
        btText = null;
        playIcon = null;
        btPlayIcon = null;
        root.removeAllViews();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), 0, dp(16), 0);
        TextView back = text("←  Inicio", 18);
        back.setPadding(dp(8), dp(8), dp(24), dp(8));
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showHome(); } });
        bar.addView(back);
        col.addView(bar, new LinearLayout.LayoutParams(-1, dp(48)));
        col.addView(line(false));

        final PackageManager pm = getPackageManager();
        final List<ResolveInfo> apps = launchables();
        final int pinned = 2;
        GridView g = new GridView(this);
        g.setNumColumns(6);
        g.setVerticalSpacing(dp(12));
        g.setPadding(dp(8), dp(12), dp(8), 0);
        g.setAdapter(new BaseAdapter() {
            public int getCount() { return apps.size() + pinned; }
            public Object getItem(int p) { return p; }
            public long getItemId(int p) { return p; }
            public View getView(int p, View v, ViewGroup parent) {
                LinearLayout c = new LinearLayout(MainActivity.this);
                c.setOrientation(LinearLayout.VERTICAL);
                c.setGravity(Gravity.CENTER);
                ImageView i = new ImageView(MainActivity.this);
                String label;
                if (p < pinned) {
                    i.setImageResource(p == 0 ? R.drawable.ic_music : R.drawable.ic_video);
                    label = p == 0 ? "Música USB" : "Video";
                } else {
                    ResolveInfo ri = apps.get(p - pinned);
                    i.setImageDrawable(ri.loadIcon(pm));
                    label = ri.loadLabel(pm).toString();
                }
                c.addView(i, new LinearLayout.LayoutParams(dp(40), dp(40)));
                TextView t = text(label, 13);
                t.setSingleLine(true);
                t.setEllipsize(TextUtils.TruncateAt.END);
                t.setPadding(dp(4), dp(4), dp(4), 0);
                c.addView(t);
                return c;
            }
        });
        g.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> a, View v, int pos, long id) {
                if (pos < pinned) { open(pos == 0 ? "music" : "video"); return; }
                ResolveInfo ri = apps.get(pos - pinned);
                Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
        });
        g.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> a, View v, int pos, long id) {
                if (pos < pinned) { choose(pos == 0 ? "music" : "video"); return true; }
                return false;
            }
        });
        col.addView(g, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
    }

    List<ResolveInfo> launchables() {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        PackageManager pm = getPackageManager();
        List<ResolveInfo> l = new ArrayList<ResolveInfo>(pm.queryIntentActivities(i, 0));
        Iterator<ResolveInfo> it = l.iterator();
        while (it.hasNext()) {
            if (it.next().activityInfo.packageName.equals(getPackageName())) it.remove();
        }
        Collections.sort(l, new ResolveInfo.DisplayNameComparator(pm));
        return l;
    }

    String pkgFor(String key) {
        String s = prefs.getString("pkg_" + key, null);
        if (s != null) return s;
        for (String[] d : DEFAULTS) if (d[0].equals(key)) return d[1];
        return null;
    }

    void open(String key) {
        if (key.equals("radio") || key.equals("music") || key.equals("video")) pauseSessions(null);
        String cmp = prefs.getString("cmp_" + key, null);
        if (cmp != null) {
            ComponentName cn = ComponentName.unflattenFromString(cmp);
            if (cn != null) {
                try {
                    startActivity(new Intent().setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    return;
                } catch (Exception e) {
                    // si falla, se ofrece elegir de nuevo
                }
            }
        }
        String p = pkgFor(key);
        Intent i = p == null ? null : getPackageManager().getLaunchIntentForPackage(p);
        if (i == null) { choose(key); return; }
        startActivity(i);
    }

    void choose(final String key) {
        final List<ResolveInfo> apps = launchables();
        PackageManager pm = getPackageManager();
        String[] names = new String[apps.size() + 1];
        names[0] = "Más opciones: apps ocultas del sistema...";
        for (int n = 0; n < apps.size(); n++) names[n + 1] = apps.get(n).loadLabel(pm).toString();
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Elige la app para este acceso")
            .setItems(names, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    if (w == 0) { chooseHidden(key); return; }
                    prefs.edit().putString("pkg_" + key, apps.get(w - 1).activityInfo.packageName)
                        .remove("cmp_" + key).apply();
                    open(key);
                }
            }).show();
    }

    boolean keep(String pkg, String label) {
        if (pkg.equals(getPackageName()) || pkg.startsWith("com.google.")) return false;
        if (pkg.startsWith("com.android.") || pkg.startsWith("android")) {
            String s = (pkg + " " + label).toLowerCase();
            return s.contains("music") || s.contains("video") || s.contains("radio")
                || s.contains("media") || s.contains("player") || s.contains("fm");
        }
        return true;
    }

    void chooseHidden(final String key) {
        PackageManager pm = getPackageManager();
        TreeMap<String, ComponentName> found = new TreeMap<String, ComponentName>();
        for (PackageInfo pi : pm.getInstalledPackages(PackageManager.GET_ACTIVITIES)) {
            if (pi.activities == null) continue;
            for (ActivityInfo ai : pi.activities) {
                if (!ai.exported || !ai.enabled) continue;
                String label = String.valueOf(ai.loadLabel(pm));
                if (!keep(ai.packageName, label)) continue;
                String shortName = ai.name.startsWith(ai.packageName) ? ai.name.substring(ai.packageName.length()) : ai.name;
                found.put(label + "\n" + ai.packageName + shortName, new ComponentName(ai.packageName, ai.name));
            }
        }
        final List<ComponentName> comps = new ArrayList<ComponentName>();
        final List<String> names = new ArrayList<String>();
        for (Map.Entry<String, ComponentName> e : found.entrySet()) {
            names.add(e.getKey());
            comps.add(e.getValue());
        }
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Apps ocultas del sistema")
            .setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    prefs.edit().putString("cmp_" + key, comps.get(w).flattenToString()).apply();
                    open(key);
                }
            }).show();
    }

    void sendMedia(int code) {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
    }

    void adjustVolume(int dir) {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI);
    }

    boolean hasNotifAccess() {
        String s = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(getPackageName());
    }

    void openNotifAccess() {
        try {
            startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
        } catch (Exception e) {
            if (nowText != null) nowText.setText("Esta radio no tiene la pantalla de acceso a notificaciones");
        }
    }

    List<MediaController> allSessions() {
        List<MediaController> out = new ArrayList<MediaController>();
        if (!hasNotifAccess()) return out;
        try {
            MediaSessionManager m = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            out.addAll(m.getActiveSessions(new ComponentName(this, NowPlayingService.class)));
        } catch (Exception e) {
            // sin permiso o sin sesiones
        }
        return out;
    }

    /** Sesión de medios: la de Bluetooth si bt es true, y cualquier otra si es false. */
    MediaController sessionFor(boolean bt) {
        for (MediaController c : allSessions()) {
            boolean isBt = isBtSession(c);
            if (isBt == bt) return c;
        }
        return null;
    }

    /** Una sesión es de Bluetooth si es de la app asignada al acceso Bluetooth o su nombre lo indica. */
    boolean isBtSession(MediaController c) {
        String pkg = c.getPackageName();
        if (pkg.equals(pkgFor("bt"))) return true;
        String cmp = prefs.getString("cmp_bt", null);
        ComponentName cn = cmp == null ? null : ComponentName.unflattenFromString(cmp);
        if (cn != null && pkg.equals(cn.getPackageName())) return true;
        String low = pkg.toLowerCase();
        return low.contains("bluetooth") || low.contains(".bt");
    }

    static boolean isPlayingState(PlaybackState s) {
        if (s == null) return false;
        int st = s.getState();
        return st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING
            || st == PlaybackState.STATE_CONNECTING || st == PlaybackState.STATE_FAST_FORWARDING
            || st == PlaybackState.STATE_REWINDING;
    }

    void pauseSessions(MediaController except) {
        for (MediaController c : allSessions()) {
            if (except != null && c.getSessionToken().equals(except.getSessionToken())) continue;
            if (isPlayingState(c.getPlaybackState())) c.getTransportControls().pause();
        }
    }

    void toggle(boolean bt) {
        MediaController c = sessionFor(bt);
        if (c == null) { sendMedia(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE); return; }
        PlaybackState ps = c.getPlaybackState();
        boolean playing = isPlayingState(ps);
        boolean desired = !playing;
        long until = SystemClock.elapsedRealtime() + 1500;
        if (bt) { btHold = until; btHoldPlay = desired; } else { musicHold = until; musicHoldPlay = desired; }
        ImageView iv = bt ? btPlayIcon : playIcon;
        if (iv != null) iv.setImageResource(desired ? R.drawable.ic_pause : R.drawable.ic_play);
        if (desired) pauseSessions(c);
        int st = ps == null ? PlaybackState.STATE_NONE : ps.getState();
        if (playing) {
            c.getTransportControls().pause();
        } else if (st == PlaybackState.STATE_PAUSED || st == PlaybackState.STATE_STOPPED) {
            c.getTransportControls().play();
        } else {
            c.dispatchMediaButtonEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
            c.dispatchMediaButtonEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
        }
    }

    void skip(boolean bt, boolean next) {
        MediaController c = sessionFor(bt);
        if (c != null) {
            if (next) c.getTransportControls().skipToNext();
            else c.getTransportControls().skipToPrevious();
        } else {
            sendMedia(next ? KeyEvent.KEYCODE_MEDIA_NEXT : KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        }
    }

    static String stateName(PlaybackState s) {
        if (s == null) return "sin dato";
        switch (s.getState()) {
            case PlaybackState.STATE_PLAYING: return "sonando";
            case PlaybackState.STATE_PAUSED: return "en pausa";
            case PlaybackState.STATE_STOPPED: return "detenido";
            case PlaybackState.STATE_BUFFERING: return "cargando";
            case PlaybackState.STATE_NONE: return "ninguno";
            default: return "código " + s.getState();
        }
    }

    void showDiag() {
        StringBuilder sb = new StringBuilder();
        sb.append("Acceso a notificaciones: ").append(hasNotifAccess() ? "sí" : "no").append("\n");
        sb.append("Exclusividad de audio: ").append(prefs.getBoolean("exclusive", true) ? "activada" : "desactivada").append("\n\n");
        List<MediaController> all = allSessions();
        if (all.isEmpty()) sb.append("Sin sesiones de medios activas.");
        for (MediaController c : all) {
            MediaMetadata md = c.getMetadata();
            CharSequence title = md == null ? null : md.getText(MediaMetadata.METADATA_KEY_TITLE);
            sb.append(c.getPackageName()).append("\n  estado: ").append(stateName(c.getPlaybackState()))
              .append("\n  canción: ").append(title == null ? "-" : title).append("\n\n");
        }
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Diagnóstico de audio")
            .setMessage(sb.toString())
            .setPositiveButton("Cambiar exclusividad", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    prefs.edit().putBoolean("exclusive", !prefs.getBoolean("exclusive", true)).apply();
                }
            })
            .setNegativeButton("Cerrar", null)
            .show();
    }

    void updateNow() {
        boolean access = hasNotifAccess();
        update(false, access);
        update(true, access);
    }

    void update(boolean bt, boolean access) {
        TextView tv = bt ? btText : nowText;
        ImageView iv = bt ? btPlayIcon : playIcon;
        if (tv == null) return;
        String idle = access ? "Sin reproducción" : "Toca aquí para ver qué suena";
        MediaController c = access ? sessionFor(bt) : null;
        MediaMetadata md = c == null ? null : c.getMetadata();
        CharSequence title = md == null ? null : md.getText(MediaMetadata.METADATA_KEY_TITLE);
        if (title == null) {
            tv.setText(idle);
        } else {
            CharSequence artist = md.getText(MediaMetadata.METADATA_KEY_ARTIST);
            tv.setText(artist == null ? title : title + " - " + artist);
        }
        boolean playing = isPlayingState(c == null ? null : c.getPlaybackState());
        long hold = bt ? btHold : musicHold;
        if (SystemClock.elapsedRealtime() < hold) playing = bt ? btHoldPlay : musicHoldPlay;
        if (iv != null) iv.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
    }
}
