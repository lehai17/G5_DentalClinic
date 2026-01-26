function showBookingModal() {
    document.getElementById('bookingModal').classList.add('active');
}

function closeModal(id) {
    document.getElementById(id).classList.remove('active');
}

function scrollToSection(id) {
    document.getElementById(id).scrollIntoView({ behavior: 'smooth' });
}

function handleServiceClick(element) {
    const id = element.getAttribute('data-id');
    const name = element.getAttribute('data-name');
    const desc = element.getAttribute('data-description');
    const price = element.getAttribute('data-price');

    // Gọi lại hàm show modal cũ của bạn với dữ liệu đã lấy được
    showServiceModal(id, name, desc, price);
}

function showServiceModal(id, name, desc, price) {
    console.log("Service clicked:", name);
    // Bạn có thể thêm alert hoặc hiện modal ở đây
    alert("Dịch vụ: " + name + "\nGiá: " + price + " ₫");
}

// Đóng modal khi click ra ngoài
window.onclick = function(event) {
    if (event.target.classList.contains('modal')) {
        event.target.classList.remove('active');
    }
}