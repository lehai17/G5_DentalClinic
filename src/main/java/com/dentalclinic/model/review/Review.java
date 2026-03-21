package com.dentalclinic.model.review;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.model.service.Services;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "review")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private CustomerProfile customer;

    @ManyToOne
    @JoinColumn(name = "dentist_id")
    private DentistProfile dentist;

    @ManyToOne
    @JoinColumn(name = "service_id")
    private Services service;

    @OneToOne
    @JoinColumn(name = "appointment_id", unique = true)
    private Appointment appointment;

    @Column(name = "dentist_rating", nullable = false)
    private int dentistRating;

    @Column(name = "service_rating", nullable = false)
    private int serviceRating;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String comment;

    @Column(name = "approved", nullable = false)
    private boolean approved = true;

    @Column(name = "featured_on_homepage", nullable = false)
    private boolean featuredOnHomepage = false;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "hidden_customer_name", nullable = false)
    private boolean hiddenCustomerName = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CustomerProfile getCustomer() {
        return customer;
    }

    public void setCustomer(CustomerProfile customer) {
        this.customer = customer;
    }

    public DentistProfile getDentist() {
        return dentist;
    }

    public void setDentist(DentistProfile dentist) {
        this.dentist = dentist;
    }

    public Services getService() {
        return service;
    }

    public void setService(Services service) {
        this.service = service;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public void setAppointment(Appointment appointment) {
        this.appointment = appointment;
    }

    public int getDentistRating() {
        return dentistRating;
    }

    public void setDentistRating(int dentistRating) {
        this.dentistRating = dentistRating;
    }

    public int getServiceRating() {
        return serviceRating;
    }

    public void setServiceRating(int serviceRating) {
        this.serviceRating = serviceRating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
