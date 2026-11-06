/*
 * KnowledgeForge shared front-end code. Every logged-in page includes this file.
 *
 * Page usage:
 *   <main id="main" class="page">...</main>
 *   <script src="/app.js"></script>
 *   <script>
 *     KF.initShell({ active: "dashboard", breadcrumbs: [{ label: "Dashboard" }] })
 *       .then(({ me, teams }) => { ... });
 *   </script>
 *
 * Everything lives on window.KF so page scripts don't collide with it.
 */
(function () {
  "use strict";

  /* ---------- Icons (Lucide, ISC licence) ---------- */
  const ICONS = {
    home: '<path d="M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8"/><path d="M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>',
    search: '<circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/>',
    users: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>',
    "user-plus": '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M19 8v6"/><path d="M22 11h-6"/>',
    user: '<path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>',
    folder: '<path d="M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z"/>',
    "file-text": '<path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/><path d="M10 9H8"/><path d="M16 13H8"/><path d="M16 17H8"/>',
    file: '<path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/>',
    image: '<rect width="18" height="18" x="3" y="3" rx="2" ry="2"/><circle cx="9" cy="9" r="2"/><path d="m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"/>',
    sheet: '<rect width="18" height="18" x="3" y="3" rx="2"/><path d="M3 9h18"/><path d="M3 15h18"/><path d="M9 3v18"/>',
    plus: '<path d="M5 12h14"/><path d="M12 5v14"/>',
    "log-out": '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="m16 17 5-5-5-5"/><path d="M21 12H9"/>',
    menu: '<path d="M4 12h16"/><path d="M4 6h16"/><path d="M4 18h16"/>',
    x: '<path d="M18 6 6 18"/><path d="m6 6 12 12"/>',
    check: '<path d="M20 6 9 17l-5-5"/>',
    "check-circle": '<circle cx="12" cy="12" r="10"/><path d="m9 12 2 2 4-4"/>',
    "alert-circle": '<circle cx="12" cy="12" r="10"/><path d="M12 8v4"/><path d="M12 16h.01"/>',
    info: '<circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/>',
    trash: '<path d="M3 6h18"/><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/>',
    sparkles: '<path d="M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z"/><path d="M20 3v4"/><path d="M22 5h-4"/><path d="M4 17v2"/><path d="M5 18H3"/>',
    download: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><path d="m7 10 5 5 5-5"/><path d="M12 15V3"/>',
    upload: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><path d="m17 8-5-5-5 5"/><path d="M12 3v12"/>',
    link: '<path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/>',
    paperclip: '<path d="m21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l8.57-8.57A4 4 0 1 1 18 8.84l-8.59 8.57a2 2 0 0 1-2.83-2.83l8.49-8.48"/>',
    "chevron-down": '<path d="m6 9 6 6 6-6"/>',
    "chevron-right": '<path d="m9 18 6-6-6-6"/>',
    more: '<circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/><circle cx="5" cy="12" r="1"/>',
    copy: '<rect width="14" height="14" x="8" y="8" rx="2" ry="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/>',
    pencil: '<path d="M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z"/><path d="m15 5 4 4"/>',
    history: '<path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/><path d="M12 7v5l4 2"/>',
    message: '<path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>',
    inbox: '<path d="M22 12h-6l-2 3h-4l-2-3H2"/><path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z"/>',
    eye: '<path d="M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0"/><circle cx="12" cy="12" r="3"/>',
    "eye-off": '<path d="M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49"/><path d="M14.084 14.158a3 3 0 0 1-4.242-4.242"/><path d="M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143"/><path d="m2 2 20 20"/>',
    tag: '<path d="M12.586 2.586A2 2 0 0 0 11.172 2H4a2 2 0 0 0-2 2v7.172a2 2 0 0 0 .586 1.414l8.704 8.704a2.426 2.426 0 0 0 3.42 0l6.58-6.58a2.426 2.426 0 0 0 0-3.42z"/><circle cx="7.5" cy="7.5" r=".5"/>',
    clock: '<circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/>',
    refresh: '<path d="M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8"/><path d="M21 3v5h-5"/>',
    layers: '<path d="M12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83z"/><path d="M2 12a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 12"/><path d="M2 17a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 17"/>'
  };

  /** Inline SVG icon. Decorative by default (aria-hidden); pass a label to make it meaningful. */
  function icon(name, cls, label) {
    const body = ICONS[name] || ICONS.file;
    const a11y = label ? 'role="img" aria-label="' + escapeHtml(label) + '"' : 'aria-hidden="true" focusable="false"';
    return '<svg class="icon' + (cls ? " " + cls : "") + '" viewBox="0 0 24 24" ' + a11y + ">" + body + "</svg>";
  }

  /* ---------- Text helpers ---------- */
  function escapeHtml(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
  }

  const TINTS = ["peach", "rose", "mint", "lavender", "sky", "yellow", "gray"];

  /** Same name -> same tint, always. */
  function tintFor(name) {
    const s = String(name || "").trim().toLowerCase();
    let h = 0;
    for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0;
    return TINTS[Math.abs(h) % TINTS.length];
  }

  function initials(name) {
    const parts = String(name || "").trim().split(/\s+/).filter(Boolean);
    if (!parts.length) return "?";
    const first = parts[0][0];
    const last = parts.length > 1 ? parts[parts.length - 1][0] : "";
    return (first + last).toUpperCase();
  }

  /** Avatar HTML. user: { fullName, email, profilePicture }. size: "sm" | "md" | "lg". */
  function avatar(user, size) {
    const name = (user && (user.fullName || user.email)) || "Unknown user";
    const sizeCls = size && size !== "md" ? " avatar--" + size : "";
    const pic = user && user.profilePicture;
    // External links (older profiles) or our own uploaded picture URL
    if (pic && /^(https?:\/\/|\/api\/users\/)/i.test(pic)) {
      return '<span class="avatar' + sizeCls + '"><img src="' + escapeHtml(pic) + '" alt="' + escapeHtml(name) + '" loading="lazy"></span>';
    }
    return '<span class="avatar tint-' + tintFor(name) + sizeCls + '" role="img" aria-label="' + escapeHtml(name) + '">' + escapeHtml(initials(name)) + "</span>";
  }

  /** Team / space square icon with first letter. */
  function teamIcon(name, size) {
    const sizeCls = size && size !== "md" ? " team-icon--" + size : "";
    const letter = (String(name || "?").trim()[0] || "?").toUpperCase();
    return '<span class="team-icon tint-' + tintFor(name) + sizeCls + '" aria-hidden="true">' + escapeHtml(letter) + "</span>";
  }

  /** Space icon: folder glyph on the name's tint. Letters are for teams only. */
  function spaceIcon(name, size) {
    const sizeCls = size && size !== "md" ? " team-icon--" + size : "";
    return '<span class="team-icon tint-' + tintFor(name) + sizeCls + '" aria-hidden="true">' + icon("folder", size === "lg" ? "icon--lg" : "") + "</span>";
  }

  function tag(name, href) {
    const cls = "tag tint-" + tintFor(name);
    return href
      ? '<a class="' + cls + '" href="' + escapeHtml(href) + '">' + escapeHtml(name) + "</a>"
      : '<span class="' + cls + '">' + escapeHtml(name) + "</span>";
  }

  /* ---------- Dates & sizes ---------- */
  const dateFmt = new Intl.DateTimeFormat(undefined, { day: "numeric", month: "short", year: "numeric" });
  const timeFmt = new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit" });

  function formatDate(value) {
    const d = new Date(value);
    return isNaN(d) ? "-" : dateFmt.format(d);
  }

  /** "just now", "5 min ago", "3 h ago", "yesterday", "4 days ago", then the date. */
  function relativeTime(value) {
    const d = new Date(value);
    if (isNaN(d)) return "-";
    const sec = Math.round((Date.now() - d.getTime()) / 1000);
    if (sec < 45) return "just now";
    const min = Math.round(sec / 60);
    if (min < 60) return min + " min ago";
    const hr = Math.round(min / 60);
    if (hr < 24) return hr + " h ago";
    const day = Math.round(hr / 24);
    if (day === 1) return "yesterday";
    if (day < 7) return day + " days ago";
    return formatDate(d);
  }

  function formatTime(value) {
    const d = new Date(value);
    return isNaN(d) ? "-" : timeFmt.format(d);
  }

  function formatSize(bytes) {
    const n = Number(bytes) || 0;
    if (n < 1024) return n + " B";
    if (n < 1024 * 1024) return (n / 1024).toFixed(n < 10240 ? 1 : 0) + " KB";
    return (n / (1024 * 1024)).toFixed(1) + " MB";
  }

  /* ---------- Fetch wrapper ---------- */
  function goToLogin() {
    const here = location.pathname + location.search;
    const next = /^\/(login|register)\.html/.test(location.pathname) ? "" : "?next=" + encodeURIComponent(here);
    location.href = "/login.html" + next;
  }

  /**
   * fetch() with the session cookie. Sends `json` as a JSON body. Returns parsed JSON (or null / text).
   * 401 -> redirect to login (unless opts.redirectOn401 === false). Other errors throw Error(message) with .status.
   */
  async function api(url, opts) {
    const o = Object.assign({ credentials: "include", headers: {} }, opts || {});
    if (o.json !== undefined) {
      o.body = JSON.stringify(o.json);
      o.headers = Object.assign({ "Content-Type": "application/json" }, o.headers);
      if (!o.method) o.method = "POST";
      delete o.json;
    }
    const redirect = o.redirectOn401 !== false;
    delete o.redirectOn401;

    let res;
    try {
      res = await fetch(url, o);
    } catch (e) {
      const err = new Error("Can't reach the server. Check your connection and try again.");
      err.status = 0;
      throw err;
    }

    if (res.status === 401 && redirect) {
      goToLogin();
      // Keep callers waiting while the browser navigates away.
      return new Promise(function () {});
    }

    const text = await res.text();
    let data = null;
    if (text) {
      try { data = JSON.parse(text); } catch (e) { data = text; }
    }
    if (!res.ok) {
      const msg = (data && typeof data === "object" && (data.message || data.error)) || "Request failed (" + res.status + ")";
      const err = new Error(msg);
      err.status = res.status;
      err.data = data;
      throw err;
    }
    return data;
  }

  /* ---------- Toasts ---------- */
  let toastRegion = null;

  function ensureToastRegion() {
    if (toastRegion && document.body.contains(toastRegion)) return toastRegion;
    toastRegion = document.createElement("div");
    toastRegion.className = "toast-region";
    toastRegion.setAttribute("role", "status");
    toastRegion.setAttribute("aria-live", "polite");
    document.body.appendChild(toastRegion);
    return toastRegion;
  }

  /** toast("Saved") or toast("Couldn't save", { type: "error" }). Auto-hides after 4s. */
  function toast(message, opts) {
    const type = (opts && opts.type) || "success";
    const iconName = type === "error" ? "alert-circle" : type === "info" ? "info" : "check-circle";
    const el = document.createElement("div");
    el.className = "toast toast--" + type;
    el.innerHTML = icon(iconName) + '<div class="toast__msg">' + escapeHtml(message) + "</div>" +
      '<button type="button" class="toast__close" aria-label="Dismiss">' + icon("x", "icon--sm") + "</button>";
    const region = ensureToastRegion();
    if (type === "error") region.setAttribute("aria-live", "assertive");
    region.appendChild(el);

    let timer;
    function close() {
      clearTimeout(timer);
      el.classList.add("is-leaving");
      setTimeout(function () { el.remove(); region.setAttribute("aria-live", "polite"); }, 160);
    }
    el.querySelector(".toast__close").addEventListener("click", close);
    timer = setTimeout(close, (opts && opts.duration) || 4000);
    el.addEventListener("mouseenter", function () { clearTimeout(timer); });
    el.addEventListener("mouseleave", function () { timer = setTimeout(close, 2000); });
    return close;
  }

  /* ---------- Modal + confirm dialog (native <dialog>: focus trap + Esc built in) ---------- */

  /** Open a <dialog class="modal">. Closes on backdrop click. Returns the dialog. */
  function openModal(dialog) {
    if (!dialog.dataset.kfBound) {
      dialog.dataset.kfBound = "1";
      dialog.addEventListener("click", function (e) {
        if (e.target === dialog) dialog.close("cancel"); // click landed on the backdrop
      });
      dialog.querySelectorAll("[data-close]").forEach(function (b) {
        b.addEventListener("click", function () { dialog.close("cancel"); });
      });
    }
    dialog._returnFocus = document.activeElement;
    dialog.showModal();
    dialog.addEventListener("close", function restore() {
      dialog.removeEventListener("close", restore);
      if (dialog._returnFocus && dialog._returnFocus.focus) dialog._returnFocus.focus();
    });
    return dialog;
  }

  /**
   * await confirmDialog({ title, message, confirmLabel, danger }) -> true / false.
   * Use for every delete/remove action instead of window.confirm().
   */
  function confirmDialog(opts) {
    const o = Object.assign({ title: "Are you sure?", message: "", confirmLabel: "Confirm", cancelLabel: "Cancel", danger: true }, opts || {});
    return new Promise(function (resolve) {
      const d = document.createElement("dialog");
      d.className = "modal";
      d.setAttribute("aria-labelledby", "kf-confirm-title");
      d.innerHTML =
        '<div class="modal__header"><h2 class="title-md" id="kf-confirm-title">' + escapeHtml(o.title) + "</h2></div>" +
        (o.message ? '<div class="modal__body"><p>' + escapeHtml(o.message) + "</p></div>" : '<div class="modal__body"></div>') +
        '<div class="modal__footer">' +
        '<button type="button" class="btn btn--secondary" value="cancel">' + escapeHtml(o.cancelLabel) + "</button>" +
        '<button type="button" class="btn ' + (o.danger ? "btn--danger-solid" : "btn--primary") + '" value="ok">' + escapeHtml(o.confirmLabel) + "</button>" +
        "</div>";
      document.body.appendChild(d);
      d.querySelectorAll("button[value]").forEach(function (b) {
        b.addEventListener("click", function () { d.close(b.value); });
      });
      d.addEventListener("close", function () {
        resolve(d.returnValue === "ok");
        setTimeout(function () { d.remove(); }, 0);
      });
      openModal(d);
      d.querySelector('button[value="cancel"]').focus(); // safe default for destructive actions
    });
  }

  /** Put a button into its loading state while `work` runs. */
  async function withLoading(button, work) {
    button.classList.add("is-loading");
    button.setAttribute("aria-busy", "true");
    button.disabled = true;
    try { return await work(); }
    finally {
      button.classList.remove("is-loading");
      button.removeAttribute("aria-busy");
      button.disabled = false;
    }
  }

  /* ---------- Dropdown menus: <div class="dropdown"><button aria-haspopup="menu" aria-expanded="false">..</button><div class="menu" role="menu" hidden>..</div></div> ---------- */
  function closeMenus(except) {
    document.querySelectorAll(".dropdown > [aria-expanded='true']").forEach(function (btn) {
      if (btn === except) return;
      btn.setAttribute("aria-expanded", "false");
      const m = btn.parentElement.querySelector(".menu");
      if (m) m.hidden = true;
    });
  }

  document.addEventListener("click", function (e) {
    const trigger = e.target.closest(".dropdown > [aria-haspopup]");
    if (trigger) {
      const menu = trigger.parentElement.querySelector(".menu");
      const open = trigger.getAttribute("aria-expanded") !== "true";
      closeMenus(trigger);
      trigger.setAttribute("aria-expanded", String(open));
      menu.hidden = !open;
      if (open) { const first = menu.querySelector(".menu__item"); if (first) first.focus(); }
      return;
    }
    if (!e.target.closest(".menu")) closeMenus();
  });

  document.addEventListener("keydown", function (e) {
    const menu = e.target.closest && e.target.closest(".menu");
    if (e.key === "Escape") {
      const openBtn = document.querySelector(".dropdown > [aria-expanded='true']");
      if (openBtn) { closeMenus(); openBtn.focus(); }
    }
    if (menu && (e.key === "ArrowDown" || e.key === "ArrowUp")) {
      e.preventDefault();
      const items = Array.from(menu.querySelectorAll(".menu__item"));
      const i = items.indexOf(document.activeElement);
      const next = e.key === "ArrowDown" ? (i + 1) % items.length : (i - 1 + items.length) % items.length;
      items[next].focus();
    }
  });

  /* ---------- Show/hide password: <button data-toggle-password="inputId" aria-pressed="false"> ---------- */
  document.addEventListener("click", function (e) {
    const btn = e.target.closest("[data-toggle-password]");
    if (!btn) return;
    const input = document.getElementById(btn.getAttribute("data-toggle-password"));
    if (!input) return;
    const show = input.type === "password";
    input.type = show ? "text" : "password";
    btn.setAttribute("aria-pressed", String(show));
    btn.setAttribute("aria-label", show ? "Hide password" : "Show password");
    btn.innerHTML = icon(show ? "eye-off" : "eye");
  });

  /* ---------- Tabs:<div role="tablist"><button role="tab" aria-controls="p1">..</button></div> ---------- */
  function initTabs(tablist, onChange) {
    const tabs = Array.from(tablist.querySelectorAll('[role="tab"]'));
    function select(tab, focus) {
      tabs.forEach(function (t) {
        const on = t === tab;
        t.setAttribute("aria-selected", String(on));
        t.tabIndex = on ? 0 : -1;
        const panel = document.getElementById(t.getAttribute("aria-controls"));
        if (panel) panel.hidden = !on;
      });
      if (focus) tab.focus();
      if (onChange) onChange(tab);
    }
    tabs.forEach(function (t, i) {
      t.addEventListener("click", function () { select(t); });
      t.addEventListener("keydown", function (e) {
        if (e.key !== "ArrowRight" && e.key !== "ArrowLeft") return;
        const n = e.key === "ArrowRight" ? (i + 1) % tabs.length : (i - 1 + tabs.length) % tabs.length;
        select(tabs[n], true);
      });
    });
    select(tabs.find(function (t) { return t.getAttribute("aria-selected") === "true"; }) || tabs[0]);
    return { select: select };
  }

  /* ---------- Shell: sidebar + top bar ---------- */
  let shellState = { me: null, teams: [] };

  function sidebarHtml(active, me, teams) {
    function nav(href, label, iconName, key) {
      const on = key === active;
      return '<a class="nav-item' + (on ? " is-active" : "") + '" href="' + href + '" title="' + escapeHtml(label) + '"' +
        (on ? ' aria-current="page"' : "") + ">" + icon(iconName) + '<span class="nav-item__label">' + escapeHtml(label) + "</span></a>";
    }
    const teamLinks = teams.length
      ? teams.map(function (t) {
          const on = active === t.teamId;
          const name = t.teamName || "Untitled team";
          return '<a class="nav-item' + (on ? " is-active" : "") + '" href="/team.html?id=' + encodeURIComponent(t.teamId) + '" title="' + escapeHtml(name) + '"' +
            (on ? ' aria-current="page"' : "") + ">" + teamIcon(t.teamName, "sm") + '<span class="nav-item__label">' + escapeHtml(name) + "</span></a>";
        }).join("")
      : '<p class="sidebar__empty">No teams yet</p>';
    const name = (me && (me.fullName || me.email)) || "";

    return '<a class="sidebar__brand" href="/dashboard.html" title="KnowledgeForge"><span class="brand-mark">' + icon("layers") + '</span><span class="sidebar__brand-name">KnowledgeForge</span></a>' +
      '<nav class="sidebar__section" aria-label="Main">' +
        nav("/dashboard.html", "Dashboard", "home", "dashboard") +
        nav("/search.html", "Search", "search", "search") +
      "</nav>" +
      '<nav class="sidebar__section" aria-labelledby="kf-teams-label">' +
        '<div class="sidebar__label"><span class="overline" id="kf-teams-label">Teams</span></div>' +
        teamLinks +
      "</nav>" +
      '<div class="sidebar__footer">' +
        '<a class="sidebar__user" href="/profile.html" title="Profile"' + (active === "profile" ? ' aria-current="page"' : "") + ">" +
          avatar(me, "sm") + '<span class="sidebar__user-name truncate body-medium">' + escapeHtml(name) + "</span></a>" +
        '<button type="button" class="btn btn--ghost btn--icon btn--sm" data-kf-logout aria-label="Log out" title="Log out">' + icon("log-out") + "</button>" +
      "</div>";
  }

  function breadcrumbsHtml(items) {
    if (!items || !items.length) return "";
    return '<nav class="breadcrumbs" aria-label="Breadcrumb"><ol>' + items.map(function (b, i) {
      const last = i === items.length - 1;
      if (last || !b.href) return "<li><span" + (last ? ' aria-current="page"' : "") + ">" + escapeHtml(b.label) + "</span></li>";
      return '<li><a href="' + escapeHtml(b.href) + '">' + escapeHtml(b.label) + "</a></li>";
    }).join("") + "</ol></nav>";
  }

  /* ---------- Site footer (every page) ---------- */
  const REPO_URL = "https://github.com/Lut4Sayem/knowledgeforge-ai";

  function footerHtml() {
    return '<span>&copy; ' + new Date().getFullYear() + " KnowledgeForge</span>" +
      '<nav class="site-footer__links" aria-label="Footer"><a href="' + REPO_URL + '" target="_blank" rel="noopener">Source on GitHub</a></nav>';
  }

  /** Fill every <footer data-kf-footer> on the page (auth pages place one by hand; the shell adds its own). */
  function renderFooters() {
    document.querySelectorAll("[data-kf-footer]").forEach(function (f) {
      f.classList.add("site-footer");
      f.innerHTML = footerHtml();
    });
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", renderFooters);
  else renderFooters();

  /** Update breadcrumbs after data loads (e.g. team name known). */
  function setBreadcrumbs(items) {
    const slot = document.getElementById("kf-breadcrumbs");
    if (slot) slot.innerHTML = breadcrumbsHtml(items);
  }

  function setDrawer(open) {
    const sb = document.getElementById("kf-sidebar");
    const bd = document.getElementById("kf-backdrop");
    const btn = document.getElementById("kf-menu-btn");
    if (!sb) return;
    sb.classList.toggle("is-open", open);
    bd.classList.toggle("is-open", open);
    btn.setAttribute("aria-expanded", String(open));
    if (open) { const first = sb.querySelector("a, button"); if (first) first.focus(); }
    else if (sb.contains(document.activeElement)) btn.focus();
  }

  async function logout() {
    try { await fetch("/logout", { method: "POST", credentials: "include" }); } catch (e) { /* go to login anyway */ }
    location.href = "/login.html";
  }

  /**
   * Build the app shell around <main id="main">, then load /api/me and /api/me/teams.
   * opts: { active: "dashboard" | "search" | "profile" | <teamId>, breadcrumbs: [{label, href}], actions: html string }
   * Resolves { me, teams }. A 401 redirects to login.
   */
  async function initShell(opts) {
    const o = opts || {};
    const main = document.getElementById("main") || document.querySelector("main");
    main.id = "main";
    main.tabIndex = -1;

    const skip = document.createElement("a");
    skip.className = "skip-link";
    skip.href = "#main";
    skip.textContent = "Skip to content";

    const app = document.createElement("div");
    app.className = "app";
    app.innerHTML =
      '<aside class="sidebar" id="kf-sidebar" aria-label="Sidebar">' + sidebarHtml(o.active, null, []) + "</aside>" +
      '<div class="drawer-backdrop" id="kf-backdrop" aria-hidden="true"></div>' +
      '<header class="topbar">' +
        '<button type="button" class="btn btn--ghost btn--icon topbar__menu" id="kf-menu-btn" aria-label="Open menu" aria-controls="kf-sidebar" aria-expanded="false">' + icon("menu", "icon--lg") + "</button>" +
        '<div id="kf-breadcrumbs" class="breadcrumbs-slot" style="min-width:0">' + breadcrumbsHtml(o.breadcrumbs) + "</div>" +
        '<div class="topbar__actions" id="kf-actions">' + (o.actions || "") + "</div>" +
      "</header>" +
      '<div class="sheet"></div>';

    document.body.insertBefore(skip, document.body.firstChild);
    main.parentNode.insertBefore(app, main);
    app.querySelector(".sheet").appendChild(main);
    const footer = document.createElement("footer");
    footer.className = "site-footer";
    footer.innerHTML = footerHtml();
    app.querySelector(".sheet").appendChild(footer);
    ensureToastRegion();

    document.getElementById("kf-menu-btn").addEventListener("click", function () {
      setDrawer(!document.getElementById("kf-sidebar").classList.contains("is-open"));
    });
    document.getElementById("kf-backdrop").addEventListener("click", function () { setDrawer(false); });
    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape" && document.getElementById("kf-sidebar").classList.contains("is-open")) setDrawer(false);
    });
    app.addEventListener("click", function (e) {
      if (e.target.closest("[data-kf-logout]")) logout();
    });

    const results = await Promise.all([api("/api/me"), api("/api/me/teams").catch(function () { return []; })]);
    shellState = { me: results[0], teams: Array.isArray(results[1]) ? results[1] : [] };
    document.getElementById("kf-sidebar").innerHTML = sidebarHtml(o.active, shellState.me, shellState.teams);
    return shellState;
  }

  /** shellState.teams is [{ teamId, teamName, role }] from /api/me/teams. Needs initShell first. */
  /** Role of the current user in a team ("ADMIN" | "EDITOR" | "VIEWER" | null). Needs initShell first. */
  function roleIn(teamId) {
    const t = shellState.teams.find(function (x) { return x.teamId === teamId; });
    return t ? t.role : null;
  }
  function canEdit(teamId) {
    const r = roleIn(teamId);
    return r === "ADMIN" || r === "EDITOR";
  }

  /* ---------- Global shortcut: Ctrl/Cmd+K focuses search (or opens the search page) ---------- */
  document.addEventListener("keydown", function (e) {
    if ((e.ctrlKey || e.metaKey) && (e.key === "k" || e.key === "K")) {
      const field = document.querySelector("[data-search-input]");
      if (field) { e.preventDefault(); field.focus(); field.select(); }
      else if (document.getElementById("kf-sidebar")) { e.preventDefault(); location.href = "/search.html"; }
    }
  });

  window.KF = {
    api: api,
    icon: icon,
    escapeHtml: escapeHtml,
    tintFor: tintFor,
    initials: initials,
    avatar: avatar,
    teamIcon: teamIcon,
    spaceIcon: spaceIcon,
    tag: tag,
    formatDate: formatDate,
    formatTime: formatTime,
    relativeTime: relativeTime,
    formatSize: formatSize,
    toast: toast,
    openModal: openModal,
    confirmDialog: confirmDialog,
    withLoading: withLoading,
    initTabs: initTabs,
    initShell: initShell,
    setBreadcrumbs: setBreadcrumbs,
    roleIn: roleIn,
    teams: function () { return shellState.teams; },
    canEdit: canEdit,
    logout: logout,
    goToLogin: goToLogin
  };
})();
