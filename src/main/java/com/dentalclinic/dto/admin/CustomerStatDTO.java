package com.dentalclinic.dto.admin;

public class CustomerStatDTO {
    private long totalCustomers;
    private long newCustomersThisMonth;
    private long customersWithAppointments;

    public CustomerStatDTO() {
    }

    public CustomerStatDTO(long totalCustomers, long newCustomersThisMonth, long customersWithAppointments) {
        this.totalCustomers = totalCustomers;
        this.newCustomersThisMonth = newCustomersThisMonth;
        this.customersWithAppointments = customersWithAppointments;
    }

    public long getTotalCustomers() {
        return totalCustomers;
    }

    public void setTotalCustomers(long totalCustomers) {
        this.totalCustomers = totalCustomers;
    }

    public long getNewCustomersThisMonth() {
        return newCustomersThisMonth;
    }

    public void setNewCustomersThisMonth(long newCustomersThisMonth) {
        this.newCustomersThisMonth = newCustomersThisMonth;
    }

    public long getCustomersWithAppointments() {
        return customersWithAppointments;
    }

    public void setCustomersWithAppointments(long customersWithAppointments) {
        this.customersWithAppointments = customersWithAppointments;
    }
}
