/*
 * Masal Dünyası hareket katmanı. Motion (motion.dev, Framer Motion'ın React gerektirmeyen sürümü) ile çalışır.
 * Kurallar: yalnızca transform, opacity ve clip-path canlandırılır; giriş hareketleri bir kez oynar;
 * "hareketi azalt" açıksa yolculuk, ölçek ve döngüler kapanır, içerik bitmiş hâliyle görünür.
 * Gizli başlangıç durumları CSS'te yalnızca html.js altında tanımlıdır; bu dosya hiç çalışmazsa
 * <head> içindeki zamanlayıcı 3 saniye sonra html.js sınıfını kaldırır ve her şey görünür olur.
 */
(function () {
    "use strict";
    var root = document.documentElement;

    /* ---------- Kitap sayfası sığdırma (hareket kitaplığından bağımsız, her zaman çalışır) ----------
     * Form sayfası ekran yüksekliğine sığmazsa süsler kademeli olarak gizlenir (data-fit 1-4, CSS'te tanımlı).
     * Açılışta, yazı tipleri yüklenince, pencere boyutu değişince ve sayfa çevrilince yeniden ölçülür. */
    function bookFit(leaf) {
        var copy = leaf && leaf.querySelector(".book-copy");
        if (!copy || leaf.hidden) return;
        leaf.removeAttribute("data-fit");
        for (var lvl = 1; lvl <= 4 && copy.scrollHeight - copy.clientHeight > 0; lvl++) leaf.setAttribute("data-fit", String(lvl));
    }
    function fitAll() { Array.prototype.forEach.call(document.querySelectorAll("[data-book] .book-form"), bookFit); }
    window.bookFit = bookFit;
    if (document.querySelector("[data-book]")) {
        fitAll();
        if (document.fonts && document.fonts.ready) document.fonts.ready.then(fitAll);
        var fitTimer = null;
        window.addEventListener("resize", function () { clearTimeout(fitTimer); fitTimer = setTimeout(fitAll, 120); });
    }

    var M = window.Motion;
    if (!M) { root.classList.remove("js"); return; }
    window.motionReady = true;

    var animate = M.animate, inView = M.inView, scroll = M.scroll, stagger = M.stagger, hover = M.hover;
    var reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    var expo = [0.16, 1, 0.3, 1], out = [0.22, 1, 0.36, 1], inOut = [0.76, 0, 0.24, 1];
    var $ = function (sel, ctx) { return (ctx || document).querySelector(sel); };
    var $$ = function (sel, ctx) { return Array.prototype.slice.call((ctx || document).querySelectorAll(sel)); };

    /* ---------- Ana sayfa açılışı: 1.2 saniyede biter, en fazla dört öğe ---------- */
    function heroLoad() {
        var hero = $(".hero");
        if (!hero || reduce) return;
        animate($$("[data-line]", hero), { transform: ["translateY(105%)", "translateY(0%)"] },
            { duration: 0.9, ease: expo, delay: stagger(0.11, { startDelay: 0.12 }) });
        animate($$("[data-load]", hero), { opacity: [0, 1], transform: ["translateY(14px)", "translateY(0px)"] },
            { duration: 0.55, ease: out, delay: stagger(0.06, { startDelay: 0.42 }) });
        var win = $(".hero-stage .apple-window", hero);
        if (win) animate(win, { opacity: [0, 1], transform: ["scale(0.92) rotate(-4deg)", "scale(1) rotate(0deg)"] },
            { type: "spring", stiffness: 120, damping: 18, delay: 0.15 });
    }

    /* ---------- İmza hareketi: masal levhaları elma penceresinde yavaşça yer değiştirir ---------- */
    function crossfade() {
        var box = $("[data-crossfade]");
        if (!box || reduce) return;
        var imgs = $$("img", box), i = 0, timer = null;
        if (imgs.length < 2) return;
        function next() {
            // Sayfa çevrilince levha dışarıdan değişebilir; sıradaki levha her seferinde görünenden hesaplanır
            for (var k = 0; k < imgs.length; k++) if (imgs[k].classList.contains("is-shown")) i = k;
            imgs[i].classList.remove("is-shown");
            i = (i + 1) % imgs.length;
            imgs[i].classList.add("is-shown");
        }
        function start() { if (!timer) timer = setInterval(next, 5600); }
        function stop() { clearInterval(timer); timer = null; }
        document.addEventListener("visibilitychange", function () { document.hidden ? stop() : start(); });
        // Sayfa çevrilince sayaç baştan başlar: yeni levha hemen ardından değişmez
        box.addEventListener("plate:reset", function () { if (timer) { stop(); start(); } });
        inView(box, function () { start(); return stop; });
    }

    /* ---------- Giriş ⇄ kayıt: aynı kitabın sayfası çevrilir ----------
     * İleri (giriş → kayıt): sağ sayfa sırttan kalkıp sola yatar; arka yüzünde yeni levha vardır, altta kayıt formu belirir.
     * Geri (kayıt → giriş): soldan bir yaprak kalkıp sağa yatar; ön yüzünde giriş formu vardır.
     * Dar ekranda tek sayfa olduğu için sayfa sırttan yarım döner, içerik değişir, geri döner.
     * Adres çubuğu /login ⇄ /register olarak güncellenir; tarayıcının geri düğmesi sayfayı geri çevirir. */
    function bookTurn() {
        var book = $("[data-book]");
        if (!book) return;
        var leaves = { login: $('[data-leaf="login"]', book), register: $('[data-leaf="register"]', book) };
        if (!leaves.login || !leaves.register) return;
        var plate = $(".book-plate", book), frame = plate && $("[data-crossfade]", plate), plateFolio = plate && $(".folio", plate);
        var paths = { login: "/login", register: "/register" }, order = { login: 0, register: 1 };
        var mode = book.getAttribute("data-mode") || "login", busy = false;

        function showPlate(file, instant) {
            if (!frame) return;
            if (instant) frame.classList.add("no-fade");
            $$("img", frame).forEach(function (img) { img.classList.toggle("is-shown", img.getAttribute("data-file") === file); });
            if (instant) { void frame.offsetWidth; requestAnimationFrame(function () { frame.classList.remove("no-fade"); }); }
        }
        function setLeft(to) {
            showPlate(leaves[to].getAttribute("data-plate"), true);
            if (frame) frame.dispatchEvent(new Event("plate:reset"));
            if (plateFolio) plateFolio.textContent = leaves[to].getAttribute("data-folio");
        }
        function setRight(to) {
            leaves[to].hidden = false;
            leaves[to].style.opacity = "1";
            leaves[to].style.transform = "";
            leaves[to === "login" ? "register" : "login"].hidden = true;
            bookFit(leaves[to]);
        }
        function settle(to) {
            mode = to;
            book.setAttribute("data-mode", to);
            document.title = leaves[to].getAttribute("data-title") + " · Masal Dünyası";
            var h = $(".auth-title", leaves[to]);
            if (h) h.focus({ preventScroll: true });
            busy = false;
        }
        // Çevrilen yaprağın yüzleri: gerçek sayfanın kopyası, kimlikleri silinmiş, ekran okuyucudan gizli
        function face(src, cls) {
            var c = src.cloneNode(true);
            c.removeAttribute("hidden"); c.removeAttribute("data-leaf"); c.removeAttribute("data-crossfade");
            $$("[id]", c).forEach(function (n) { n.removeAttribute("id"); });
            $$("[data-crossfade]", c).forEach(function (n) { n.removeAttribute("data-crossfade"); n.classList.add("no-fade"); });
            c.style.opacity = "1"; c.style.transform = "";
            c.classList.add("leaf-face", cls);
            var shade = document.createElement("span");
            shade.className = "leaf-shade";
            c.appendChild(shade);
            return c;
        }
        function plateFace(key) {
            var c = face(plate, "leaf-back"), file = leaves[key].getAttribute("data-plate");
            $$("img", c).forEach(function (img) { img.classList.toggle("is-shown", img.getAttribute("data-file") === file); img.loading = "eager"; });
            var f = $(".folio", c);
            if (f) f.textContent = leaves[key].getAttribute("data-folio");
            return c;
        }

        function turnWide(from, to) {
            var forward = order[to] > order[from];
            var r = leaves[from].getBoundingClientRect(), b = book.getBoundingClientRect();
            var leaf = document.createElement("div");
            leaf.className = "leaf-turn";
            leaf.setAttribute("aria-hidden", "true");
            leaf.style.left = (r.left - b.left) + "px"; leaf.style.top = (r.top - b.top) + "px";
            leaf.style.width = r.width + "px"; leaf.style.height = r.height + "px";
            var front, back;
            if (forward) {
                front = face(leaves[from], "leaf-front");                   // eski sağ sayfa kalkar
                back = plateFace(to);    // arkasında yeni levha
            } else {
                front = face(leaves[to], "leaf-front");                     // yeni sağ sayfa iner
                leaves[to].hidden = true;
                back = plateFace(from);  // kalkan yaprağın arkasında eski levha
            }
            leaf.appendChild(front); leaf.appendChild(back);
            book.appendChild(leaf);
            if (forward) setRight(to); else setLeft(to);

            var rot = forward ? ["perspective(2400px) rotateY(0deg)", "perspective(2400px) rotateY(-180deg)"]
                              : ["perspective(2400px) rotateY(-180deg)", "perspective(2400px) rotateY(0deg)"];
            var opts = { duration: 0.95, ease: [0.645, 0.045, 0.355, 1] };
            $$(".leaf-shade", leaf).forEach(function (s) { animate(s, { opacity: [0, 0.32, 0] }, opts); });
            animate(leaf, { transform: rot }, opts).then(function () {
                if (forward) setLeft(to); else setRight(to);
                leaf.remove();
                settle(to);
            });
        }

        function turnNarrow(from, to) {
            var forward = order[to] > order[from], sign = forward ? -1 : 1;
            animate(leaves[from], { transform: ["perspective(1600px) rotateY(0deg)", "perspective(1600px) rotateY(" + (sign * 90) + "deg)"], opacity: [1, 0.6] },
                { duration: 0.32, ease: [0.55, 0, 1, 0.45] }).then(function () {
                leaves[from].style.transform = "";
                setRight(to); setLeft(to);
                animate(leaves[to], { transform: ["perspective(1600px) rotateY(" + (-sign * 90) + "deg)", "perspective(1600px) rotateY(0deg)"], opacity: [0.6, 1] },
                    { duration: 0.42, ease: expo }).then(function () { settle(to); });
            });
        }

        function turn(to, push) {
            if (busy || to === mode || !leaves[to]) return;
            var from = mode;
            if (push) history.pushState({ book: to }, "", paths[to] + location.hash);
            if (reduce) { setRight(to); setLeft(to); settle(to); return; }
            busy = true;
            var wide = plate && getComputedStyle(plate).display !== "none";
            wide ? turnWide(from, to) : turnNarrow(from, to);
        }

        history.replaceState({ book: mode }, "", location.href);
        book.addEventListener("click", function (e) {
            var a = e.target.closest("a[data-turn]");
            if (!a || e.metaKey || e.ctrlKey || e.shiftKey || e.button !== 0) return;
            e.preventDefault();
            turn(a.getAttribute("data-turn"), true);
        });
        // Üst bardaki "Giriş yap" / "Kayıt ol" bağlantıları da sayfayı çevirir
        $$('header a[href$="/login"], header a[href$="/register"]').forEach(function (a) {
            a.addEventListener("click", function (e) {
                if (e.metaKey || e.ctrlKey || e.shiftKey || e.button !== 0) return;
                e.preventDefault();
                turn(/register$/.test(a.getAttribute("href")) ? "register" : "login", true);
            });
        });
        window.addEventListener("popstate", function (e) {
            var to = (e.state && e.state.book) || (/\/register$/.test(location.pathname) ? "register" : "login");
            turn(to, false);
        });
    }

    /* ---------- Hero kaydırma: elma penceresi ve resim farklı hızlarda kayar (baskı kayması hissi) ---------- */
    function heroScroll() {
        var hero = $(".hero"), win = $(".hero-stage .apple-window"), pic = $(".hero-stage .apple-pic");
        if (!hero || !win || reduce) return;
        scroll(animate(pic, { transform: ["translate(0px, 0px)", "translate(-14px, -36px)"] }, { ease: "linear" }),
            { target: hero, offset: ["start start", "end start"] });
        // İmleç elmayı hafifçe kendine doğru eğer (yalnızca fare ile)
        var stage = $("[data-hero-stage]");
        if (!stage || !window.matchMedia("(hover: hover) and (pointer: fine)").matches) return;
        stage.addEventListener("pointermove", function (e) {
            var r = stage.getBoundingClientRect();
            var x = (e.clientX - r.left) / r.width - 0.5, y = (e.clientY - r.top) / r.height - 0.5;
            animate(win, { rotate: x * 5, x: x * 14, y: y * 10 }, { type: "spring", stiffness: 140, damping: 20 });
        });
        stage.addEventListener("pointerleave", function () {
            animate(win, { rotate: 0, x: 0, y: 0 }, { type: "spring", stiffness: 140, damping: 20 });
        });
    }

    /* ---------- Giriş sayfaları: kitap açılır. Sol sayfa sırttan dönerek açılır, form sayfası yükselir ---------- */
    function bookOpen() {
        var book = $("[data-book]");
        if (!book) return;
        var plate = $(".book-plate", book), form = $(".book-form:not([hidden])", book);
        if (reduce) return;
        animate(book, { transform: ["scale(0.97)", "scale(1)"] }, { duration: 0.8, ease: expo });
        if (form) animate(form, { opacity: [0, 1], transform: ["translateY(14px)", "translateY(0px)"] }, { duration: 0.6, ease: out, delay: 0.1 });
        if (plate && getComputedStyle(plate).display !== "none") {
            animate(plate, { opacity: [0, 1], transform: ["perspective(1800px) rotateY(80deg)", "perspective(1800px) rotateY(0deg)"] },
                { duration: 1.1, ease: expo, delay: 0.15 });
        } else if (plate) {
            plate.style.opacity = "1";
        }
    }

    /* ---------- Ortak kaydırma döngüsü: sabitlenmiş sahneler her karede en fazla bir kez güncellenir ---------- */
    var wide = window.matchMedia("(min-width: 1100px)");
    var ticks = [], queued = false;
    function onFrame(fn) { ticks.push(fn); }
    function runTicks() { queued = false; for (var i = 0; i < ticks.length; i++) ticks[i](); }
    function queue() { if (!queued) { queued = true; requestAnimationFrame(runTicks); } }
    window.addEventListener("scroll", queue, { passive: true });
    window.addEventListener("resize", queue);
    var clamp01 = function (v) { return v < 0 ? 0 : v > 1 ? 1 : v; };
    // Sabitlenmiş bir bölümde ilerleme: bölümün tepesi ekranın tepesine gelince 0, sahne bırakılırken 1
    function pinProgress(sec) {
        var r = sec.getBoundingClientRect(), run = r.height - window.innerHeight;
        return run > 0 ? clamp01(-r.top / run) : 0;
    }

    /* ---------- Nasıl çalışır: dağınık elmalar şeritte toplanır ve sahne sıralarına dizilir ----------
       Geniş ekranda bölüm sabitlenir ve kaydırma üç adımı sırayla oynatır; dar ekranda diyagram görünürken oynar. */
    function flow() {
        var fig = $("[data-flow]");
        if (!fig || reduce) return;
        var sec = $("[data-pin-flow]"), pinned = !!sec && wide.matches;
        var steps = $$("[data-flow-steps] > li"), bar = null;
        if (pinned) {
            var list = $("[data-flow-steps]");
            list.removeAttribute("data-stagger"); // adımların görünürlüğünü sabitleme yönetir
            var prog = document.createElement("div"); prog.className = "flow-progress"; prog.setAttribute("aria-hidden", "true");
            bar = document.createElement("span"); prog.appendChild(bar); list.after(prog);
        }
        // Bölgeler sırayla yerleşir: önce dağınık elmalar, sonra şerit, en son sahne sıraları
        var stages = { scatter: [0, 0.3], ribbon: [0.3, 0.62], rows: [0.62, 0.92] };
        $$("svg", fig).forEach(function (svg) {
            var vb = svg.viewBox.baseVal, tall = vb.height > vb.width;
            var midX = vb.width / 2, midY = vb.height / 2;
            var seeds = [], groups = {};
            ["scatter", "ribbon", "rows"].forEach(function (zone) {
                groups[zone] = $('[data-zone="' + zone + '"]', svg);
                $$('[data-zone="' + zone + '"] use', svg).forEach(function (el) {
                    var x = +el.getAttribute("x"), y = +el.getAttribute("y"), dx, dy;
                    if (zone === "scatter") { dx = (Math.random() - 0.5) * 70; dy = (Math.random() - 0.5) * 70; }
                    else if (zone === "ribbon") { // soldaki (telefonda üstteki) dağınık alandan gelir
                        if (tall) { dx = (Math.random() - 0.5) * vb.width * 0.8; dy = -(y - Math.random() * 150); }
                        else { dx = -(x - Math.random() * 360); dy = (Math.random() - 0.5) * 200; }
                    } else { // şeridin ortasından açılır
                        dx = (tall ? midX : 600) - x; dy = (tall ? 265 : midY) - y;
                    }
                    seeds.push({ el: el, zone: zone, dx: dx, dy: dy, r: (Math.random() - 0.5) * 80 });
                });
            });
            function place(s, p) {
                var e = 1 - Math.pow(1 - clamp01(p), 3), k = 1 - e;
                s.el.style.transform = k < 0.001 ? "" : "translate(" + (s.dx * k).toFixed(1) + "px," + (s.dy * k).toFixed(1) + "px) rotate(" + (s.r * k).toFixed(1) + "deg)";
            }
            if (pinned) {
                onFrame(function () {
                    var p = pinProgress(sec);
                    for (var i = 0; i < seeds.length; i++) { var st = stages[seeds[i].zone]; place(seeds[i], (p - st[0]) / (st[1] - st[0])); }
                    Object.keys(groups).forEach(function (z) {
                        var st = stages[z]; groups[z].style.opacity = p < st[0] ? "0.3" : "1";
                    });
                });
            } else {
                seeds.forEach(function (s) { place(s, 0); });
                scroll(function (p) { seeds.forEach(function (s) { place(s, p); }); }, { target: svg, offset: ["start end", "center 55%"] });
            }
        });
        if (pinned) onFrame(function () {
            var p = pinProgress(sec), on = p < 0.3 ? 0 : p < 0.62 ? 1 : 2;
            steps.forEach(function (li, i) { li.classList.toggle("is-on", i === on); });
            bar.style.transform = "scaleX(" + p.toFixed(3) + ")";
        });
        queue();
    }

    /* ---------- Bölüm girişleri: başlık, metin, resim sırayla; bir kez ---------- */
    function reveals() {
        if (reduce) return;
        inView("[data-reveal]:not([data-reveal=mask])", function (el) {
            animate(el, { opacity: [0, 1], transform: ["translateY(24px)", "translateY(0px)"] }, { duration: 0.8, ease: expo });
        }, { margin: "0px 0px -12% 0px" });
        inView('[data-reveal="mask"]', function (el) {
            animate(el, { clipPath: ["inset(0% 0% 100% 0%)", "inset(0% 0% 0% 0%)"] }, { duration: 1.1, ease: inOut });
            var img = $("img", el);
            if (img) animate(img, { transform: ["scale(1.12)", "scale(1)"] }, { duration: 1.5, ease: [0.33, 1, 0.68, 1] });
        }, { margin: "0px 0px -12% 0px" });
        inView("[data-stagger]", function (el) {
            animate(Array.prototype.slice.call(el.children), { opacity: [0, 1], transform: ["translateY(18px)", "translateY(0px)"] },
                { duration: 0.6, ease: out, delay: stagger(0.06) });
        }, { margin: "0px 0px -10% 0px" });
    }

    /* ---------- Kapanış: gökten üç elma düşer ---------- */
    function closing() {
        var mark = $(".closing-mark");
        if (!mark || reduce) return;
        inView(mark, function () {
            $$(".apple", mark).forEach(function (apple, i) {
                animate(apple, { opacity: [0, 1] }, { duration: 0.2, delay: 0.15 * i });
                animate(apple, { transform: ["translateY(-120%) rotate(-20deg)", "translateY(0%) rotate(0deg)"] },
                    { type: "spring", stiffness: 160, damping: 11, mass: 1, delay: 0.15 * i });
            });
        }, { amount: 0.4 });
        // Elmanın üzerine gelince hafifçe sallanır
        hover(".closing-mark .apple", function (el) {
            animate(el, { rotate: [0, -8, 6, -3, 0] }, { duration: 0.7, ease: "easeInOut" });
        });
    }

    /* ---------- Canlı öğe: genişleyen levhalar, otomatik ilerleyen masal listesi ve takma ad ---------- */
    function sample() {
        var box = $("[data-sample]"), data = $("#tales");
        if (!box || !data) return;
        var tales;
        try { tales = JSON.parse(data.textContent); } catch (e) { return; }
        var items = $$("[data-tale]", box), panels = $$("[data-panel]", box), input = $("#child-name");
        var text = $("[data-tale-text]", box), count = $("[data-tale-count]", box), next = $("[data-next]", box);
        var tale = 0, scene = 0, AUTO_MS = 7000, timer = null, auto = !reduce, inViewNow = false;
        box.style.setProperty("--auto-ms", AUTO_MS + "ms");

        function childName() { var v = (input.value || "").trim().slice(0, 16); return v || "Ece"; }
        function renderText() {
            var parts = tales[tale].scenes[scene].split("{ad}");
            text.textContent = "";
            parts.forEach(function (part, i) {
                if (i > 0) { var s = document.createElement("span"); s.className = "name"; s.textContent = childName(); text.appendChild(s); }
                text.appendChild(document.createTextNode(part));
            });
            count.textContent = "Sahne " + (scene + 1) + " / " + tales[tale].scenes.length;
        }
        function swapIn(el) { if (!reduce) animate(el, { opacity: [0, 1], transform: ["translateY(10px)", "translateY(0px)"] }, { duration: 0.45, ease: out }); }
        function show(n) {
            tale = n; scene = 0;
            items.forEach(function (b, i) {
                var on = i === n;
                b.classList.toggle("is-active", on); b.classList.remove("is-running");
                b.setAttribute("aria-selected", on ? "true" : "false"); b.tabIndex = on ? 0 : -1;
            });
            panels.forEach(function (p, i) { p.classList.toggle("is-active", i === n); });
            renderText(); swapIn(text);
            schedule();
        }
        // Otomatik ilerleme: ilerleme çizgisi dolunca sıradaki masala geçer
        function schedule() {
            clearTimeout(timer);
            if (!auto || !inViewNow) return;
            var cur = items[tale];
            void cur.offsetWidth; // çizgi animasyonunu baştan başlat
            cur.classList.add("is-running");
            timer = setTimeout(function () { if (!box.classList.contains("is-paused")) show((tale + 1) % items.length); else schedule(); }, AUTO_MS);
        }
        function takeControl() { auto = false; clearTimeout(timer); items.forEach(function (b) { b.classList.remove("is-running"); }); }
        function pause(on) {
            box.classList.toggle("is-paused", on);
            if (!on && auto) schedule();
            else clearTimeout(timer);
        }

        items.forEach(function (b, i) {
            b.addEventListener("click", function () { takeControl(); show(i); });
            b.addEventListener("keydown", function (e) {
                var d = e.key === "ArrowDown" || e.key === "ArrowRight" ? 1 : e.key === "ArrowUp" || e.key === "ArrowLeft" ? -1 : 0;
                if (!d) return;
                e.preventDefault(); takeControl();
                var j = (i + d + items.length) % items.length; items[j].focus(); show(j);
            });
        });
        panels.forEach(function (p, i) { p.addEventListener("click", function () { if (i !== tale) { takeControl(); show(i); } }); });
        box.addEventListener("pointerenter", function () { pause(true); });
        box.addEventListener("pointerleave", function () { pause(false); });
        box.addEventListener("focusin", function () { pause(true); });
        box.addEventListener("focusout", function (e) { if (!box.contains(e.relatedTarget)) pause(false); });
        input.addEventListener("input", renderText);
        next.addEventListener("click", function () {
            takeControl();
            scene = (scene + 1) % tales[tale].scenes.length;
            renderText(); swapIn(text);
        });
        inView(box, function () { inViewNow = true; schedule(); return function () { inViewNow = false; clearTimeout(timer); }; }, { amount: 0.3 });
        renderText();
    }

    /* ---------- Kahramanlar: geniş ekranda sabitlenir ve aşağı kaydırma şeridi yatay kaydırır;
       dar ekranda sürüklenebilir şerit. Ok düğmeleri ve şerit/ızgara görünümü her iki durumda çalışır. ---------- */
    function strip() {
        var wrap = $("[data-strip]");
        if (!wrap) return;
        var row = $("[data-cards]", wrap), sec = $("[data-pin-h]"), extra = 0;
        var step = function () { var c = row.firstElementChild; return c ? c.getBoundingClientRect().width + 16 : 300; };
        var behavior = reduce ? "auto" : "smooth";
        function pinOn() { return !!sec && wide.matches && !reduce && !wrap.classList.contains("is-grid"); }
        function layout() {
            if (pinOn()) {
                sec.classList.add("is-pinned");
                row.style.transform = "";
                extra = Math.max(0, row.scrollWidth - row.clientWidth);
                sec.style.height = (window.innerHeight + extra) + "px";
            } else if (sec) {
                sec.classList.remove("is-pinned");
                sec.style.height = ""; row.style.transform = ""; extra = 0;
            }
            queue();
        }
        onFrame(function () {
            if (!sec || !sec.classList.contains("is-pinned")) return;
            row.style.transform = "translateX(" + (-pinProgress(sec) * extra).toFixed(1) + "px)";
        });
        var t = null;
        window.addEventListener("resize", function () { clearTimeout(t); t = setTimeout(layout, 150); });
        wide.addEventListener("change", layout);
        // Oklar: sabitliyken sayfayı, değilken şeridi kaydırır
        function move(dir) {
            var d = step() * 2 * dir;
            if (sec && sec.classList.contains("is-pinned")) window.scrollBy({ top: d, behavior: behavior });
            else row.scrollBy({ left: d, behavior: behavior });
        }
        $("[data-strip-prev]", wrap).addEventListener("click", function () { move(-1); });
        $("[data-strip-next]", wrap).addEventListener("click", function () { move(1); });
        // Fareyle sürükleme (yalnızca sabitlenmemişken; dokunmatikte tarayıcının kendi kaydırması kullanılır)
        var down = false, startX = 0, startLeft = 0, moved = false;
        row.addEventListener("pointerdown", function (e) {
            if (e.pointerType !== "mouse" || wrap.classList.contains("is-grid") || (sec && sec.classList.contains("is-pinned"))) return;
            down = true; moved = false; startX = e.clientX; startLeft = row.scrollLeft;
        });
        window.addEventListener("pointermove", function (e) {
            if (!down) return;
            var dx = e.clientX - startX;
            if (Math.abs(dx) > 4 && !moved) { moved = true; row.classList.add("is-dragging"); }
            if (moved) row.scrollLeft = startLeft - dx;
        });
        window.addEventListener("pointerup", function () { if (down) { down = false; row.classList.remove("is-dragging"); } });
        // Görünüm düğmeleri: şerit veya ızgara (ızgarada sabitleme kapanır)
        $$("[data-view]").forEach(function (btn) {
            btn.addEventListener("click", function () {
                var grid = btn.getAttribute("data-view") === "grid";
                $$("[data-view]").forEach(function (b) { b.setAttribute("aria-pressed", b === btn ? "true" : "false"); });
                wrap.classList.toggle("is-grid", grid);
                layout();
                if (!reduce) animate(Array.prototype.slice.call(row.children), { opacity: [0, 1], transform: ["translateY(16px)", "translateY(0px)"] },
                    { duration: 0.45, ease: out, delay: stagger(0.03) });
            });
        });
        // Resimler yüklenince şerit genişliği değişebilir
        $$("img", row).forEach(function (img) { if (!img.complete) img.addEventListener("load", layout, { once: true }); });
        layout();
    }

    /* ---------- Mekânlar: geniş ekranda kartlar yığılırken alttaki kart hafifçe küçülür ---------- */
    function places() {
        var cards = $$(".place");
        if (cards.length < 2 || reduce || !wide.matches) return;
        onFrame(function () {
            for (var i = 0; i < cards.length - 1; i++) {
                var top = parseFloat(getComputedStyle(cards[i]).top) || 0;
                var next = cards[i + 1].getBoundingClientRect().top;
                var q = clamp01(1 - (next - top) / (window.innerHeight * 0.7));
                cards[i].style.transform = q > 0 ? "scale(" + (1 - 0.06 * q).toFixed(4) + ")" : "";
            }
        });
        queue();
    }

    /* ---------- Tema seçici: üzerine gelince önizler, tıklayınca seçer; panel yumuşakça yenilenir ---------- */
    function themes() {
        var box = $("[data-themes]");
        if (!box) return;
        var btns = $$(".theme-btn", box), name = $("[data-theme-name]", box), desc = $("[data-theme-desc]", box), sample = $("[data-theme-sample]", box);
        var apple = $(".theme-apple", box), selected = 0, shown = 0;
        function render(i) {
            if (i === shown) return;
            shown = i;
            var b = btns[i];
            name.textContent = b.textContent.trim(); desc.textContent = b.getAttribute("data-desc"); sample.textContent = b.getAttribute("data-sample");
            if (reduce) return;
            animate(name, { opacity: [0, 1], transform: ["translateY(18px)", "translateY(0px)"] }, { duration: 0.5, ease: out });
            animate([desc, sample.parentElement], { opacity: [0, 1], transform: ["translateY(12px)", "translateY(0px)"] }, { duration: 0.5, ease: out, delay: stagger(0.06, { startDelay: 0.06 }) });
            if (apple) animate(apple, { rotate: [0, -10, 7, -3, 0] }, { duration: 0.7, ease: "easeInOut" });
        }
        function select(i, focus) {
            selected = i;
            btns.forEach(function (b, j) {
                var on = j === i;
                b.classList.toggle("is-active", on); b.setAttribute("aria-selected", on ? "true" : "false"); b.tabIndex = on ? 0 : -1;
            });
            if (focus) btns[i].focus();
            render(i);
        }
        btns.forEach(function (b, i) {
            b.addEventListener("click", function () { select(i); });
            b.addEventListener("keydown", function (e) {
                var k = e.key, j = null;
                if (k === "ArrowDown" || k === "ArrowRight") j = (i + 1) % btns.length;
                else if (k === "ArrowUp" || k === "ArrowLeft") j = (i - 1 + btns.length) % btns.length;
                else if (k === "Home") j = 0; else if (k === "End") j = btns.length - 1;
                if (j === null) return;
                e.preventDefault(); select(j, true);
            });
            // Fareyle önizleme (dokunmatikte yalnızca dokunma seçer)
            b.addEventListener("pointerenter", function (e) { if (e.pointerType === "mouse") render(i); });
        });
        $(".theme-list", box).addEventListener("pointerleave", function (e) { if (e.pointerType === "mouse") render(selected); });
    }

    /* ---------- Bölüm takibi: görünen bölüme göre sağ menüdeki aktif öğe ve başlıktaki ışık şeridi ---------- */
    function chapters() {
        var sections = $$("[data-chapter]");
        if (!sections.length) return;
        var links = $$("[data-chapter-link]");
        var io = new IntersectionObserver(function (entries) {
            entries.forEach(function (e) {
                if (!e.isIntersecting) return;
                var id = e.target.id;
                sections.forEach(function (s) { s.classList.toggle("is-current", s === e.target); });
                links.forEach(function (a) {
                    var on = a.getAttribute("data-chapter-link") === id;
                    a.classList.toggle("is-active", on);
                    if (on) a.setAttribute("aria-current", "true"); else a.removeAttribute("aria-current");
                });
            });
        }, { rootMargin: "-45% 0px -50% 0px" });
        sections.forEach(function (s) { io.observe(s); });
    }

    /* ---------- Masal okuyucu: sahneler kitap sayfası gibi sırayla gelir ---------- */
    function storyScenes() {
        var scenes = $$(".scene");
        if (!scenes.length || reduce) return;
        scenes.forEach(function (s) { s.style.opacity = "0"; });
        inView(".scene", function (el) {
            animate(el, { opacity: [0, 1] }, { duration: 0.5 });
            var art = $(".scene-art", el), txt = $(".scene-text", el);
            if (art) animate(art, { clipPath: ["inset(0% 0% 100% 0%)", "inset(0% 0% 0% 0%)"] }, { duration: 1, ease: inOut });
            var a = art && $(".apple", art);
            if (a) animate(a, { transform: ["translateY(-40%) rotate(-18deg)", "translateY(0%) rotate(0deg)"] }, { type: "spring", stiffness: 150, damping: 12, delay: 0.25 });
            if (txt) animate(txt, { opacity: [0, 1], transform: ["translateY(20px)", "translateY(0px)"] }, { duration: 0.8, ease: expo, delay: 0.2 });
        }, { margin: "0px 0px -15% 0px" });
    }

    /* ---------- Marka elması: üzerine gelince sallanır ---------- */
    function brand() {
        if (reduce) return;
        hover(".story-end .apple, .empty .apple", function (el) {
            animate(el, { rotate: [0, -10, 8, -4, 0] }, { duration: 0.7, ease: "easeInOut" });
        });
    }

    function init() {
        heroLoad(); bookOpen(); bookTurn(); crossfade(); heroScroll(); flow(); reveals(); closing(); sample(); strip(); places(); themes(); chapters(); storyScenes(); brand();
    }
    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init); else init();
})();
