/*
 * Masal okuyucu. Betik yoksa sahneler alt alta, her biri kendi oynatıcısıyla okunur (sayfa bu hâliyle eksiksizdir).
 * Betik çalışınca:
 *  - Sahneler sayfa sayfa gösterilir; önceki/sonraki düğmeleri, sahne noktaları ve klavye okları (← →) ile gezilir.
 *  - Tek bir ses öğesi sahnenin sesini çalar; "Sahneleri kendiliğinden çevir" açıksa sahne bitince sonrakine geçilir.
 *  - Ses veya resim hazırlanıyorsa durum 4 saniyede bir yoklanır; hazır olanlar sayfa yenilenmeden açılır.
 *  - "Hareketi azalt" açıksa sayfa geçişi canlandırılmaz.
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
        if (direction && !reduce && window.Motion) {
            window.Motion.animate(scenes[current], { opacity: [0, 1], transform: ["translateX(" + (direction * 28) + "px)", "translateX(0px)"] },
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
        player.play().catch(function () { playing = false; render(); });
    }

    function go(i, keepPlaying) {
        if (i < 0) return;
        if (i >= scenes.length) {           // son sahneden sonra masalın sonu
            stop();
            finished = true;
            render();
            if (end) end.scrollIntoView({ behavior: reduce ? "auto" : "smooth", block: "center" });
            return;
        }
        var direction = i > current ? 1 : (i < current ? -1 : 0);
        finished = false;
        current = i;
        player.pause();
        player.removeAttribute("data-scene");
        playing = false;
        render(direction);
        if (keepPlaying) playCurrent();
    }

    player.addEventListener("ended", function () {
        playing = false;
        if (autoBox.checked) {
            // Sayfa çevrilmeden önce kısa bir nefes; sonraki sahnenin sesi yoksa okuma orada durur.
            setTimeout(function () { go(current + 1, current + 1 < scenes.length && hasAudio(current + 1)); }, 700);
        } else {
            render();
        }
    });
    // Sahne değişirken gelen eski "pause" olayı yeni çalmayı durdurmuş saymasın: yalnızca gerçekten duruyorsa.
    player.addEventListener("pause", function () { if (!player.ended && player.paused && playing) { playing = false; render(); } });
    player.addEventListener("play", function () { playing = true; render(); });

    prevBtn.addEventListener("click", function () { go(current - 1, playing); });
    nextBtn.addEventListener("click", function () { go(current + 1, playing); });
    playBtn.addEventListener("click", function () { playing ? stop() : playCurrent(); render(); });
    if (restart) restart.addEventListener("click", function () {
        go(0, false);
        reader.scrollIntoView({ behavior: reduce ? "auto" : "smooth", block: "start" });
    });
    if (listen) {
        listen.hidden = false;
        listen.addEventListener("click", function () {
            go(0, true);
            reader.scrollIntoView({ behavior: reduce ? "auto" : "smooth", block: "start" });
        });
    }
    document.addEventListener("keydown", function (e) {
        if (e.target.closest("input, textarea, select") || e.metaKey || e.ctrlKey || e.altKey) return;
        if (e.key === "ArrowRight") { go(current + 1, playing); e.preventDefault(); }
        if (e.key === "ArrowLeft") { go(current - 1, playing); e.preventDefault(); }
    });

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
                if (note && a.status === "PREPARING") note.textContent = a.ready + " / " + a.total + " sahne hazır. Masalı okumaya başlayabilirsiniz; sesler hazır oldukça açılır.";
                else if (note && a.status === "READY") { note.textContent = "Tüm sahnelerin sesi hazır."; if (progress) progress.parentNode.hidden = true; }
                else if (note && a.status !== "NONE") note.textContent = "Bazı sahnelerin sesi hazırlanamadı. Sayfayı yenileyip sesi yeniden isteyebilirsiniz.";
                if (imageNote) {
                    if (im.status === "PREPARING") imageNote.textContent = "Resimler çiziliyor: " + im.ready + " / " + im.total + " sahne hazır.";
                    else if (im.status === "READY") imageNote.parentNode.hidden = true;
                    else imageNote.textContent = "Bazı sahnelerin resmi çizilemedi. Sayfayı yenileyip yeniden isteyebilirsiniz.";
                }
                render();
                // Okuma kesilmesin diye sayfa yenilenmez; ikisi de bitince yoklama durur.
                if (a.status === "PREPARING" || im.status === "PREPARING") setTimeout(poll, 4000);
            })
            .catch(function () { setTimeout(poll, 8000); });
    }
    setTimeout(poll, 4000);
})();
