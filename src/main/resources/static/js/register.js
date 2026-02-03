(() => {
    const form = document.getElementById("registerForm");
    const btn = document.getElementById("signupBtn");
    if (!form || !btn) return;

    form.addEventListener("submit", () => {
        btn.classList.add("is-loading");
        btn.disabled = true;
    });
})();
