(function () {
    function getDropdown() {
        return document.getElementById("userDropdown");
    }

    function getNotificationDropdown() {
        return document.getElementById("notificationDropdown");
    }

    // gọi từ onclick avatar
    window.toggleUserMenu = function (e) {
        if (e) e.stopPropagation();
        const dd = getDropdown();
        if (!dd) return;
        dd.classList.toggle("hidden");

        const nd = getNotificationDropdown();
        if (nd) nd.classList.add("hidden");
    };

    window.toggleNotificationMenu = function (e) {
        if (e) e.stopPropagation();
        const nd = getNotificationDropdown();
        if (!nd) return;
        nd.classList.toggle("hidden");

        const dd = getDropdown();
        if (dd) dd.classList.add("hidden");
    };

    // click ngoài user-menu thì đóng
    document.addEventListener("click", function (e) {
        const dd = getDropdown();
        const nd = getNotificationDropdown();
        if (!dd && !nd) return;

        // nếu click nằm trong khối user-menu (avatar + dropdown) thì không đóng
        const inside = e.target.closest(".user-menu");
        const insideNotification = e.target.closest(".notification-menu");
        if (!inside && dd) dd.classList.add("hidden");
        if (!insideNotification && nd) nd.classList.add("hidden");
    });

    // ESC để đóng
    document.addEventListener("keydown", function (e) {
        if (e.key === "Escape") {
            const dd = getDropdown();
            const nd = getNotificationDropdown();
            if (dd) dd.classList.add("hidden");
            if (nd) nd.classList.add("hidden");
        }
    });
})();
