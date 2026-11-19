/*
 * Masal okuyucu. Betik yoksa sahneler alt alta, her biri kendi oynatıcısıyla okunur (sayfa bu hâliyle eksiksizdir).
 * Betik çalışınca:
 *  - Sahneler sayfa sayfa gösterilir; önceki/sonraki düğmeleri, sahne noktaları ve klavye okları (← →) ile gezilir.
 *  - Tek bir ses öğesi sahnenin sesini çalar; "Sahneleri kendiliğinden çevir" açıksa sahne bitince sonrakine geçilir.
 *  - Ses veya resim hazırlanıyorsa durum 4 saniyede bir yoklanır; hazır olanlar sayfa yenilenmeden açılır.
 *  - "Hareketi azalt" açıksa sayfa geçişi canlandırılmaz.
 *  - Tam ekran okuyucuda (story-stage) sahne metni kelimelere bölünür; ses çalarken okunan kelimeler renklenir
 *    (kelime zamanlaması olmadığı için ilerleme kelime uzunluğu ve noktalamaya göre tahmin edilir). Metin paneli
 *    taşırırsa yazı boyutu kademeli küçülür. Sayfa kaymadığı için sayfa kaydırma çağrıları yoktur.
 */
(function () {
    "use strict";
    var reader = document.querySelector("[data-reader]");
    if (!reader) return;

    var scenes = Array.prototype.slice.call(reader.querySelectorAll("[data-scene]"));
    if (!scenes.length) return;
    var bar = reader.querySelector("[data-reader-bar]");
    var prevBtn = bar.querySelector("[data-prev]"), nextBtn = bar.querySelector("[data-next]");
    var playBtn = bar.querySelector("[data-play]"), autoBox = bar.querySelector("[data-auto]");
    var dotsBox = bar.querySelector("[data-dots]");
    var end = document.querySelector("[data-story-end]");
    var restart = end && end.querySelector("[data-restart]");
    var listen = document.querySelector("[data-listen]");
    var panel = document.querySelector("[data-status-url]");
    var reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

    // Sahne -> ses adresi. Sayfadaki oynatıcılar betik açıkken gizlenir; çalma tek ses öğesinden yapılır.
    var urls = {};
    scenes.forEach(function (li) {
        var a = li.querySelector("audio");
        if (a) { urls[li.getAttribute("data-scene")] = a.getAttribute("src"); a.remove(); }
    });
    var player = new Audio();
    player.preload = "auto";

    var current = 0, playing = false, finished = false;
    reader.classList.add("is-paged");
    bar.hidden = false;
    if (restart) restart.hidden = false;

    // Sahne noktaları
    var dots = scenes.map(function (li, i) {
        var item = document.createElement("li");
        var b = document.createElement("button");
        b.type = "button";
        b.className = "reader-dot";
        b.setAttribute("aria-label", "Sahne " + (i + 1));
        b.addEventListener("click", function () { go(i, playing); });
        item.appendChild(b);
        dotsBox.appendChild(item);
        return b;
    });

    function sceneNo(i) { return scenes[i].getAttribute("data-scene"); }

    // ---- Okunan kelimeleri renklendirme ----
    function words(i) {
        var p = scenes[i].querySelector("[data-scene-text]");
        if (!p) return null;
        if (!p._words) {
            var text = p.textContent, list = [], total = 0;
            p.textContent = "";
            text.split(/(\s+)/).forEach(function (part) {
                if (!part) return;
                if (/^\s+$/.test(part)) { p.appendChild(document.createTextNode(part)); return; }
                var span = document.createElement("span");
                span.className = "w";
                span.textContent = part;
                p.appendChild(span);
                // Ağırlık: harf sayısı + noktalamadan sonraki kısa duraklama
                var weight = part.length + 1 + (/[.!?…]["”']?$/.test(part) ? 6 : /[,;:]$/.test(part) ? 3 : 0);
                list.push({ el: span, start: total, end: total + weight });
                total += weight;
            });
            p._words = list; p._total = total || 1;
        }
        return p;
    }
    function highlight(i, frac) {
        var p = words(i);
        if (!p) return;
        var at = frac * p._total;
        p._words.forEach(function (w) {
            w.el.classList.toggle("is-read", frac >= 1 || w.end <= at);
            w.el.classList.toggle("is-now", frac > 0 && frac < 1 && w.start <= at && w.end > at);
        });
    }
    function clearHighlight(i) { highlight(i, 0); }
    var raf = null;
    function follow() {
        cancelAnimationFrame(raf);
        if (!playing) return;
        var d = player.duration || parseFloat(scenes[current].getAttribute("data-duration")) || 0;
        if (d > 0) highlight(current, Math.min(1, player.currentTime / d));
        raf = requestAnimationFrame(follow);
    }

    // ---- Metni panele sığdırma: taşarsa yazı kademeli küçülür (en az 17px; yine taşarsa panel içinde kayar) ----
    function fit(i) {
        var body = scenes[i].querySelector(".scene-body"), p = scenes[i].querySelector("[data-scene-text]");
        if (!body || !p || !reader.classList.contains("story-stage")) return;
        p.style.removeProperty("--reader-fs");
        var size = parseFloat(getComputedStyle(p).fontSize);
        while (body.scrollHeight > body.clientHeight + 1 && size > 17) {
            size -= 1;
            p.style.setProperty("--reader-fs", size + "px");
        }
    }
    var fitTimer = null;
    window.addEventListener("resize", function () { clearTimeout(fitTimer); fitTimer = setTimeout(function () { fit(current); }, 120); });
    function hasAudio(i) { return !!urls[sceneNo(i)]; }

    function render(direction) {
        scenes.forEach(function (li, i) {
            var on = i === current;
            li.hidden = !on;
            li.classList.toggle("is-current", on);
        });
        dots.forEach(function (d, i) {
            d.classList.toggle("is-current", i === current);
            d.classList.toggle("has-audio", hasAudio(i));
            if (i === current) d.setAttribute("aria-current", "step"); else d.removeAttribute("aria-current");
        });
        prevBtn.disabled = current === 0;
        nextBtn.disabled = current === scenes.length - 1 && finished;
        nextBtn.setAttribute("aria-label", current === scenes.length - 1 ? "Masalın sonu" : "Sonraki sahne");
        playBtn.disabled = !hasAudio(current);
        playBtn.setAttribute("aria-label", playing ? "Duraklat" : (hasAudio(current) ? "Bu sahneyi dinle" : "Bu sahnenin sesi henüz hazır değil"));
        reader.classList.toggle("is-playing", playing);
        if (end) end.classList.toggle("is-shown", finished);
        fit(current);
        if (direction && !reduce && window.Motion) {
            var parts = Array.prototype.slice.call(scenes[current].querySelectorAll(".scene-art, .scene-body"));
            window.Motion.animate(parts, { opacity: [0, 1], transform: ["translateX(" + (direction * 28) + "px)", "translateX(0px)"] },
                { duration: 0.42, ease: [0.22, 1, 0.36, 1] });
        }
    }

    function stop() {
        player.pause();
        playing = false;
    }

    function playCurrent() {
        var url = urls[sceneNo(current)];
        if (!url) { stop(); render(); return; }
        if (player.getAttribute("data-scene") !== sceneNo(current)) {
            player.src = url;
            player.setAttribute("data-scene", sceneNo(current));
        }
        playing = true;
        render();
        player.play().then(follow).catch(function () { playing = false; render(); });
    }

    function go(i, keepPlaying) {
        if (i < 0) return;
        if (i >= scenes.length) {           // son sahneden sonra masalın sonu
            stop();
            finished = true;
            render();
            return;
        }
        var direction = i > current ? 1 : (i < current ? -1 : 0);
        finished = false;
        clearHighlight(current);
        current = i;
        clearHighlight(current);
        player.pause();
        player.removeAttribute("data-scene");
        playing = false;
        render(direction);
        if (keepPlaying) playCurrent();
    }

    player.addEventListener("ended", function () {
        playing = false;
        highlight(current, 1);
        if (autoBox.checked) {
            // Sayfa çevrilmeden önce kısa bir nefes; sonraki sahnenin sesi yoksa okuma orada durur.
            setTimeout(function () { go(current + 1, current + 1 < scenes.length && hasAudio(current + 1)); }, 700);
        } else {
            render();
        }
    });
    // Sahne değişirken gelen eski "pause" olayı yeni çalmayı durdurmuş saymasın: yalnızca gerçekten duruyorsa.
    player.addEventListener("pause", function () { if (!player.ended && player.paused && playing) { playing = false; render(); } });
    player.addEventListener("play", function () { playing = true; render(); follow(); });
    player.addEventListener("seeked", follow);

    prevBtn.addEventListener("click", function () { go(current - 1, playing); });
    nextBtn.addEventListener("click", function () { go(current + 1, playing); });
    playBtn.addEventListener("click", function () { playing ? stop() : playCurrent(); render(); });
    if (restart) restart.addEventListener("click", function () { go(0, false); });
    if (listen) {
        listen.hidden = false;
        listen.addEventListener("click", function () { go(0, true); });
    }
    document.addEventListener("keydown", function (e) {
        if (e.target.closest("input, textarea, select") || e.metaKey || e.ctrlKey || e.altKey) return;
        if (e.key === "ArrowRight") { go(current + 1, playing); e.preventDefault(); }
        if (e.key === "ArrowLeft") { go(current - 1, playing); e.preventDefault(); }
    });

    // "Kaydedildi" simgesi: dokununca açıklama açılır/kapanır; dışarı tıklayınca ya da Escape ile kapanır
    var info = document.querySelector("[data-info]");
    if (info) {
        info.addEventListener("click", function (e) { e.stopPropagation(); info.setAttribute("aria-expanded", info.getAttribute("aria-expanded") === "true" ? "false" : "true"); });
        document.addEventListener("click", function () { info.setAttribute("aria-expanded", "false"); });
        document.addEventListener("keydown", function (e) { if (e.key === "Escape") info.setAttribute("aria-expanded", "false"); });
    }

    // Adres #sahne-3 ise o sahneden başlanır
    var m = /^#sahne-(\d+)$/.exec(location.hash);
    if (m) current = Math.min(scenes.length - 1, Math.max(0, parseInt(m[1], 10) - 1));
    render();

    // ---- Ses veya resim hazırlanırken durum yoklaması ----
    if (!panel || panel.getAttribute("data-media-preparing") !== "true") return;
    var note = panel.querySelector("[data-audio-note]"), progress = panel.querySelector("[data-audio-bar]");
    var imageNote = panel.querySelector("[data-image-note] span");
    var statusUrl = panel.getAttribute("data-status-url"), tries = 0;

    function showImage(i, url) {
        var art = scenes[i] && scenes[i].querySelector(".scene-art");
        if (!art || art.classList.contains("has-art")) return;
        var img = new Image();
        img.alt = ""; img.width = 1024; img.height = 768; img.decoding = "async";
        img.onload = function () {
            art.textContent = "";
            art.appendChild(img);
            art.classList.add("has-art");
            if (!reduce && window.Motion) window.Motion.animate(img, { opacity: [0, 1] }, { duration: 0.6 });
        };
        img.src = url;
    }

    function poll() {
        if (++tries > 450) return;          // en fazla yaklaşık 30 dakika
        fetch(statusUrl, { headers: { Accept: "application/json" }, credentials: "same-origin" })
            .then(function (r) { return r.ok ? r.json() : null; })
            .then(function (s) {
                if (!s) return setTimeout(poll, 8000);
                s.scenes.forEach(function (sc, i) {
                    if (sc.audio) urls[String(sc.sceneNumber)] = sc.audio;
                    if (sc.image) showImage(i, sc.image);
                });
                var a = s.audio, im = s.images;
                if (progress) progress.style.width = (a.total ? Math.round(a.ready * 100 / a.total) : 0) + "%";
                var noteSpan = note && note.querySelector("[data-audio-text]");
                var noteText = function (t) { if (noteSpan) noteSpan.textContent = t; else if (note) note.textContent = t; };
                if (a.status === "PREPARING") noteText("Masal seslendiriliyor: " + a.ready + " / " + a.total + " sahne hazır");
                else if (a.status === "READY") { noteText("Sesli masal hazır"); if (progress) progress.parentNode.hidden = true; }
                else if (a.status !== "NONE") noteText("Bazı sahnelerin sesi hazırlanamadı");
                if (imageNote) {
                    if (im.status === "PREPARING") imageNote.textContent = "Resimler çiziliyor: " + im.ready + " / " + im.total + " sahne hazır.";
                    else if (im.status === "READY") imageNote.parentNode.hidden = true;
                    else imageNote.textContent = "Bazı resimler çizilemedi";
                }
                render();
                // Okuma kesilmesin diye sayfa yenilenmez; ikisi de bitince yoklama durur.
                if (a.status === "PREPARING" || im.status === "PREPARING") setTimeout(poll, 4000);
            })
            .catch(function () { setTimeout(poll, 8000); });
    }
    setTimeout(poll, 4000);
})();
