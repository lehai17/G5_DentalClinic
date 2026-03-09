package com.dentalclinic.model.appointment;

import com.dentalclinic.model.service.Services;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

@Entity
@Table(
        name = "appointment_detail",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_appointment_detail_appointment_service",
                        columnNames = {"appointment_id", "service_id"}
                )
        }
)
public class AppointmentDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @ManyToOne
    @JoinColumn(name = "service_id", nullable = false)
    private Services service;

    @Column(name = "service_name_snapshot", nullable = false, length = 255)
    private String serviceNameSnapshot;

    @Column(name = "price_snapshot", nullable = false, precision = 18, scale = 2)
    private BigDecimal priceSnapshot;

    @Column(name = "duration_snapshot", nullable = false)
    private Integer durationSnapshot;

    @Column(name = "detail_order", nullable = false)
    private Integer detailOrder;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public void setAppointment(Appointment appointment) {
        this.appointment = appointment;
    }

    public Services getService() {
        return service;
    }

    public void setService(Services service) {
        this.service = service;
    }

    public String getServiceNameSnapshot() {
        return serviceNameSnapshot;
    }

    public void setServiceNameSnapshot(String serviceNameSnapshot) {
        this.serviceNameSnapshot = serviceNameSnapshot;
    }

    public BigDecimal getPriceSnapshot() {
        return priceSnapshot;
    }

    public void setPriceSnapshot(BigDecimal priceSnapshot) {
        this.priceSnapshot = priceSnapshot;
    }

    public Integer getDurationSnapshot() {
        return durationSnapshot;
    }

    public void setDurationSnapshot(Integer durationSnapshot) {
        this.durationSnapshot = durationSnapshot;
    }

    public Integer getDetailOrder() {
        return detailOrder;
    }

    public void setDetailOrder(Integer detailOrder) {
        this.detailOrder = detailOrder;
    }
}
