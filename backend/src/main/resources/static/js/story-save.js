/* Geçici masal: "Kaydetme" hemen silmez. 5 saniyelik "Geri al" bildirimi gösterilir; süre dolunca silme formu gönderilir.
 * Bu sürede sayfadan ayrılınca hiçbir şey silinmez (masal süresi dolana kadar "Son üretilen" olarak kalır).
 * Betik yoksa form doğrudan gönderilir. */
(function () {
    "use strict";
    var form = document.querySelector("[data-discard-form]");
    var toast = document.querySelector("[data-undo-toast]");
    if (!form || !toast) return;
    var text = toast.querySelector("[data-undo-text]");
    var undo = toast.querySelector("[data-undo]");
    var timer = null, tick = null, opener = null;

    function cancel() {
        clearTimeout(timer); clearInterval(tick); timer = null;
        toast.hidden = true;
        if (opener) opener.focus();
    }

    function start(button) {
        if (timer) return;
        opener = button;
        var left = 5;
        text.textContent = "Masal kaydedilmeyecek ve silinecek (" + left + " sn).";
        toast.hidden = false;
        undo.focus();
        tick = setInterval(function () {
            left -= 1;
            if (left > 0) text.textContent = "Masal kaydedilmeyecek ve silinecek (" + left + " sn).";
        }, 1000);
        timer = setTimeout(function () {
            clearInterval(tick);
            text.textContent = "Siliniyor…";
            undo.disabled = true;
            form.submit(); // submit() olay tetiklemez; döngü olmaz
        }, 5000);
    }

    document.querySelectorAll("[data-discard]").forEach(function (b) {
        b.addEventListener("click", function (e) { e.preventDefault(); start(b); });
    });
    undo.addEventListener("click", cancel);
    document.addEventListener("keydown", function (e) { if (e.key === "Escape" && timer) cancel(); });
})();
