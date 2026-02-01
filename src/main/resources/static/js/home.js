// Các hàm tiện ích
function scrollToSection(id) {
    const el = document.getElementById(id);
    if(el) el.scrollIntoView({ behavior: 'smooth' });
}

function handleServiceClick(element) {
    const name = element.getAttribute('data-name');
    const price = element.getAttribute('data-price');
    alert("Dịch vụ: " + name + "\nGiá: " + price + " ₫");
}

window.loadBlogPage = function(page) {
    console.log("===> Đang kích hoạt AJAX cho trang:", page);

    const blogSection = document.getElementById('blog');
    if (!blogSection) {
        console.error("Không tìm thấy thẻ id='blog'");
        return;
    }

    blogSection.style.opacity = '0.5';

    // Sử dụng URL tương đối
    fetch('/homepage?page=' + page)
        .then(response => {
            if (!response.ok) throw new Error('Network response was not ok');
            return response.text();
        })
        .then(html => {
            const parser = new DOMParser();
            const doc = parser.parseFromString(html, 'text/html');
            const newContent = doc.querySelector('#blog').innerHTML;

            blogSection.innerHTML = newContent;
            blogSection.style.opacity = '1';

            // Cuộn lên đầu phần blog mượt mà
            blogSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
            console.log("===> Cập nhật thành công trang:", page);
        })
        .catch(err => {
            console.error("Lỗi AJAX:", err);
            blogSection.style.opacity = '1';
        });
};

// Xử lý đóng modal bằng Event Listener (Tránh đè lên sự kiện khác)
window.addEventListener('click', function(event) {
    if (event.target.classList.contains('modal')) {
        event.target.classList.remove('active');
    }
});

//Xử lý đóng mở tiện ích từ ava profile
    function toggleUserMenu(e){
    e.stopPropagation();
    const dd = document.getElementById("userDropdown");
    dd.classList.toggle("hidden");
}

    document.addEventListener("click", function(){
    const dd = document.getElementById("userDropdown");
    if(dd && !dd.classList.contains("hidden")){
    dd.classList.add("hidden");
}
});

