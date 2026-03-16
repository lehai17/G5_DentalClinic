package com.dentalclinic.dto.admin;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class AdminDashboardStatsDTO {
    private long totalCustomers;
    private long totalAppointments;
    private BigDecimal totalRevenue;
    private long pendingAppointments;
    private double growthRate; // Percentage growth in customers this month

    private List<AdminAgendaItemDto> recentAppointments;
    private Map<String, BigDecimal> revenueTrend; // Date as String -> Revenue
    private Map<String, Long> serviceDistribution; // Service Name -> Count

    public AdminDashboardStatsDTO() {
    }

    public AdminDashboardStatsDTO(long totalCustomers, long totalAppointments, BigDecimal totalRevenue,
            long pendingAppointments) {
        this.totalCustomers = totalCustomers;
        this.totalAppointments = totalAppointments;
        this.totalRevenue = totalRevenue;
        this.pendingAppointments = pendingAppointments;
    }

    // Getters and Setters
    public long getTotalCustomers() {
        return totalCustomers;
    }

    public void setTotalCustomers(long totalCustomers) {
        this.totalCustomers = totalCustomers;
    }

    public long getTotalAppointments() {
        return totalAppointments;
    }

    public void setTotalAppointments(long totalAppointments) {
        this.totalAppointments = totalAppointments;
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(BigDecimal totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public long getPendingAppointments() {
        return pendingAppointments;
    }

    public void setPendingAppointments(long pendingAppointments) {
        this.pendingAppointments = pendingAppointments;
    }

    public double getGrowthRate() {
        return growthRate;
    }

    public void setGrowthRate(double growthRate) {
        this.growthRate = growthRate;
    }

    public List<AdminAgendaItemDto> getRecentAppointments() {
        return recentAppointments;
    }

    public void setRecentAppointments(List<AdminAgendaItemDto> recentAppointments) {
        this.recentAppointments = recentAppointments;
    }

    public Map<String, BigDecimal> getRevenueTrend() {
        return revenueTrend;
    }

    public void setRevenueTrend(Map<String, BigDecimal> revenueTrend) {
        this.revenueTrend = revenueTrend;
    }

    public Map<String, Long> getServiceDistribution() {
        return serviceDistribution;
    }

    public void setServiceDistribution(Map<String, Long> serviceDistribution) {
        this.serviceDistribution = serviceDistribution;
    }
}
