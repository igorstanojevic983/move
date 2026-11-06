package rs.webtv.gecko;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONObject;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.StorageController;
import org.mozilla.geckoview.WebExtension;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    public static final String PREFS = "webtv";
    public static final String KEY_AUTOSTART = "autostart";
    private static final String KEY_URL = "url";
    private static final String KEY_ZOOM = "zoom";
    private static final String KEY_SPEED = "speed";
    private static final String KEY_MOBILE = "mobile_mode";
    private static final String KEY_USER = "login_user";
    private static final String KEY_PASS = "login_pass";
    private static final String KEY_ECO = "eco_mode";

    private static final String DEFAULT_URL = "https://play.move.tv/";
    private static final String DEFAULT_USER = "irena.stanojevic";
    private static final String DEFAULT_PASS = "Benevreci123";
    private static final String TAG = "MoveTVFx";

    private static final long CURSOR_HIDE_MS = 5000;
    private static final long LONG_BACK_MS = 800;

    private static GeckoRuntime sRuntime;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    private FrameLayout root;
    private GeckoView geckoView;
    private GeckoSession session;
    private CursorView cursor;
    private WebExtension extension;
    private final List<WebExtension.Port> ports = new ArrayList<>();

    private boolean canGoBack;
    private boolean fullScreen;

    // kursor
    private float cx = -1, cy = -1;
    private boolean up, down, left, right;
    private long moveStart, lastWheel, touchDownTime;
    private boolean okDown, moverRunning;

    private boolean backLongFired;
    private long lastBackExit;

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (sRuntime == null) {
            GeckoRuntimeSettings.Builder b = new GeckoRuntimeSettings.Builder()
                    .javaScriptEnabled(true)
                    .consoleOutput(true);
            String cfg = writeConfig(prefs.getBoolean(KEY_ECO, true));
            if (cfg != null) b.configFilePath(cfg);
            GeckoRuntimeSettings settings = b.build();
            sRuntime = GeckoRuntime.create(getApplicationContext(), settings);
        }

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        geckoView = new GeckoView(this);
        root.addView(geckoView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        cursor = new CursorView(this);
        root.addView(cursor, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        hideSystemUi();

        session = new GeckoSession(buildSettings());
        setupDelegates(session);
        session.open(sRuntime);
        geckoView.setSession(session);

        installExtension();
        session.loadUri(homeUrl());

        root.post(() -> {
            if (cx < 0) {
                cx = root.getWidth() / 2f;
                cy = root.getHeight() / 2f;
            }
            cursor.setPosition(cx, cy);
            scheduleHide();
        });
        geckoView.requestFocus();
    }

    /**
     * Režim štednje: Gecko podešavanja za slabije uređaje (manje memorije,
     * bez posebnog grafičkog procesa, ređe osvežavanje animacija stranice).
     * Video se i dalje dekodira hardverski i prikazuje punim kvalitetom.
     */
    private String writeConfig(boolean eco) {
        java.io.File f = new java.io.File(getFilesDir(), "geckoview-config.yaml");
        if (!eco) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            return null;
        }
        String yaml = "prefs:\n"
                + "  layers.gpu-process.enabled: false\n"
                + "  layout.frame_rate: 30\n"
                + "  ui.prefersReducedMotion: 1\n"
                + "  dom.ipc.processPrelaunch.enabled: false\n"
                + "  dom.ipc.processCount: 1\n"
                + "  browser.cache.memory.capacity: 16384\n"
                + "  browser.sessionhistory.max_total_viewers: 0\n"
                + "  image.mem.surfacecache.max_size_kb: 51200\n"
                + "  media.hardware-video-decoding.enabled: true\n";
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
            out.write(yaml.getBytes("UTF-8"));
            return f.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "config write failed", e);
            return null;
        }
    }

    private void toggleEco() {
        boolean eco = !prefs.getBoolean(KEY_ECO, true);
        prefs.edit().putBoolean(KEY_ECO, eco).commit();
        toast("Režim štednje: " + (eco ? "UKLJUČEN" : "ISKLJUČEN") + " – aplikacija se ponovo pokreće…");
        handler.postDelayed(this::restartApp, 1500);
    }

    private void restartApp() {
        android.content.Intent i = new android.content.Intent(this, MainActivity.class);
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        android.app.PendingIntent pi = android.app.PendingIntent.getActivity(this, 1, i,
                android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_CANCEL_CURRENT);
        android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
        if (am != null) am.set(android.app.AlarmManager.RTC, System.currentTimeMillis() + 800, pi);
        finishAffinity();
        System.exit(0);
    }

    private GeckoSessionSettings buildSettings() {
        boolean mobile = prefs.getBoolean(KEY_MOBILE, false);
        return new GeckoSessionSettings.Builder()
                .userAgentMode(mobile ? GeckoSessionSettings.USER_AGENT_MODE_MOBILE
                        : GeckoSessionSettings.USER_AGENT_MODE_DESKTOP)
                .viewportMode(mobile ? GeckoSessionSettings.VIEWPORT_MODE_MOBILE
                        : GeckoSessionSettings.VIEWPORT_MODE_DESKTOP)
                .build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (session != null) session.close();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    // ------------------------------------------------------------------ Gecko

    private void setupDelegates(GeckoSession s) {
        s.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public void onCanGoBack(GeckoSession session, boolean value) {
                canGoBack = value;
            }
        });

        s.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override
            public void onFullScreen(GeckoSession session, boolean full) {
                fullScreen = full;
                hideSystemUi();
            }

            @Override
            public void onCrash(GeckoSession session) {
                Log.w(TAG, "content crash – restart");
                recreate();
            }

            @Override
            public void onKill(GeckoSession session) {
                Log.w(TAG, "content killed – restart");
                recreate();
            }
        });

        s.setPermissionDelegate(new GeckoSession.PermissionDelegate() {
            @Override
            public GeckoResult<Integer> onContentPermissionRequest(GeckoSession session, ContentPermission perm) {
                // DRM (Widevine), automatsko puštanje zvuka/videa – sve dozvoli
                Log.i(TAG, "permission request " + perm.permission);
                return GeckoResult.fromValue(ContentPermission.VALUE_ALLOW);
            }
        });

        s.setPromptDelegate(new GeckoSession.PromptDelegate() {
            @Override
            public GeckoResult<PromptResponse> onAlertPrompt(GeckoSession session, AlertPrompt prompt) {
                final GeckoResult<PromptResponse> res = new GeckoResult<>();
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(prompt.title)
                        .setMessage(prompt.message)
                        .setPositiveButton("OK", null)
                        .setOnDismissListener(d -> res.complete(prompt.dismiss()))
                        .show();
                return res;
            }

            @Override
            public GeckoResult<PromptResponse> onButtonPrompt(GeckoSession session, ButtonPrompt prompt) {
                final GeckoResult<PromptResponse> res = new GeckoResult<>();
                final boolean[] done = {false};
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(prompt.title)
                        .setMessage(prompt.message)
                        .setPositiveButton("OK", (d, w) -> {
                            done[0] = true;
                            res.complete(prompt.confirm(ButtonPrompt.Type.POSITIVE));
                        })
                        .setNegativeButton("Otkaži", (d, w) -> {
                            done[0] = true;
                            res.complete(prompt.confirm(ButtonPrompt.Type.NEGATIVE));
                        })
                        .setOnDismissListener(d -> {
                            if (!done[0]) res.complete(prompt.dismiss());
                        })
                        .show();
                return res;
            }

            @Override
            public GeckoResult<PromptResponse> onChoicePrompt(GeckoSession session, ChoicePrompt prompt) {
                // padajuće liste (<select>) – prikaži kao izbor na ekranu
                final List<ChoicePrompt.Choice> flat = new ArrayList<>();
                flatten(prompt.choices, flat);
                String[] labels = new String[flat.size()];
                int checked = -1;
                for (int i = 0; i < flat.size(); i++) {
                    labels[i] = flat.get(i).label;
                    if (flat.get(i).selected) checked = i;
                }
                final GeckoResult<PromptResponse> res = new GeckoResult<>();
                final boolean[] done = {false};
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(prompt.title != null ? prompt.title : "Izaberite")
                        .setSingleChoiceItems(labels, checked, (d, which) -> {
                            done[0] = true;
                            res.complete(prompt.confirm(flat.get(which)));
                            d.dismiss();
                        })
                        .setOnDismissListener(d -> {
                            if (!done[0]) res.complete(prompt.dismiss());
                        })
                        .show();
                return res;
            }
        });
    }

    private static void flatten(GeckoSession.PromptDelegate.ChoicePrompt.Choice[] in,
                                List<GeckoSession.PromptDelegate.ChoicePrompt.Choice> out) {
        if (in == null) return;
        for (GeckoSession.PromptDelegate.ChoicePrompt.Choice c : in) {
            if (c.items != null) flatten(c.items, out);
            else if (!c.separator && !c.disabled) out.add(c);
        }
    }

    private void installExtension() {
        sRuntime.getWebExtensionController()
                .ensureBuiltIn("resource://android/assets/extension/", "movetv@webtv.rs")
                .accept(ext -> {
                    extension = ext;
                    attachExtension();
                }, e -> Log.e(TAG, "extension install failed", e));
    }

    private void attachExtension() {
        if (extension == null || session == null) return;
        session.getWebExtensionController().setMessageDelegate(extension, new WebExtension.MessageDelegate() {
            @Override
            public void onConnect(WebExtension.Port port) {
                ports.add(port);
                port.setDelegate(new WebExtension.PortDelegate() {
                    @Override
                    public void onPortMessage(Object message, WebExtension.Port p) {
                    }

                    @Override
                    public void onDisconnect(WebExtension.Port p) {
                        ports.remove(p);
                    }
                });
                try {
                    JSONObject init = new JSONObject();
                    init.put("type", "init");
                    init.put("user", prefs.getString(KEY_USER, DEFAULT_USER));
                    init.put("pass", prefs.getString(KEY_PASS, DEFAULT_PASS));
                    init.put("zoom", zoom());
                    port.postMessage(init);
                } catch (Exception ex) {
                    Log.e(TAG, "init message", ex);
                }
            }
        }, "browser");
    }

    private void broadcast(JSONObject msg) {
        for (WebExtension.Port p : new ArrayList<>(ports)) {
            try {
                p.postMessage(msg);
            } catch (Exception ex) {
                ports.remove(p);
            }
        }
    }

    private void sendSimple(String type, String key, Object value) {
        try {
            JSONObject o = new JSONObject();
            o.put("type", type);
            if (key != null) o.put(key, value);
            broadcast(o);
        } catch (Exception ignored) {
        }
    }

    private String homeUrl() {
        return prefs.getString(KEY_URL, DEFAULT_URL);
    }

    private int zoom() {
        return prefs.getInt(KEY_ZOOM, 100);
    }

    // ------------------------------------------------------------------ tasteri

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean isDown = e.getAction() == KeyEvent.ACTION_DOWN;
        boolean isUp = e.getAction() == KeyEvent.ACTION_UP;

        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (isDown) setDir(code, true);
                else if (isUp) setDir(code, false);
                return true;

            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                if (isDown && e.getRepeatCount() == 0) pressOk();
                else if (isUp) releaseOk();
                return true;

            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (isDown && e.getRepeatCount() == 0) {
                    backLongFired = false;
                    handler.postDelayed(backLong, LONG_BACK_MS);
                } else if (isUp) {
                    handler.removeCallbacks(backLong);
                    if (!backLongFired && !e.isCanceled()) onBack();
                }
                return true;

            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SETTINGS:
            case KeyEvent.KEYCODE_INFO:
            case KeyEvent.KEYCODE_GUIDE:
                if (isUp) showMenu();
                return true;

            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (isUp) sendSimple("video", "action", "toggle");
                return true;

            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (isUp) sendSimple("video", "action", "ff");
                return true;

            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (isUp) sendSimple("video", "action", "rew");
                return true;

            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:
                if (isDown) wheel(0, 5);
                return true;

            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                if (isDown) wheel(0, -5);
                return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private final Runnable backLong = () -> {
        backLongFired = true;
        showMenu();
    };

    private void onBack() {
        if (fullScreen) {
            session.exitFullScreen();
            return;
        }
        if (canGoBack) {
            session.goBack();
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastBackExit < 2500) {
            finish();
        } else {
            lastBackExit = now;
            toast("Pritisnite NAZAD još jednom za izlaz.\nDugo držanje NAZAD = meni.");
        }
    }

    // ------------------------------------------------------------------ kursor

    private void setDir(int code, boolean pressed) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: up = pressed; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: down = pressed; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: left = pressed; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: right = pressed; break;
        }
        showCursor();
        if (pressed && !moverRunning) {
            moverRunning = true;
            moveStart = SystemClock.uptimeMillis();
            handler.post(mover);
        }
    }

    private final Runnable mover = new Runnable() {
        @Override
        public void run() {
            if (!(up || down || left || right)) {
                moverRunning = false;
                return;
            }
            float speedMul = new float[]{0.6f, 1f, 1.6f}[prefs.getInt(KEY_SPEED, 1)];
            float density = getResources().getDisplayMetrics().density;
            long held = SystemClock.uptimeMillis() - moveStart;
            float t = Math.min(1f, held / 900f);
            float step = (2.5f + 12f * t * t) * density * speedMul;

            float dx = (right ? step : 0) - (left ? step : 0);
            float dy = (down ? step : 0) - (up ? step : 0);
            float w = root.getWidth() - 1, h = root.getHeight() - 1;
            float nx = Math.max(0, Math.min(w, cx + dx));
            float ny = Math.max(0, Math.min(h, cy + dy));

            if (!okDown) {
                long now = SystemClock.uptimeMillis();
                if (now - lastWheel > 70) {
                    float vs = 0, hs = 0;
                    if (up && ny <= 0) vs = 1;
                    if (down && ny >= h) vs = -1;
                    if (left && nx <= 0) hs = -1;
                    if (right && nx >= w) hs = 1;
                    if (vs != 0 || hs != 0) {
                        wheel(hs, vs);
                        lastWheel = now;
                    }
                }
            }

            if (nx != cx || ny != cy) {
                cx = nx;
                cy = ny;
                cursor.setPosition(cx, cy);
                if (okDown) sendTouch(MotionEvent.ACTION_MOVE);
                else sendHover();
            }
            handler.postDelayed(this, 16);
        }
    };

    private void pressOk() {
        showCursor();
        okDown = true;
        cursor.setPressed2(true);
        touchDownTime = SystemClock.uptimeMillis();
        sendTouch(MotionEvent.ACTION_DOWN);
    }

    private void releaseOk() {
        if (!okDown) return;
        okDown = false;
        cursor.setPressed2(false);
        sendTouch(MotionEvent.ACTION_UP);
        showCursor();
    }

    private void sendTouch(int action) {
        long now = SystemClock.uptimeMillis();
        MotionEvent.PointerProperties[] pp = {new MotionEvent.PointerProperties()};
        pp[0].id = 0;
        pp[0].toolType = MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent.PointerCoords[] pc = {new MotionEvent.PointerCoords()};
        pc[0].x = cx;
        pc[0].y = cy;
        pc[0].pressure = 1f;
        pc[0].size = 1f;
        MotionEvent ev = MotionEvent.obtain(touchDownTime, now, action, 1, pp, pc,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        geckoView.dispatchTouchEvent(ev);
        ev.recycle();
    }

    private MotionEvent mouseEvent(int action, float hs, float vs) {
        long now = SystemClock.uptimeMillis();
        MotionEvent.PointerProperties[] pp = {new MotionEvent.PointerProperties()};
        pp[0].id = 0;
        pp[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords[] pc = {new MotionEvent.PointerCoords()};
        pc[0].x = cx;
        pc[0].y = cy;
        pc[0].size = 1f;
        pc[0].setAxisValue(MotionEvent.AXIS_VSCROLL, vs);
        pc[0].setAxisValue(MotionEvent.AXIS_HSCROLL, hs);
        return MotionEvent.obtain(now, now, action, 1, pp, pc,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0);
    }

    private void sendHover() {
        MotionEvent ev = mouseEvent(MotionEvent.ACTION_HOVER_MOVE, 0, 0);
        geckoView.dispatchGenericMotionEvent(ev);
        ev.recycle();
    }

    private void wheel(float hs, float vs) {
        MotionEvent ev = mouseEvent(MotionEvent.ACTION_SCROLL, hs, vs);
        geckoView.dispatchGenericMotionEvent(ev);
        ev.recycle();
    }

    private final Runnable hideCursor = () -> {
        if (!okDown && !(up || down || left || right)) cursor.setShown(false);
    };

    private void showCursor() {
        cursor.setShown(true);
        scheduleHide();
    }

    private void scheduleHide() {
        handler.removeCallbacks(hideCursor);
        handler.postDelayed(hideCursor, CURSOR_HIDE_MS);
    }

    // ------------------------------------------------------------------ meni

    private void showMenu() {
        final boolean auto = prefs.getBoolean(KEY_AUTOSTART, false);
        final boolean mobile = prefs.getBoolean(KEY_MOBILE, false);
        final boolean eco = prefs.getBoolean(KEY_ECO, true);
        String[] items = {
                "Režim štednje: " + (eco ? "UKLJUČEN" : "ISKLJUČEN") + " (promeni – ponovo pokreće aplikaciju)",
                "Ukucaj tekst u izabrano polje",
                "Podaci za prijavu (korisničko ime i lozinka)",
                "Režim prikaza: " + (mobile ? "MOBILNI TELEFON" : "DESKTOP RAČUNAR") + " (promeni)",
                "Osveži stranicu",
                "Početna stranica",
                "Uvećaj prikaz (+10%)  — sada " + zoom() + "%",
                "Umanji prikaz (−10%)",
                "Brzina kursora",
                "Pokreni pri uključenju uređaja: " + (auto ? "DA" : "NE"),
                "Promeni adresu stranice",
                "Obriši kolačiće i keš (odjava)",
                "Izađi iz aplikacije"
        };
        new AlertDialog.Builder(this)
                .setTitle("Meni")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: toggleEco(); break;
                        case 1: showTypeDialog(); break;
                        case 2: showLoginDialog(); break;
                        case 3: toggleMobile(); break;
                        case 4: session.reload(); break;
                        case 5: session.loadUri(homeUrl()); break;
                        case 6: setZoom(zoom() + 10); break;
                        case 7: setZoom(zoom() - 10); break;
                        case 8: showSpeed(); break;
                        case 9:
                            prefs.edit().putBoolean(KEY_AUTOSTART, !auto).apply();
                            toast("Pokretanje pri uključenju: " + (!auto ? "DA" : "NE"));
                            break;
                        case 10: showUrlDialog(); break;
                        case 11: clearData(); break;
                        case 12: finish(); break;
                    }
                })
                .show();
    }

    private void showTypeDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle("Tekst za izabrano polje")
                .setView(input)
                .setPositiveButton("Upiši", (d, w) -> sendType(input.getText().toString(), false))
                .setNeutralButton("Upiši + Enter", (d, w) -> sendType(input.getText().toString(), true))
                .setNegativeButton("Otkaži", null)
                .show();
    }

    private void sendType(String text, boolean enter) {
        try {
            JSONObject o = new JSONObject();
            o.put("type", "type");
            o.put("text", text);
            o.put("enter", enter);
            broadcast(o);
        } catch (Exception ignored) {
        }
    }

    private void toggleMobile() {
        boolean m = !prefs.getBoolean(KEY_MOBILE, false);
        prefs.edit().putBoolean(KEY_MOBILE, m).apply();
        session.getSettings().setUserAgentMode(m ? GeckoSessionSettings.USER_AGENT_MODE_MOBILE
                : GeckoSessionSettings.USER_AGENT_MODE_DESKTOP);
        session.getSettings().setViewportMode(m ? GeckoSessionSettings.VIEWPORT_MODE_MOBILE
                : GeckoSessionSettings.VIEWPORT_MODE_DESKTOP);
        session.loadUri(homeUrl());
        toast("Režim prikaza: " + (m ? "mobilni telefon" : "desktop računar"));
    }

    private void showLoginDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        final EditText user = new EditText(this);
        user.setHint("Korisničko ime");
        user.setSingleLine(true);
        user.setText(prefs.getString(KEY_USER, DEFAULT_USER));
        final EditText pass = new EditText(this);
        pass.setHint("Lozinka");
        pass.setSingleLine(true);
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        pass.setText(prefs.getString(KEY_PASS, DEFAULT_PASS));
        box.addView(user);
        box.addView(pass);
        new AlertDialog.Builder(this)
                .setTitle("Podaci za automatsku prijavu")
                .setView(box)
                .setPositiveButton("Sačuvaj", (d, w) -> {
                    prefs.edit().putString(KEY_USER, user.getText().toString().trim())
                            .putString(KEY_PASS, pass.getText().toString()).apply();
                    toast("Sačuvano – važi od sledećeg učitavanja stranice.");
                })
                .setNeutralButton("Isključi", (d, w) -> {
                    prefs.edit().putString(KEY_USER, "").putString(KEY_PASS, "").apply();
                    toast("Automatska prijava isključena.");
                })
                .setNegativeButton("Otkaži", null)
                .show();
    }

    private void setZoom(int z) {
        z = Math.max(30, Math.min(250, z));
        prefs.edit().putInt(KEY_ZOOM, z).apply();
        sendSimple("zoom", "zoom", z);
        toast("Prikaz: " + z + "%");
    }

    private void showSpeed() {
        new AlertDialog.Builder(this)
                .setTitle("Brzina kursora")
                .setSingleChoiceItems(new String[]{"Spora", "Srednja", "Brza"},
                        prefs.getInt(KEY_SPEED, 1), (d, which) -> {
                            prefs.edit().putInt(KEY_SPEED, which).apply();
                            d.dismiss();
                        })
                .show();
    }

    private void showUrlDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(homeUrl());
        new AlertDialog.Builder(this)
                .setTitle("Adresa početne stranice")
                .setView(input)
                .setPositiveButton("Sačuvaj", (d, w) -> {
                    String u = input.getText().toString().trim();
                    if (u.isEmpty()) u = DEFAULT_URL;
                    if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
                    prefs.edit().putString(KEY_URL, u).apply();
                    session.loadUri(u);
                })
                .setNeutralButton("Podrazumevano", (d, w) -> {
                    prefs.edit().remove(KEY_URL).apply();
                    session.loadUri(DEFAULT_URL);
                })
                .setNegativeButton("Otkaži", null)
                .show();
    }

    private void clearData() {
        sRuntime.getStorageController().clearData(StorageController.ClearFlags.ALL)
                .accept(v -> {
                    session.loadUri(homeUrl());
                    toast("Podaci obrisani.");
                });
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
