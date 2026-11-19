/*
 * Tanıtım sayfalarının hareketleri (/nasil-calisir, /ornek-masal, /kahramanlar, /mekan-ve-tema, /guvenlik).
 * motion.js ile aynı kurallar: yalnızca transform ve opacity canlandırılır; "hareketi azalt" açıksa yolculuk ve döngüler
 * kapanır, içerik bitmiş hâliyle görünür. Her parça yalnızca sayfada kendi öğesi varsa çalışır.
 * Etkileşimler (kart çevirme, kaydet denemesi, gizlilik gösterimi, takma ad) Motion olmadan da çalışır.
 */
(function () {
    "use strict";
    var M = window.Motion || null;
    var reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    var wide = window.matchMedia("(min-width: 1100px)");
    var mid = window.matchMedia("(min-width: 900px)");
    var expo = [0.16, 1, 0.3, 1], out = [0.22, 1, 0.36, 1];
    var $ = function (s, c) { return (c || document).querySelector(s); };
    var $$ = function (s, c) { return Array.prototype.slice.call((c || document).querySelectorAll(s)); };
    var clamp01 = function (v) { return v < 0 ? 0 : v > 1 ? 1 : v; };
    var animate = M && !reduce ? M.animate : null;

    // Ortak kaydırma döngüsü: her karede en fazla bir güncelleme
    var ticks = [], queued = false;
    function onFrame(fn) { ticks.push(fn); queue(); }
    function queue() { if (!queued) { queued = true; requestAnimationFrame(function () { queued = false; ticks.forEach(function (f) { f(); }); }); } }
    window.addEventListener("scroll", queue, { passive: true });
    window.addEventListener("resize", queue);

    /* ---------- Döngü videolar: görünürken oynar, ekrandan çıkınca durur; hareket azaltılmışsa yalnızca kapak ---------- */
    function videos() {
        var list = $$("video[data-loop]");
        if (!list.length || reduce || !("IntersectionObserver" in window)) return;
        var io = new IntersectionObserver(function (entries) {
            entries.forEach(function (e) {
                var v = e.target;
                if (e.isIntersecting) {
                    if (v.preload !== "auto") { v.preload = "auto"; }
                    var p = v.play();
                    if (p && p.catch) p.catch(function () {});
                } else if (!v.paused) {
                    v.pause();
                }
            });
        }, { rootMargin: "120px 0px", threshold: 0.15 });
        list.forEach(function (v) { v.muted = true; io.observe(v); });
        document.addEventListener("visibilitychange", function () {
            if (document.hidden) list.forEach(function (v) { v.pause(); });
            else queue();
        });
    }

    /* ---------- Sayfa girişi: başlık satırları yükselir, metin ve video sırayla gelir ---------- */
    function pageHero() {
        var hero = $(".page-hero");
        if (!hero || !animate) return;
        animate($$("[data-line]", hero), { transform: ["translateY(105%)", "translateY(0%)"] },
            { duration: 0.9, ease: expo, delay: M.stagger(0.11, { startDelay: 0.1 }) });
        animate($$("[data-load]", hero), { opacity: [0, 1], transform: ["translateY(14px)", "translateY(0px)"] },
            { duration: 0.55, ease: out, delay: M.stagger(0.08, { startDelay: 0.35 }) });
        var media = $(".page-hero-media", hero);
        if (media) animate(media, { opacity: [0, 1], transform: ["scale(0.96)", "scale(1)"] }, { type: "spring", stiffness: 110, damping: 20, delay: 0.15 });
    }

    /* ---------- Kayma: çerçevedeki video, sayfa kaydıkça çerçeveden biraz daha yavaş hareket eder ---------- */
    function parallax() {
        if (reduce) return;
        $$("[data-parallax]").forEach(function (frame) {
            var inner = frame.querySelector("video, img");
            if (!inner) return;
            inner.style.height = "118%";
            inner.style.top = "-9%";
            onFrame(function () {
                var r = frame.getBoundingClientRect(), vh = window.innerHeight;
                if (r.bottom < 0 || r.top > vh) return;
                var p = clamp01((vh - r.top) / (vh + r.height)); // 0: alttan girer, 1: üstten çıkar
                inner.style.transform = "translateY(" + ((p - 0.5) * -14).toFixed(2) + "%)";
            });
        });
    }

    /* ---------- Üst üste binen mekân panelleri: altta kalan panel hafifçe küçülüp çekilir ---------- */
    function stacks() {
        $$("[data-stack]").forEach(function (list) {
            var items = Array.prototype.slice.call(list.children);
            if (items.length < 2 || reduce) return;
            onFrame(function () {
                var on = mid.matches;
                items.forEach(function (item, i) {
                    var card = item;
                    var next = items[i + 1];
                    if (!on || !next) { card.style.transform = ""; card.style.opacity = ""; card.style.visibility = ""; return; }
                    var top = item.getBoundingClientRect().top, nTop = next.getBoundingClientRect().top;
                    var h = item.getBoundingClientRect().height || 1;
                    var p = clamp01(1 - (nTop - top) / h); // sıradaki panel üstüne bindikçe 0 → 1
                    // Örtüldükçe küçülür ve soluklaşır: altta kalan başlık okunmaz hâlde görünmez, tamamen kaybolur
                    card.style.transform = p > 0 ? "scale(" + (1 - p * 0.06).toFixed(4) + ")" : "";
                    card.style.opacity = p > 0 ? Math.max(0, 1 - p * 1.25).toFixed(3) : "";
                    // Kart büyük ölçüde örtülünce yazısı tamamen gizlenir (soluk da olsa altta kalan başlık görünmesin)
                    card.style.visibility = p > 0.7 ? "hidden" : "";
                });
            });
        });
    }

    /* ---------- Nasıl çalışır: dört adım. Geniş ekranda kaydırmayla, dar ekranda görünürken kendiliğinden ilerler ---------- */
    function steps() {
        var sec = $("[data-steps]");
        if (!sec) return;
        var items = $$(".steps-list li", sec), views = $$(".step-view", sec), bar = $("[data-steps-bar]", sec);
        var type = $("[data-type]", sec), full = type ? type.textContent : "", current = -1, timer = null;
        if (type) {
            // Yazılma etkisi: kelimeler sırayla görünür (metin baştan sayfada; yalnızca görünürlüğü değişir)
            type.innerHTML = full.split(/(\s+)/).map(function (w) { return /^\s+$/.test(w) ? w : "<span>" + w + "</span>"; }).join("");
        }
        var words = type ? $$("span", type) : [];
        function typeTo(frac) {
            var n = Math.round(words.length * clamp01(frac));
            words.forEach(function (w, i) { w.classList.toggle("is-hidden", i >= n); });
        }
        function show(i, frac) {
            if (i !== current) {
                current = i;
                items.forEach(function (li, k) { li.classList.toggle("is-on", k === i); });
                views.forEach(function (v, k) { v.classList.toggle("is-on", k === i); });
            }
            if (type) typeTo(i > 1 ? 1 : i === 1 ? frac : 0);
        }
        function pinned() { return wide.matches && !reduce; }
        function layout() {
            if (pinned()) {
                sec.classList.add("is-pinned");
                sec.style.height = (window.innerHeight * 3.4) + "px";
                stopCycle();
            } else {
                sec.classList.remove("is-pinned");
                sec.style.height = "";
                startCycle();
            }
            queue();
        }
        onFrame(function () {
            if (!sec.classList.contains("is-pinned")) return;
            var r = sec.getBoundingClientRect(), run = r.height - window.innerHeight;
            var p = run > 0 ? clamp01(-r.top / run) : 0;
            var pos = p * items.length, i = Math.min(items.length - 1, Math.floor(pos));
            show(i, pos - i);
            if (bar) bar.style.transform = "scaleX(" + Math.max(0.04, p).toFixed(3) + ")";
        });
        // Dar ekran: görünürken 3.5 sn'de bir sonraki adım; metin adımında kelimeler kendiliğinden yazılır
        var visible = false;
        function tick() {
            var i = (current + 1) % items.length;
            show(i, 0);
            if (i === 1 && !reduce) {
                var start = performance.now();
                (function typeLoop(t) {
                    if (current !== 1) return;
                    typeTo((t - start) / 2600);
                    if (t - start < 2600) requestAnimationFrame(typeLoop);
                })(start);
            }
        }
        function startCycle() {
            if (timer || reduce) { if (reduce) show(0, 1); return; }
            if (current < 0) show(0, 0);
            timer = setInterval(function () { if (visible) tick(); }, 3500);
        }
        function stopCycle() { clearInterval(timer); timer = null; }
        if ("IntersectionObserver" in window) {
            new IntersectionObserver(function (e) { visible = e[0].isIntersecting; }, { threshold: 0.3 }).observe(sec);
        } else { visible = true; }
        wide.addEventListener ? wide.addEventListener("change", layout) : wide.addListener(layout);
        var t = null;
        window.addEventListener("resize", function () { clearTimeout(t); t = setTimeout(layout, 150); });
        show(0, 0);
        layout();
    }

    /* ---------- Yatay sahne şeridi: geniş ekranda bölüm sabitlenir, aşağı kaydırma şeridi sola kaydırır ---------- */
    function hscroll() {
        $$("[data-hscroll]").forEach(function (sec) {
            var row = $("[data-hscroll-row]", sec), extra = 0;
            if (!row) return;
            function layout() {
                if (wide.matches && !reduce) {
                    sec.classList.add("is-pinned");
                    row.style.transform = "";
                    var last = row.lastElementChild;
                    extra = last ? Math.max(0, last.offsetLeft + last.offsetWidth - row.clientWidth + parseFloat(getComputedStyle(row).paddingRight)) : 0;
                    sec.style.height = (window.innerHeight + extra * 0.85) + "px";
                } else {
                    sec.classList.remove("is-pinned");
                    sec.style.height = ""; row.style.transform = ""; extra = 0;
                }
                queue();
            }
            onFrame(function () {
                if (!sec.classList.contains("is-pinned")) return;
                var r = sec.getBoundingClientRect(), run = r.height - window.innerHeight;
                var p = run > 0 ? clamp01(-r.top / run) : 0;
                row.style.transform = "translateX(" + (-p * extra).toFixed(1) + "px)";
            });
            var t = null;
            window.addEventListener("resize", function () { clearTimeout(t); t = setTimeout(layout, 150); });
            $$("img", row).forEach(function (img) { if (!img.complete) img.addEventListener("load", layout, { once: true }); });
            layout();
        });
    }

    /* ---------- Örnek masal: yukarıdaki takma ad sahne şeridinde de görünür ---------- */
    function nameSync() {
        var input = $("#child-name"), spots = $$("[data-name]");
        if (!input || !spots.length) return;
        function sync() {
            var v = input.value.trim() || "Ece";
            spots.forEach(function (s) { s.textContent = v; });
        }
        input.addEventListener("input", sync);
        sync();
    }

    /* ---------- Kahramanlar: karta dokununca döner ---------- */
    function flips() {
        $$("[data-flip]").forEach(function (btn) {
            btn.addEventListener("click", function () {
                btn.setAttribute("aria-pressed", btn.getAttribute("aria-pressed") === "true" ? "false" : "true");
            });
        });
    }

    /* ---------- Nasıl çalışır: kaydet / kaydetme denemesi (5 sn geri al) ---------- */
    function keep() {
        var box = $("[data-keep]");
        if (!box) return;
        var card = $(".keep-card", box), state = $("[data-keep-state]", box), save = $("[data-keep-save]", box), drop = $("[data-keep-drop]", box);
        var timer = null, left = 0;
        function reset(text) {
            clearInterval(timer); timer = null;
            card.classList.remove("is-saved", "is-dropped");
            save.textContent = "Bu masalı kaydet"; drop.textContent = "Kaydetme";
            save.disabled = false; drop.disabled = false;
            state.textContent = text;
        }
        save.addEventListener("click", function () {
            if (card.classList.contains("is-saved")) { reset("Bu masal henüz kaydedilmedi."); return; }
            reset("Kütüphanenize kaydedildi.");
            card.classList.add("is-saved");
            save.textContent = "Kaydı kaldır";
            drop.disabled = true;
            if (animate) animate(card, { transform: ["scale(0.98)", "scale(1)"] }, { type: "spring", stiffness: 300, damping: 18 });
        });
        drop.addEventListener("click", function () {
            if (card.classList.contains("is-dropped")) { reset("Geri alındı. Bu masal henüz kaydedilmedi."); return; }
            card.classList.add("is-dropped");
            save.disabled = true;
            drop.textContent = "Geri al";
            left = 5;
            state.textContent = "Bu masal kaydedilmeyecek. Geri almak için " + left + " sn.";
            timer = setInterval(function () {
                left -= 1;
                if (left > 0) { state.textContent = "Bu masal kaydedilmeyecek. Geri almak için " + left + " sn."; return; }
                clearInterval(timer); timer = null;
                drop.disabled = true;
                state.textContent = "Masal 24 saat sonra silinecek. (Deneme: yeniden başlatmak için sayfayı yenileyin.)";
            }, 1000);
        });
    }

    /* ---------- Güvenlik: takma ad ve yaş üç sütunda güncellenir ---------- */
    function privacy() {
        var box = $("[data-privacy]");
        if (!box) return;
        var name = $("[data-pv-name]", box), age = $("[data-pv-age]", box), steps = $$(".pv-step", box);
        function update(pulse) {
            var n = name.value.trim() || "Ece";
            $$('[data-pv-out="name"]', box).forEach(function (el) { el.textContent = n; });
            $$('[data-pv-out="age"]', box).forEach(function (el) { el.textContent = age.value; });
            if (pulse && !reduce) {
                steps.forEach(function (s, i) {
                    s.classList.remove("is-pulse");
                    setTimeout(function () { void s.offsetWidth; s.classList.add("is-pulse"); }, i * 120);
                });
            }
        }
        var t = null;
        name.addEventListener("input", function () { update(false); clearTimeout(t); t = setTimeout(function () { update(true); }, 350); });
        age.addEventListener("change", function () { update(true); });
        update(false);
    }

    function init() {
        videos(); pageHero(); parallax(); stacks(); steps(); hscroll(); nameSync(); flips(); keep(); privacy();
    }
    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init); else init();
})();
