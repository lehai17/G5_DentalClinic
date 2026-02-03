(function () {
    function getDropdown() {
        return document.getElementById("userDropdown");
    }

    // gọi từ onclick avatar
    window.toggleUserMenu = function (e) {
        if (e) e.stopPropagation();
        const dd = getDropdown();
        if (!dd) return;
        dd.classList.toggle("hidden");
    };

    // click ngoài user-menu thì đóng
    document.addEventListener("click", function (e) {
        const dd = getDropdown();
        if (!dd) return;

        // nếu click nằm trong khối user-menu (avatar + dropdown) thì không đóng
        const inside = e.target.closest(".user-menu");
        if (!inside) dd.classList.add("hidden");
    });

    // ESC để đóng
    document.addEventListener("keydown", function (e) {
        if (e.key === "Escape") {
            const dd = getDropdown();
            if (dd) dd.classList.add("hidden");
        }
    });
})();
