(() => {
    const form = document.getElementById("loginForm");
    const btn = document.getElementById("loginBtn");

    if (!form || !btn) return;

    form.addEventListener("submit", () => {
        // tránh click nhiều lần
        btn.classList.add("is-loading");
        btn.disabled = true;
    });
})();
