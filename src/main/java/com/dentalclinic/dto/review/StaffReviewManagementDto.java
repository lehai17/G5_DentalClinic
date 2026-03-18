package com.dentalclinic.dto.review;

import java.time.LocalDateTime;

public class StaffReviewManagementDto {
    private Long id;
    private Long appointmentId;
    private String customerName;
    private String dentistName;
    private String serviceName;
    private Integer dentistRating;
    private Integer serviceRating;
    private String comment;
    private LocalDateTime createdAt;
    private boolean featuredOnHomepage;
    private Integer displayOrder;
    private boolean hiddenCustomerName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(Long appointmentId) {
        this.appointmentId = appointmentId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getDentistName() {
        return dentistName;
    }

    public void setDentistName(String dentistName) {
        this.dentistName = dentistName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public Integer getDentistRating() {
        return dentistRating;
    }

    public void setDentistRating(Integer dentistRating) {
        this.dentistRating = dentistRating;
    }

    public Integer getServiceRating() {
        return serviceRating;
    }

    public void setServiceRating(Integer serviceRating) {
        this.serviceRating = serviceRating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isFeaturedOnHomepage() {
        return featuredOnHomepage;
    }

    public void setFeaturedOnHomepage(boolean featuredOnHomepage) {
        this.featuredOnHomepage = featuredOnHomepage;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public boolean isHiddenCustomerName() {
        return hiddenCustomerName;
    }

    public void setHiddenCustomerName(boolean hiddenCustomerName) {
        this.hiddenCustomerName = hiddenCustomerName;
    }
}