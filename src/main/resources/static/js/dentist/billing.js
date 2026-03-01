document.addEventListener('DOMContentLoaded', function () {

    // Highlight service when checked
    document.querySelectorAll('.service-item input[type="checkbox"]')
        .forEach(cb => {
            cb.addEventListener('change', function () {
                const row = this.closest('.service-item');
                if (this.checked) {
                    row.style.background = '#ffffff';
                    row.style.boxShadow = '0 10px 25px rgba(37,99,235,0.15)';
                    row.style.borderColor = '#3b82f6';
                } else {
                    row.style.background = '#f8fafc';
                    row.style.boxShadow = 'none';
                    row.style.borderColor = '#e2e8f0';
                }
            });
        });

});