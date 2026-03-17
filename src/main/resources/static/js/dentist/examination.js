document.addEventListener('DOMContentLoaded', function () {
    const items = Array.from(document.querySelectorAll('.visit-item[data-step-id]'));
    const navButtons = Array.from(document.querySelectorAll('.visit-nav-btn[data-step-id]'));

    if (!items.length) {
        return;
    }

    function setActiveItem(targetItem) {
        items.forEach(item => {
            const isActive = item === targetItem;
            item.classList.toggle('active', isActive);

            const text = item.querySelector('.toggle-text');
            if (text) {
                text.textContent = isActive ? 'An chi tiet' : 'Xem chi tiet';
            }
        });

        navButtons.forEach(button => {
            button.classList.toggle('active', button.dataset.stepId === targetItem.dataset.stepId);
        });
    }

    items.forEach(item => {
        const header = item.querySelector('.visit-header');
        if (!header) {
            return;
        }

        header.addEventListener('click', () => {
            const alreadyActive = item.classList.contains('active');
            if (alreadyActive) {
                item.classList.remove('active');
                const text = item.querySelector('.toggle-text');
                if (text) {
                    text.textContent = 'Xem chi tiet';
                }
                navButtons.forEach(button => button.classList.remove('active'));
                return;
            }

            setActiveItem(item);
        });

        header.addEventListener('keydown', (event) => {
            if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                header.click();
            }
        });
    });

    navButtons.forEach(button => {
        button.addEventListener('click', () => {
            const target = items.find(item => item.dataset.stepId === button.dataset.stepId);
            if (!target) {
                return;
            }

            setActiveItem(target);
            target.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        });
    });

});
