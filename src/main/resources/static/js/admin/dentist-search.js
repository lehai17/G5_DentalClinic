document.addEventListener('DOMContentLoaded', function() {
    const searchInput = document.querySelector('input[id="dentistSearch"]');
    const tableContainer = document.querySelector('#dentistTableBody');

    let timeout = null;

    if (searchInput) {
        searchInput.addEventListener('input', function() {
            clearTimeout(timeout);
            const keyword = this.value;

            timeout = setTimeout(() => {
                const specialty = document.getElementById('specialtyFilter')?.value || '';
                const status = document.getElementById('statusFilter')?.value || '';

                fetch(`/admin/dentists/api/search?keyword=${encodeURIComponent(keyword)}&specialty=${encodeURIComponent(specialty)}&status=${encodeURIComponent(status)}`)
                    .then(response => {
                        if (!response.ok) throw new Error('Network response was not ok');
                        return response.text();
                    })
                    .then(html => {
                        tableContainer.innerHTML = html;
                    })
                    .catch(error => console.error('Error fetching search results:', error));
            }, 300);
        });
    }

    // Add listeners for filters too
    ['specialtyFilter', 'statusFilter'].forEach(id => {
        const el = document.getElementById(id);
        if (el) {
            el.addEventListener('change', () => {
                const keyword = document.getElementById('dentistSearch')?.value || '';
                const specialty = document.getElementById('specialtyFilter')?.value || '';
                const status = document.getElementById('statusFilter')?.value || '';

                fetch(`/admin/dentists/api/search?keyword=${encodeURIComponent(keyword)}&specialty=${encodeURIComponent(specialty)}&status=${encodeURIComponent(status)}`)
                    .then(response => response.text())
                    .then(html => {
                        tableContainer.innerHTML = html;
                    });
            });
        }
    });
});
