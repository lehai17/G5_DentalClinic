document.addEventListener('DOMContentLoaded', function() {
    const searchInput = document.querySelector('input[name="keyword"]');
    const tableContainer = document.querySelector('#dentistTableBody'); // Vùng chứa Fragment

    let timeout = null;

    if (searchInput) {
        searchInput.addEventListener('input', function() {
            // Xóa timeout cũ để tránh gửi yêu cầu liên tục (Debouncing)
            clearTimeout(timeout);
            const keyword = this.value;

            // Chờ người dùng ngừng gõ 300ms mới gửi yêu cầu
            timeout = setTimeout(() => {
                // Gọi API trả về Fragment
                fetch(`/admin/dentists/api/search?keyword=${encodeURIComponent(keyword)}`)
                    .then(response => {
                        if (!response.ok) throw new Error('Network response was not ok');
                        return response.text();
                    })
                    .then(html => {
                        // Thay thế nội dung bên trong div bằng đoạn HTML mới nhận được
                        tableContainer.innerHTML = html;
                    })
                    .catch(error => console.error('Error fetching search results:', error));
            }, 300);
        });
    }
});