/*
 * Yönetim: karakter ve mekân kütüphanesi.
 * - "Yeni … oluştur" alanı açılınca ilk alana odaklanır ve alan görünür hale kaydırılır.
 * - Seçilen örnek resimleri yüklemeden önce önizler; boyut (5 MB), tür (JPEG/PNG) ve sayı sınırını hemen bildirir.
 * Sunucu her şeyi yeniden denetler; bu dosya çalışmasa da formlar çalışır.
 */
(function () {
    "use strict";
    var MAX_BYTES = 5 * 1024 * 1024;
    var TYPES = ["image/jpeg", "image/png"];

    Array.prototype.forEach.call(document.querySelectorAll("details.create-panel"), function (panel) {
        panel.addEventListener("toggle", function () {
            if (!panel.open) return;
            var first = panel.querySelector("input:not([type=hidden]), select, textarea");
            panel.scrollIntoView({ behavior: matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth", block: "start" });
            if (first) setTimeout(function () { first.focus({ preventScroll: true }); }, 250);
        });
    });

    Array.prototype.forEach.call(document.querySelectorAll("[data-image-picker]"), function (input) {
        var box = input.closest("fieldset");
        var preview = box.querySelector("[data-image-preview]");
        var warning = box.querySelector("[data-image-warning]");
        var max = parseInt(input.getAttribute("data-max"), 10) || 4;
        var existing = parseInt(input.getAttribute("data-existing"), 10) || 0;
        var urls = [];

        function removed() {
            return box.querySelectorAll("input[name=removeImageIds]:checked").length;
        }

        function render() {
            urls.forEach(URL.revokeObjectURL);
            urls = [];
            preview.innerHTML = "";
            var files = Array.prototype.slice.call(input.files || []);
            var problems = [];
            files.forEach(function (f, i) {
                var li = document.createElement("li");
                if (TYPES.indexOf(f.type) < 0) problems.push(f.name + ": yalnızca JPEG ya da PNG");
                else if (f.size > MAX_BYTES) problems.push(f.name + ": 5 MB'tan büyük");
                if (TYPES.indexOf(f.type) >= 0) {
                    var img = document.createElement("img");
                    var url = URL.createObjectURL(f);
                    urls.push(url);
                    img.src = url;
                    img.alt = "Seçilen resim " + (i + 1);
                    li.appendChild(img);
                }
                var name = document.createElement("span");
                name.className = "sample-name";
                name.textContent = f.name + " · " + (f.size / 1024 / 1024).toFixed(1) + " MB";
                li.appendChild(name);
                preview.appendChild(li);
            });
            var total = existing - removed() + files.length;
            if (total > max) problems.push("Toplam en fazla " + max + " örnek resim olabilir (şu an " + total + ")");
            preview.hidden = files.length === 0;
            warning.hidden = problems.length === 0;
            warning.textContent = problems.join(" · ");
            input.setAttribute("aria-invalid", problems.length ? "true" : "false");
        }

        input.addEventListener("change", render);
        Array.prototype.forEach.call(box.querySelectorAll("input[name=removeImageIds]"), function (cb) {
            cb.addEventListener("change", function () {
                cb.closest("li").classList.toggle("is-removed", cb.checked);
                render();
            });
        });
    });
})();
