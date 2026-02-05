// File: static/js/admin/dentist-viewdetail.js
function showDentistDetail(element) {
    try {
        // 1. Lấy dữ liệu an toàn
        const name = element.getAttribute('data-fullname') || 'N/A';
        const email = element.getAttribute('data-email') || 'N/A';
        // ... lấy các trường khác tương tự ...

        // 2. Gán vào Modal ID
        document.getElementById('viewFullName').innerText = name;
        document.getElementById('viewEmail').innerText = email;

        // 3. Kích hoạt Modal
        const modalElement = document.getElementById('viewDentistModal');
        const myModal = new bootstrap.Modal(modalElement);
        myModal.show();
    } catch (error) {
        console.error("Lỗi hiển thị Modal:", error);
    }
}