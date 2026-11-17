/* Profil: hesap silme onay penceresi.
 * Yerleşik <dialog> showModal() ile açılır: sayfanın geri kalanı etkisiz olur (odak pencerede kalır), Esc kapatır.
 * İlk alana odaklanır; kapanınca odak açan düğmeye döner. "Kalıcı olarak sil" yalnızca "SİL" yazılıp parola girilince açılır
 * (asıl denetim sunucudadır). Betik yoksa pencere sayfada açık bir form olarak görünür (CSS) ve sunucu yine denetler. */
(function () {
    "use strict";
    var dialog = document.getElementById("delete-dialog");
    var opener = document.querySelector('[data-open-dialog="delete-dialog"]');
    if (!dialog || !opener || typeof dialog.showModal !== "function") return;

    var form = dialog.querySelector("[data-delete-form]");
    var word = dialog.querySelector("#confirmWord");
    var password = dialog.querySelector("#deletePassword");
    var submit = dialog.querySelector("[data-delete-submit]");

    function valid() {
        var w = word.value.trim().toLocaleUpperCase("tr");
        return (w === "SİL" || w === "SIL") && password.value.length > 0;
    }
    function update() {
        submit.disabled = !valid();
        submit.setAttribute("aria-disabled", submit.disabled ? "true" : "false");
    }
    function open() {
        dialog.showModal();
        update();
        word.focus();
    }

    opener.addEventListener("click", open);
    dialog.querySelector("[data-close-dialog]").addEventListener("click", function () { dialog.close(); });
    // Pencerenin dışındaki karartmaya tıklanınca kapanır
    dialog.addEventListener("click", function (e) { if (e.target === dialog) dialog.close(); });
    dialog.addEventListener("close", function () {
        word.value = ""; password.value = "";
        opener.focus();
    });
    // Odak tuzağı: Tab ve Shift+Tab pencerenin içinde döner
    dialog.addEventListener("keydown", function (e) {
        if (e.key !== "Tab") return;
        var items = Array.prototype.filter.call(dialog.querySelectorAll("input, button, a[href], [tabindex]:not([tabindex='-1'])"),
            function (el) { return !el.disabled && el.type !== "hidden" && el.offsetParent !== null; });
        if (!items.length) return;
        var first = items[0], last = items[items.length - 1];
        if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
        else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
    });
    word.addEventListener("input", update);
    password.addEventListener("input", update);
    form.addEventListener("submit", function (e) {
        if (!valid()) { e.preventDefault(); update(); return; }
        submit.disabled = true; // çift gönderimi önle
        submit.textContent = "Siliniyor…";
    });

    update();
    // Sunucu hata döndürdüyse (yanlış parola vb.) pencere tekrar açık gelir
    if (dialog.hasAttribute("data-open-on-load")) open();
})();
