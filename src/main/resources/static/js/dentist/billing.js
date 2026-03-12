document.getElementById('billingForm').addEventListener('submit', () => {

    const rows = document.querySelectorAll('#performedTable tbody tr');

    rows.forEach((tr, i) => {

        const service = tr.querySelector('select');
        const qty = tr.querySelector('.svc-qty');
        const tooth = tr.querySelector('.svc-tooth');

        if(service) service.name = `performedServices[${i}].service.id`;
        if(qty) qty.name = `performedServices[${i}].qty`;
        if(tooth) tooth.name = `performedServices[${i}].toothNo`;

    });

    document.querySelectorAll('#rxTable tbody tr').forEach(tr => {

        const med = tr.querySelector('[name$=".medicineName"]')?.value;
        const dose = tr.querySelector('[name$=".dosage"]')?.value;
        const note = tr.querySelector('[name$=".note"]')?.value;

        if(!med && !dose && !note){
            tr.remove();
        }

    });

});