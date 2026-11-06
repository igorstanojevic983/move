package rs.webtv.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

public class MainActivity extends Activity {

    public static final String PREFS = "webtv";
    public static final String KEY_AUTOSTART = "autostart";
    private static final String KEY_URL = "url";
    private static final String KEY_ZOOM = "zoom";
    private static final String KEY_SPEED = "speed";
    private static final String KEY_MOBILE = "mobile_mode";
    private static final String MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; SM-S911B) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36";
    private static final String KEY_USER = "login_user";
    private static final String KEY_PASS = "login_pass";

    /** Podrazumevani podaci za prijavu (mogu se promeniti u meniju). */
    private static final String DEFAULT_USER = "irena.stanojevic";
    private static final String DEFAULT_PASS = "Benevreci123";

    private static final String DEFAULT_URL = "https://play.move.tv/";
    /** Desktop Chrome na Windows-u – sajt misli da je u pitanju računar. */
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";

    private static final long CURSOR_HIDE_MS = 5000;
    private static final long LONG_BACK_MS = 800;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    private FrameLayout root;
    private WebView web;
    private CursorView cursor;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;

    // Kursor
    private float cx = -1, cy = -1;
    private boolean up, down, left, right;
    private long moveStart;
    private long lastWheel;
    private boolean okDown;
    private long touchDownTime;
    private boolean moverRunning;

    // NAZAD
    private boolean backLongFired;
    private long lastBackExit;

    // ---------------------------------------------------------------- lifecycle

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        web = new WebView(this);
        setupWebView(web);
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        cursor = new CursorView(this);
        root.addView(cursor, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        hideSystemUi();

        root.post(new Runnable() {
            @Override
            public void run() {
                if (cx < 0) {
                    cx = root.getWidth() / 2f;
                    cy = root.getHeight() / 2f;
                }
                cursor.setPosition(cx, cy);
                scheduleHide();
            }
        });

        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) {
            web.loadUrl(homeUrl());
        }
        web.requestFocus();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        web.resumeTimers();
        hideSystemUi();
    }

    @Override
    protected void onPause() {
        CookieManager.getInstance().flush();
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (web != null) {
            root.removeView(web);
            web.destroy();
        }
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

    // ---------------------------------------------------------------- WebView

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView(final WebView w) {
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUserAgentString(prefs.getBoolean(KEY_MOBILE, false) ? MOBILE_UA : DESKTOP_UA);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);
        s.setLoadsImagesAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(w, true);

        w.setBackgroundColor(0xFF000000);
        w.setFocusable(true);
        w.setFocusableInTouchMode(true);
        w.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);

        w.setWebViewClient(new WebViewClient() {
            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !isWebUrl(url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isWebUrl(request.getUrl().toString());
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                handler.removeCallbacks(retryLoad);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                applyZoom();
                injectAutofill();
                CookieManager.getInstance().flush();
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (Build.VERSION.SDK_INT < 23) onMainFrameError(description);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (Build.VERSION.SDK_INT >= 23 && request.isForMainFrame()) {
                    onMainFrameError(String.valueOf(error.getDescription()));
                }
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                // Pregledač se srušio – ponovo pokreni ekran
                recreate();
                return true;
            }
        });

        w.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                // Dozvola za zaštićeni (DRM / Widevine) video
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        for (String r : request.getResources()) {
                            if (PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(r)) {
                                request.grant(new String[]{PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID});
                                return;
                            }
                        }
                        request.deny();
                    }
                });
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customCallback = callback;
                root.addView(view, 1, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                web.setVisibility(View.GONE);
                hideSystemUi();
            }

            @Override
            public void onHideCustomView() {
                hideCustomView();
            }

            @Override
            public Bitmap getDefaultVideoPoster() {
                // Sprečava sivu "play" ikonicu pre pokretanja videa
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            }
        });
    }

    /** Izlazak iz prikaza preko celog ekrana (video). */
    private void hideCustomView() {
        if (customView == null) return;
        View v = customView;
        WebChromeClient.CustomViewCallback cb = customCallback;
        customView = null;
        customCallback = null;
        root.removeView(v);
        web.setVisibility(View.VISIBLE);
        if (cb != null) cb.onCustomViewHidden();
        web.requestFocus();
        hideSystemUi();
    }

    private static boolean isWebUrl(String url) {
        if (url == null) return false;
        String scheme = Uri.parse(url).getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)
                || "about".equalsIgnoreCase(scheme) || "data".equalsIgnoreCase(scheme)
                || "blob".equalsIgnoreCase(scheme);
    }

    private final Runnable retryLoad = new Runnable() {
        @Override
        public void run() {
            web.reload();
        }
    };

    private void onMainFrameError(String description) {
        toast("Greška pri učitavanju (" + description + "). Novi pokušaj za 10 s…");
        handler.removeCallbacks(retryLoad);
        handler.postDelayed(retryLoad, 10000);
    }

    private String homeUrl() {
        return prefs.getString(KEY_URL, DEFAULT_URL);
    }

    private int zoom() {
        return prefs.getInt(KEY_ZOOM, 100);
    }

    /**
     * Svake 1,5 s proverava da li na stranici postoji prazno polje za lozinku;
     * ako postoji, upisuje korisničko ime i lozinku (samo jednom po polju,
     * pa korisnik može ručno da ih promeni).
     */
    private void injectAutofill() {
        String u = prefs.getString(KEY_USER, DEFAULT_USER);
        String pw = prefs.getString(KEY_PASS, DEFAULT_PASS);
        if (u == null || u.isEmpty()) return;
        String js = "(function(U,P){if(window.__wtvAF)clearInterval(window.__wtvAF);"
                + "function setv(e,v){var pr=e.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;"
                + "var d=Object.getOwnPropertyDescriptor(pr,'value');if(d&&d.set)d.set.call(e,v);else e.value=v;"
                + "e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "e.dispatchEvent(new Event('blur',{bubbles:true}));}"
                + "function vis(e){return e&&e.offsetParent!==null&&!e.disabled;}"
                + "function run(){var ps=document.querySelectorAll('input[type=password]');"
                + "for(var i=0;i<ps.length;i++){var p=ps[i];if(!vis(p)||p.dataset.wtvFilled)continue;"
                + "var scope=p.form||document;var all=scope.querySelectorAll('input');var user=null;"
                + "for(var j=0;j<all.length;j++){var a=all[j];if(a===p)break;"
                + "var t=(a.type||'text').toLowerCase();if((t==='text'||t==='email'||t==='tel')&&vis(a))user=a;}"
                + "if(user&&!user.dataset.wtvFilled&&!user.value){setv(user,U);}if(user)user.dataset.wtvFilled='1';"
                + "if(!p.value&&P)setv(p,P);p.dataset.wtvFilled='1';}}"
                + "run();window.__wtvAF=setInterval(run,1500);})("
                + org.json.JSONObject.quote(u) + "," + org.json.JSONObject.quote(pw) + ");";
        web.evaluateJavascript(js, null);
    }

    private void toggleMobile() {
        boolean m = !prefs.getBoolean(KEY_MOBILE, false);
        prefs.edit().putBoolean(KEY_MOBILE, m).apply();
        web.getSettings().setUserAgentString(m ? MOBILE_UA : DESKTOP_UA);
        web.clearCache(false);
        web.loadUrl(homeUrl());
        toast("Režim prikaza: " + (m ? "mobilni telefon" : "desktop računar"));
    }

    private void showLoginDialog() {
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
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
                    toast("Sačuvano.");
                    injectAutofill();
                })
                .setNeutralButton("Isključi", (d, w) -> {
                    prefs.edit().putString(KEY_USER, "").putString(KEY_PASS, "").apply();
                    web.evaluateJavascript("if(window.__wtvAF)clearInterval(window.__wtvAF);", null);
                    toast("Automatska prijava isključena.");
                })
                .setNegativeButton("Otkaži", null)
                .show();
    }

    private void applyZoom() {
        int z = zoom();
        web.evaluateJavascript("document.documentElement.style.zoom='" + (z / 100f) + "';", null);
    }

    // ---------------------------------------------------------------- tasteri

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
            case KeyEvent.KEYCODE_SPACE:
                if (isUp) videoJs("v.paused?v.play():v.pause();");
                return true;

            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (isUp) videoJs("v.currentTime=Math.min(v.duration||1e9,v.currentTime+30);");
                return true;

            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (isUp) videoJs("v.currentTime=Math.max(0,v.currentTime-30);");
                return true;

            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:
                if (isDown) wheel(0, 5);
                return true;

            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                if (isDown) wheel(0, -5);
                return true;

            case KeyEvent.KEYCODE_F5:
                if (isUp) web.reload();
                return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private final Runnable backLong = new Runnable() {
        @Override
        public void run() {
            backLongFired = true;
            showMenu();
        }
    };

    private void onBack() {
        if (customView != null) {
            hideCustomView();
            return;
        }
        if (web.canGoBack()) {
            web.goBack();
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

    private void videoJs(String body) {
        String js = "(function(){var vs=document.querySelectorAll('video');"
                + "for(var i=0;i<vs.length;i++){var v=vs[i];if(v.readyState>0){" + body + "return;}}})();";
        web.evaluateJavascript(js, null);
    }

    // ---------------------------------------------------------------- kursor

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

            // Na ivici ekrana – skroluj stranicu (kao točkić miša)
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

    private View target() {
        return customView != null ? customView : web;
    }

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
        // Ako je kliknuto na polje za tekst – otvori naš prozor sa tastaturom
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkTextField(false);
            }
        }, 350);
    }

    // ---------------------------------------------------------------- unos teksta

    private static final String JS_FIND_FIELD =
            "(function(){var e=document.activeElement;"
            + "try{while(e&&e.shadowRoot&&e.shadowRoot.activeElement)e=e.shadowRoot.activeElement;}catch(x){}"
            + "if(!e)return null;var t=(e.tagName||'').toLowerCase();"
            + "var ok=t==='textarea'||e.isContentEditable||(t==='input'&&!/^(button|submit|checkbox|radio|range|color|file|image|reset|hidden)$/i.test(e.type||''));"
            + "if(!ok)return null;window.__wtvEl=e;"
            + "var lab='';try{if(e.labels&&e.labels.length)lab=e.labels[0].innerText;}catch(x){}"
            + "return JSON.stringify({type:(e.type||'text').toLowerCase(),value:e.isContentEditable?e.innerText:(e.value||''),"
            + "label:lab||e.placeholder||e.getAttribute('aria-label')||e.name||''});})()";

    private boolean textDialogOpen;

    private void checkTextField(final boolean fromMenu) {
        if (textDialogOpen || customView != null) return;
        web.evaluateJavascript(JS_FIND_FIELD, new android.webkit.ValueCallback<String>() {
            @Override
            public void onReceiveValue(String result) {
                try {
                    if (result == null || "null".equals(result)) {
                        if (fromMenu) toast("Prvo kliknite (OK) na polje u koje želite da kucate.");
                        return;
                    }
                    String json = new org.json.JSONArray("[" + result + "]").getString(0);
                    org.json.JSONObject o = new org.json.JSONObject(json);
                    showTextDialog(o.optString("type"), o.optString("value"), o.optString("label"));
                } catch (Exception ex) {
                    if (fromMenu) toast("Polje nije pronađeno.");
                }
            }
        });
    }

    private void showTextDialog(String type, String value, String label) {
        textDialogOpen = true;
        boolean password = "password".equals(type);
        final EditText input = new EditText(this);
        int it = InputType.TYPE_CLASS_TEXT;
        if (password) it |= InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
        else if ("email".equals(type)) it |= InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
        else if ("number".equals(type) || "tel".equals(type)) it = InputType.TYPE_CLASS_NUMBER;
        else if ("url".equals(type)) it |= InputType.TYPE_TEXT_VARIATION_URI;
        input.setInputType(it);
        input.setSingleLine(true);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        input.setText(value);
        input.setSelection(input.getText().length());

        String title = password ? "Lozinka" : (label == null || label.trim().isEmpty() ? "Unesite tekst" : label.trim());
        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(input)
                .setPositiveButton("Upiši", (d, w) -> setFieldValue(input.getText().toString(), false))
                .setNeutralButton("Upiši + Enter", (d, w) -> setFieldValue(input.getText().toString(), true))
                .setNegativeButton("Otkaži", null)
                .create();
        dlg.setOnDismissListener(d -> {
            textDialogOpen = false;
            web.requestFocus();
            hideSystemUi();
        });
        input.setOnEditorActionListener((v, actionId, ev) -> {
            setFieldValue(input.getText().toString(), false);
            dlg.dismiss();
            return true;
        });
        if (dlg.getWindow() != null) {
            dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        dlg.show();
        input.requestFocus();
        input.postDelayed(() -> {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_FORCED);
        }, 200);
    }

    private void setFieldValue(String text, boolean pressEnter) {
        String js = "(function(v,enter){var e=window.__wtvEl;if(!e)return;try{e.focus();}catch(x){}"
                + "if(e.isContentEditable){e.innerText=v;}else{"
                + "var p=e.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;"
                + "var d=Object.getOwnPropertyDescriptor(p,'value');if(d&&d.set)d.set.call(e,v);else e.value=v;}"
                + "e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "if(enter){['keydown','keypress','keyup'].forEach(function(n){e.dispatchEvent(new KeyboardEvent(n,"
                + "{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}));});"
                + "if(e.form){if(e.form.requestSubmit)e.form.requestSubmit();else e.form.submit();}}"
                + "})(" + org.json.JSONObject.quote(text) + "," + pressEnter + ");";
        web.evaluateJavascript(js, null);
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
        target().dispatchTouchEvent(ev);
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
        pc[0].pressure = 0f;
        pc[0].size = 1f;
        pc[0].setAxisValue(MotionEvent.AXIS_VSCROLL, vs);
        pc[0].setAxisValue(MotionEvent.AXIS_HSCROLL, hs);
        return MotionEvent.obtain(now, now, action, 1, pp, pc,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0);
    }

    /** Pomeranje miša – da rade meniji koji se otvaraju na prelazak mišem. */
    private void sendHover() {
        MotionEvent ev = mouseEvent(MotionEvent.ACTION_HOVER_MOVE, 0, 0);
        target().dispatchGenericMotionEvent(ev);
        ev.recycle();
    }

    /** Točkić miša na poziciji kursora. vs > 0 = gore, vs < 0 = dole. */
    private void wheel(float hs, float vs) {
        MotionEvent ev = mouseEvent(MotionEvent.ACTION_SCROLL, hs, vs);
        boolean handled = target().dispatchGenericMotionEvent(ev);
        ev.recycle();
        if (!handled && customView == null) {
            web.evaluateJavascript("window.scrollBy(" + (int) (hs * 120) + "," + (int) (-vs * 120) + ");", null);
        }
    }

    private final Runnable hideCursor = new Runnable() {
        @Override
        public void run() {
            if (!okDown && !(up || down || left || right)) cursor.setShown(false);
        }
    };

    private void showCursor() {
        cursor.setShown(true);
        scheduleHide();
    }

    private void scheduleHide() {
        handler.removeCallbacks(hideCursor);
        handler.postDelayed(hideCursor, CURSOR_HIDE_MS);
    }

    // ---------------------------------------------------------------- meni

    private void showMenu() {
        final boolean auto = prefs.getBoolean(KEY_AUTOSTART, false);
        String[] items = {
                "Ukucaj tekst u izabrano polje",
                "Podaci za prijavu (korisničko ime i lozinka)",
                "Režim prikaza: " + (prefs.getBoolean(KEY_MOBILE, false) ? "MOBILNI TELEFON" : "DESKTOP RAČUNAR") + " (promeni)",
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
                        case 0: checkTextField(true); break;
                        case 1: showLoginDialog(); break;
                        case 2: toggleMobile(); break;
                        case 3: web.reload(); break;
                        case 4: web.loadUrl(homeUrl()); break;
                        case 5: setZoom(zoom() + 10); break;
                        case 6: setZoom(zoom() - 10); break;
                        case 7: showSpeed(); break;
                        case 8:
                            prefs.edit().putBoolean(KEY_AUTOSTART, !auto).apply();
                            toast("Pokretanje pri uključenju: " + (!auto ? "DA" : "NE"));
                            break;
                        case 9: showUrlDialog(); break;
                        case 10: clearData(); break;
                        case 11: finish(); break;
                    }
                })
                .show();
    }

    private void setZoom(int z) {
        z = Math.max(30, Math.min(250, z));
        prefs.edit().putInt(KEY_ZOOM, z).apply();
        applyZoom();
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
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle("Adresa početne stranice")
                .setView(input)
                .setPositiveButton("Sačuvaj", (d, w) -> {
                    String u = input.getText().toString().trim();
                    if (u.isEmpty()) u = DEFAULT_URL;
                    if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
                    prefs.edit().putString(KEY_URL, u).apply();
                    web.loadUrl(u);
                })
                .setNeutralButton("Podrazumevano", (d, w) -> {
                    prefs.edit().remove(KEY_URL).apply();
                    web.loadUrl(DEFAULT_URL);
                })
                .setNegativeButton("Otkaži", null)
                .show();
    }

    private void clearData() {
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        WebStorage.getInstance().deleteAllData();
        web.clearCache(true);
        web.clearHistory();
        web.loadUrl(homeUrl());
        toast("Podaci obrisani.");
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
