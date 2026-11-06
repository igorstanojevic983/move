// Pomoćni skript koji radi unutar stranice (Firefox / GeckoView).
// Komunicira sa aplikacijom preko porta "browser".
(function () {
  "use strict";
  var isTop = (window.top === window);
  var creds = null;
  var port;
  try {
    port = browser.runtime.connectNative("browser");
  } catch (e) {
    return;
  }

  port.onMessage.addListener(function (m) {
    if (!m || !m.type) return;
    if (m.type === "init") {
      creds = m.user ? { u: m.user, p: m.pass || "" } : null;
      setZoom(m.zoom);
      fill();
    } else if (m.type === "zoom") {
      setZoom(m.zoom);
    } else if (m.type === "video") {
      video(m.action);
    } else if (m.type === "type") {
      typeText(m.text, m.enter);
    }
  });

  function setZoom(z) {
    if (!isTop || !z) return;
    document.documentElement.style.zoom = (z === 100) ? "" : String(z / 100);
  }

  function setVal(e, v) {
    e.focus();
    e.value = v;
    e.dispatchEvent(new Event("input", { bubbles: true }));
    e.dispatchEvent(new Event("change", { bubbles: true }));
  }

  function visible(e) {
    return e && e.offsetParent !== null && !e.disabled;
  }

  function fill() {
    if (!creds) return;
    var ps = document.querySelectorAll("input[type=password]");
    for (var i = 0; i < ps.length; i++) {
      var p = ps[i];
      if (!visible(p) || p.dataset.wtvFilled) continue;
      var scope = p.form || document;
      var all = scope.querySelectorAll("input");
      var user = null;
      for (var j = 0; j < all.length; j++) {
        var a = all[j];
        if (a === p) break;
        var t = (a.type || "text").toLowerCase();
        if ((t === "text" || t === "email" || t === "tel") && visible(a)) user = a;
      }
      if (user && !user.dataset.wtvFilled && !user.value) setVal(user, creds.u);
      if (user) user.dataset.wtvFilled = "1";
      if (!p.value && creds.p) setVal(p, creds.p);
      p.blur();
      p.dataset.wtvFilled = "1";
    }
  }
  setInterval(fill, 1500);

  function video(action) {
    var vs = document.querySelectorAll("video");
    for (var i = 0; i < vs.length; i++) {
      var v = vs[i];
      if (v.readyState === 0) continue;
      if (action === "toggle") { if (v.paused) v.play(); else v.pause(); }
      else if (action === "ff") v.currentTime = Math.min(v.duration || 1e9, v.currentTime + 30);
      else if (action === "rew") v.currentTime = Math.max(0, v.currentTime - 30);
      return;
    }
  }

  function typeText(text, enter) {
    var e = document.activeElement;
    if (!e || !("value" in e)) return;
    setVal(e, text);
    if (enter) {
      ["keydown", "keypress", "keyup"].forEach(function (n) {
        e.dispatchEvent(new KeyboardEvent(n, { key: "Enter", code: "Enter", keyCode: 13, which: 13, bubbles: true }));
      });
      if (e.form) { if (e.form.requestSubmit) e.form.requestSubmit(); else e.form.submit(); }
    }
  }
})();
