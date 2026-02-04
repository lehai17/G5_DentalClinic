document.addEventListener('DOMContentLoaded', function () {

    const items = document.querySelectorAll('.visit-item');

    items.forEach(item => {
        const header = item.querySelector('.visit-header');

        header.addEventListener('click', () => {

            // Đóng tất cả trước
            items.forEach(i => {
                if(i !== item){
                    i.classList.remove('active');
                    const text = i.querySelector('.toggle-text');
                    if(text) text.textContent = "View Detail";
                }
            });

            // Toggle cái hiện tại
            item.classList.toggle('active');

            const text = item.querySelector('.toggle-text');
            if(item.classList.contains('active')){
                text.textContent = "Hide Detail";
            }else{
                text.textContent = "View Detail";
            }

        });
    });

});