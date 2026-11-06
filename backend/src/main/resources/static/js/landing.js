// Ana sayfa etkileşimleri. Script olmadan da tüm içerik görünür; hareketler "hareketi azalt" ayarına uyar.
(() => {
    const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
    const fine = matchMedia('(hover: hover) and (pointer: fine)').matches;
    const colors = ['#ffcf4a', '#ff7a59', '#5fd3a8', '#4aa8ff', '#6f61d8', '#ffb3c7'];
    const bar = document.querySelector('.bar');
    const meter = document.querySelector('.scroll-meter span');

    // Kaydırma çubuğu ve üst bar gölgesi
    const onScroll = () => {
        const max = document.documentElement.scrollHeight - innerHeight;
        meter?.style.setProperty('--s', max > 0 ? (scrollY / max).toFixed(4) : 0);
        bar?.classList.toggle('is-scrolled', scrollY > 10);
    };
    addEventListener('scroll', () => requestAnimationFrame(onScroll), { passive: true });
    onScroll();

    // Ortaya çıkış ve sayaçlar
    document.querySelectorAll('[data-reveal="stagger"]').forEach((group) =>
        [...group.children].forEach((el, i) => el.style.setProperty('--i', i)));
    const io = new IntersectionObserver((entries) => {
        for (const e of entries) {
            if (!e.isIntersecting) continue;
            e.target.classList.add('is-in');
            e.target.querySelectorAll('[data-count]').forEach(count);
            io.unobserve(e.target);
        }
    }, { rootMargin: '0px 0px -10% 0px', threshold: 0.12 });
    document.querySelectorAll('[data-reveal]').forEach((el) => io.observe(el));

    function count(el) {
        const target = Number(el.dataset.count);
        if (reduce) { el.textContent = target; return; }
        const start = performance.now();
        const tick = (now) => {
            const p = Math.min(1, (now - start) / 1400);
            el.textContent = Math.round(target * (1 - Math.pow(1 - p, 3)));
            if (p < 1) requestAnimationFrame(tick);
        };
        requestAnimationFrame(tick);
    }

    // Kayan bant: kesintisiz döngü için içerik ikiye katlanır
    const track = document.querySelector('.marquee-track');
    if (track) track.append(...[...track.children].map((n) => n.cloneNode(true)));

    // Giriş sahnesinde fareyle derinlik (paralaks)
    const hero = document.querySelector('[data-parallax]');
    if (hero && fine && !reduce) {
        const layers = [...hero.querySelectorAll('[data-depth]')];
        hero.addEventListener('pointermove', (e) => {
            const r = hero.getBoundingClientRect();
            const x = (e.clientX - r.left) / r.width - 0.5;
            const y = (e.clientY - r.top) / r.height - 0.5;
            layers.forEach((l) => { const d = Number(l.dataset.depth) * 60; l.style.translate = `${(-x * d).toFixed(1)}px ${(-y * d).toFixed(1)}px`; });
        });
        hero.addEventListener('pointerleave', () => layers.forEach((l) => { l.style.translate = ''; }));
    }

    // Eğilen kartlar
    if (fine && !reduce) document.querySelectorAll('[data-tilt]').forEach((card) => {
        card.addEventListener('pointermove', (e) => {
            const r = card.getBoundingClientRect();
            const x = (e.clientX - r.left) / r.width - 0.5;
            const y = (e.clientY - r.top) / r.height - 0.5;
            card.style.transform = `perspective(700px) rotateY(${x * 14}deg) rotateX(${-y * 14}deg) translateY(-6px)`;
        });
        card.addEventListener('pointerleave', () => { card.style.transform = ''; });
    });

    // Dönen kahraman kartları (dokunmatik için tıkla-çevir)
    document.querySelectorAll('[data-flip]').forEach((b) => b.addEventListener('click', () => b.classList.toggle('is-flipped')));

    // Butonlardan kıvılcım
    const sparks = (x, y, n = 14) => {
        if (reduce) return;
        for (let i = 0; i < n; i++) {
            const s = document.createElement('i');
            s.className = 'spark';
            const a = (Math.PI * 2 * i) / n + Math.random() * 0.4;
            const d = 50 + Math.random() * 70;
            s.style.cssText = `left:${x - 7}px;top:${y - 7}px;--c:${colors[i % colors.length]};--dx:${Math.cos(a) * d}px;--dy:${Math.sin(a) * d}px`;
            document.body.append(s);
            s.addEventListener('animationend', () => s.remove());
        }
    };
    document.querySelectorAll('[data-sparkle]').forEach((b) => {
        b.addEventListener('pointerenter', (e) => { if (fine) sparks(e.clientX, e.clientY, 8); });
        b.addEventListener('click', (e) => sparks(e.clientX, e.clientY, 18));
    });

    // Masal makinesi: seç, sihri başlat, sahne değişsin, ilk sayfa yazılsın
    const machine = document.querySelector('[data-machine]');
    if (machine) {
        const stage = machine.querySelector('.stage');
        const heroUse = machine.querySelector('[data-hero-use]');
        const placeUse = machine.querySelector('[data-place-use]');
        const story = machine.querySelector('[data-story]');
        const burst = machine.querySelector('.burst');
        let typing = null;
        const pick = (group) => machine.querySelector(`${group} [aria-checked="true"]`);
        const select = (btn) => {
            btn.parentElement.querySelectorAll('[role="radio"]').forEach((b) => b.setAttribute('aria-checked', String(b === btn)));
        };
        const write = (text) => {
            clearInterval(typing);
            if (reduce) { story.textContent = text; return; }
            let i = 0;
            story.textContent = '';
            const caret = Object.assign(document.createElement('span'), { className: 'caret' });
            story.append('', caret);
            typing = setInterval(() => {
                i += 2;
                story.firstChild.textContent = text.slice(0, i);
                if (i >= text.length) { clearInterval(typing); caret.remove(); }
            }, 22);
        };
        const pop = () => {
            if (reduce) return;
            burst.replaceChildren(...Array.from({ length: 16 }, (_, i) => {
                const s = document.createElement('i');
                const a = (Math.PI * 2 * i) / 16;
                s.style.cssText = `--x:62%;--y:60%;--c:${colors[i % colors.length]};--dx:${Math.cos(a) * 140}px;--dy:${Math.sin(a) * 110}px`;
                return s;
            }));
        };
        const cast = () => {
            const h = pick('.avatars');
            const p = pick('.places');
            const f = pick('.feelings');
            heroUse.setAttribute('href', `/img/art.svg#${h.dataset.art}`);
            placeUse.setAttribute('href', `/img/art.svg#${p.dataset.icon}`);
            stage.style.setProperty('--sky', p.dataset.sky);
            stage.style.setProperty('--ground', p.dataset.ground);
            stage.classList.toggle('is-night', p.hasAttribute('data-night'));
            stage.classList.remove('is-casting');
            void stage.offsetWidth;
            stage.classList.add('is-casting');
            pop();
            write(`Bir varmış bir yokmuş, ${p.dataset.place} ${h.dataset.hero} adında ${h.dataset.kind} yaşarmış. Bir gün ${h.dataset.hero} ${f.dataset.feel}`);
        };
        machine.querySelectorAll('[role="radio"]').forEach((b) => b.addEventListener('click', () => { select(b); cast(); }));
        machine.querySelector('[data-cast]').addEventListener('click', (e) => { cast(); sparks(e.clientX, e.clientY, 18); });
    }

    // Nasıl çalışır: ortadaki adım etkin olur, cihaz ekranı değişir
    const steps = [...document.querySelectorAll('.step')];
    const screens = [...document.querySelectorAll('.device .screen')];
    if (steps.length) {
        const sio = new IntersectionObserver((entries) => {
            for (const e of entries) {
                if (!e.isIntersecting) continue;
                const k = Number(e.target.dataset.step);
                steps.forEach((s, i) => s.classList.toggle('is-active', i === k));
                screens.forEach((s, i) => s.classList.toggle('is-on', i === k));
            }
        }, { rootMargin: '-45% 0px -45% 0px' });
        steps.forEach((s) => sio.observe(s));
    }

    // Gece gökyüzüne yıldızlar
    const sky = document.querySelector('.stars');
    if (sky) {
        for (let i = 0; i < 70; i++) {
            const s = document.createElement('i');
            s.style.cssText = `--x:${Math.random() * 100}%;--y:${20 + Math.random() * 70}%;--z:${2 + Math.random() * 4}px;--t:${1.5 + Math.random() * 3}s;animation-delay:${Math.random() * 3}s`;
            sky.append(s);
        }
    }
})();
