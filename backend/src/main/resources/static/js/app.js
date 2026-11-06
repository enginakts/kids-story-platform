// Masal Dünyası — uygulama etkileşimleri. Script olmadan her sayfa çalışır; bu dosya yalnızca deneyimi zenginleştirir.
(() => {
    const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;

    // Gezinme: bulunduğumuz sayfanın bağlantısı işaretlenir.
    const here = location.pathname.replace(/\/$/, '') || '/';
    document.querySelectorAll('.bar nav a:not(.button)').forEach((a) => {
        if (a.hash) return;                              // sayfa içi bağlantılar sayfa değildir
        const path = new URL(a.href).pathname.replace(/\/$/, '') || '/';
        if (path === here || (path !== '/app' && here.startsWith(path))) a.setAttribute('aria-current', 'page');
    });

    // Üretim sürerken sayfa kendini yeniler; ilerleme çubuğu kaldığı yerden devam eder.
    const waiting = document.querySelector('[data-reload]');
    if (waiting) setTimeout(() => location.reload(), Number(waiting.dataset.reload));

    wizard();
    reader();

    /* ---------- Masal sihirbazı: canlı özet ---------- */
    function wizard() {
        const form = document.querySelector('form.wizard-form');
        if (!form) return;
        const summary = document.querySelector('.summary');
        const plate = summary.querySelector('.plate');
        const line = summary.querySelector('.story-line');
        const submit = summary.querySelector('button[type="submit"]');
        const value = (name) => form.querySelector(`[name="${name}"]:checked`);
        const labelOf = (input) => input ? input.closest('label').querySelector('strong, span').textContent.trim() : null;
        let lastHero = null;

        const sides = () => [...form.querySelectorAll('[name="sideCharacterIds"]')];
        const limitSides = () => {
            const hero = value('characterId');
            const checked = sides().filter((i) => i.checked);
            sides().forEach((input) => {
                const isHero = hero && input.value === hero.value;
                if (isHero) input.checked = false;
                const off = isHero || (!input.checked && checked.length >= 2);
                input.disabled = off;
                input.closest('label').classList.toggle('is-disabled', off);
            });
        };

        const render = () => {
            limitSides();
            const hero = value('characterId');
            const place = value('locationId');
            const theme = value('theme');
            const situation = value('situation');
            const emotion = value('emotion');
            const scenes = value('sceneCount');
            const friends = sides().filter((i) => i.checked).map(labelOf);
            const set = (key, text) => {
                const row = summary.querySelector(`[data-sum="${key}"]`);
                row.querySelector('span:last-child').textContent = text || 'Seçilmedi';
                row.classList.toggle('is-missing', !text);
            };
            set('hero', labelOf(hero));
            set('friends', friends.length ? friends.join(', ') : '—');
            set('place', labelOf(place));
            set('theme', labelOf(theme));
            set('situation', situation && situation.value ? labelOf(situation) : 'Fark etmez');
            set('emotion', emotion && emotion.value ? labelOf(emotion) : 'Fark etmez');
            set('scenes', scenes ? labelOf(scenes) : null);
            summary.querySelector('[data-sum="friends"]').classList.remove('is-missing');

            const heroName = labelOf(hero);
            const text = heroName
                ? `${heroName}${friends.length ? ' ve ' + friends.join(', ') : ''}, ${labelOf(place) || 'bir yerde'} güne neşeyle uyanmış…`
                : 'Bir kahraman seçin, masalın ilk cümlesi burada belirsin.';
            const changed = hero !== lastHero;
            lastHero = hero;
            const apply = () => {
                line.textContent = text;
                const art = hero && hero.closest('label').querySelector('svg.art');
                plate.replaceChildren(art ? art.cloneNode(true)
                    : Object.assign(document.createElement('span'), { textContent: 'Kahraman seçilmedi' }));
                summary.classList.remove('is-changing');
            };
            if (changed && !reduce) { summary.classList.add('is-changing'); setTimeout(apply, 180); } else apply();

            const ready = hero && place && theme;
            submit.setAttribute('aria-disabled', ready ? 'false' : 'true');
        };

        form.addEventListener('change', (e) => {
            if (e.target.name === 'childId') {           // çocuğa özel karakterler değişebilir
                location.href = `${location.pathname}?childId=${encodeURIComponent(e.target.value)}`;
                return;
            }
            render();
        });
        form.addEventListener('submit', (e) => {
            if (submit.getAttribute('aria-disabled') === 'true') {
                e.preventDefault();
                const missing = form.querySelector('fieldset[data-required]:not(:has(input:checked))');
                if (missing) missing.scrollIntoView({ behavior: reduce ? 'auto' : 'smooth', block: 'center' });
                return;
            }
            submit.textContent = 'Masal hazırlanıyor…';
            submit.setAttribute('aria-disabled', 'true');
        });

        // Bana sürpriz yap: zorunlu seçimleri rastgele işaretler.
        const surprise = summary.querySelector('[data-surprise]');
        surprise?.addEventListener('click', () => {
            for (const name of ['characterId', 'locationId', 'theme', 'situation', 'emotion']) {
                const options = [...form.querySelectorAll(`[name="${name}"]`)].filter((i) => i.value);
                if (options.length) options[Math.floor(Math.random() * options.length)].checked = true;
            }
            render();
        });
        render();
    }

    /* ---------- Okuyucu: sayfa çevirme, sesli okuma, kelime vurgusu ---------- */
    function reader() {
        const root = document.querySelector('.reader');
        if (!root) return;
        const leaves = [...root.querySelectorAll('.leaf')];
        leaves.forEach(karaoke);
        if (leaves.length < 2) return;

        root.classList.add('is-paged');
        const dots = root.querySelector('.pager .dots');
        const prev = root.querySelector('[data-prev]');
        const next = root.querySelector('[data-next]');
        const count = root.querySelector('[data-count]');
        let current = 0;
        let autoplay = false;

        leaves.forEach((_, i) => {
            const b = document.createElement('button');
            b.type = 'button';
            b.setAttribute('aria-label', `Sahne ${i + 1}`);
            b.addEventListener('click', () => go(i));
            dots.append(b);
        });

        function go(i, fromAuto = false) {
            if (i < 0 || i >= leaves.length) return;
            const dir = i > current ? 'from-right' : 'from-left';
            leaves[current].querySelector('audio')?.pause();
            leaves.forEach((leaf, k) => leaf.classList.remove('is-current', 'from-right', 'from-left'));
            void leaves[i].offsetWidth;                  // animasyonu yeniden başlat
            leaves[i].classList.add('is-current', dir);
            current = i;
            [...dots.children].forEach((d, k) => d.setAttribute('aria-current', String(k === i)));
            prev.disabled = i === 0;
            next.disabled = i === leaves.length - 1;
            count.textContent = `Sahne ${i + 1} / ${leaves.length}`;
            if (!fromAuto) autoplay = false;
            if (autoplay) leaves[i].querySelector('audio')?.play().catch(() => {});
        }
        prev.addEventListener('click', () => go(current - 1));
        next.addEventListener('click', () => go(current + 1));
        document.addEventListener('keydown', (e) => {
            if (e.target.closest('input, select, textarea')) return;
            if (e.key === 'ArrowRight') go(current + 1);
            if (e.key === 'ArrowLeft') go(current - 1);
        });
        let startX = null;
        root.addEventListener('pointerdown', (e) => { if (e.pointerType !== 'mouse') startX = e.clientX; });
        root.addEventListener('pointerup', (e) => {
            if (startX === null) return;
            const dx = e.clientX - startX;
            startX = null;
            if (Math.abs(dx) > 50) go(current + (dx < 0 ? 1 : -1));
        });

        // Baştan sesli oku: her sahnenin sesi bitince sonraki sayfaya geçer.
        root.querySelector('[data-read-all]')?.addEventListener('click', () => {
            autoplay = true;
            go(0, true);
            leaves[0].querySelector('audio')?.play().catch(() => {});
        });
        leaves.forEach((leaf, i) => leaf.querySelector('audio')?.addEventListener('ended', () => {
            if (autoplay && i < leaves.length - 1) setTimeout(() => go(i + 1, true), 600);
            else autoplay = false;
        }));
        go(0);
    }

    function karaoke(leaf) {
        const text = leaf.querySelector('.scene-text');
        const audio = leaf.querySelector('audio');
        let marks = [];
        try { marks = JSON.parse(text.dataset.ts || '[]'); } catch { marks = []; }
        if (!audio || !marks.length) return;
        const words = text.textContent.trim().split(/\s+/);
        text.textContent = '';
        const spans = words.map((w, i) => {
            const s = document.createElement('span');
            s.textContent = w;
            text.append(s, i < words.length - 1 ? ' ' : '');
            return s;
        });
        audio.addEventListener('timeupdate', () => {
            const t = audio.currentTime;
            const index = marks.findIndex((m) => t >= m.start && t < m.end);
            spans.forEach((s, i) => s.classList.toggle('reading', i === index));
        });
        audio.addEventListener('ended', () => spans.forEach((s) => s.classList.remove('reading')));
    }
})();
