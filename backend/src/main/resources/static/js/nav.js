/* Uygulama menüsü (giriş sonrası): dar ekranda üst çubuktaki düğme menü panelini açar ve kapatır.
 * Escape ve panel dışına tıklama menüyü kapatır; geniş ekrana geçince panel kapanır (kenar çubuğu her zaman görünür).
 * Betik çalışmazsa CSS menüyü açık gösterir (yalnızca html.js varken gizlenir). */
(function () {
    "use strict";
    // "İlk çocuğu ekle" gibi düğmeler sayfadaki kapalı formu (<details>) açar ve ilk alana odaklanır
    document.addEventListener("click", function (e) {
        var trigger = e.target.closest && e.target.closest("[data-open-details]");
        if (!trigger) return;
        var box = document.getElementById(trigger.getAttribute("data-open-details"));
        if (!box) return;
        e.preventDefault();
        box.open = true;
        var field = box.querySelector("input:not([type=hidden]), select, textarea");
        if (field) field.focus({ preventScroll: true });
        box.scrollIntoView({ behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth", block: "start" });
    });

    var shell = document.querySelector("[data-app-shell]");
    if (!shell) return;
    var btn = shell.querySelector("[data-menu-toggle]");
    var menu = btn && document.getElementById(btn.getAttribute("aria-controls"));
    if (!menu) return;
    // Menünün her zaman açık göründüğü genişlik: uygulamada 1024px, ana sayfada 1200px (data-wide)
    var wide = window.matchMedia(shell.getAttribute("data-wide") || "(min-width: 1024px)");

    function set(open) {
        btn.setAttribute("aria-expanded", open ? "true" : "false");
        btn.setAttribute("aria-label", open ? "Menüyü kapat" : "Menüyü aç");
        menu.classList.toggle("is-open", open);
    }
    function isOpen() { return btn.getAttribute("aria-expanded") === "true"; }

    btn.addEventListener("click", function () { set(!isOpen()); });
    document.addEventListener("keydown", function (e) {
        if (e.key === "Escape" && isOpen()) { set(false); btn.focus(); }
    });
    document.addEventListener("click", function (e) {
        if (isOpen() && !shell.contains(e.target)) set(false);
    });
    // Sayfa içi bağlantıya (ana sayfa bölümleri) tıklanınca panel kapanır
    menu.addEventListener("click", function (e) {
        var a = e.target.closest && e.target.closest("a[href^='#']");
        if (a && isOpen()) set(false);
    });
    // Klavyeyle menüden çıkınca (Tab ile panelin dışına) panel kapanır
    shell.addEventListener("focusout", function (e) {
        if (isOpen() && e.relatedTarget && !shell.contains(e.relatedTarget)) set(false);
    });
    var onWide = function () { if (wide.matches) set(false); };
    if (wide.addEventListener) wide.addEventListener("change", onWide); else wide.addListener(onWide);
})();
