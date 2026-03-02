package com.dentalclinic.model.appointment;

import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.model.schedule.DentistSchedule;
import com.dentalclinic.model.service.Services;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "appointment",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_appointment_customer_date_start",
                        columnNames = {"customer_id", "appointment_date", "start_time"}
                )
        },
        indexes = {
                @Index(name = "idx_appointment_date_start", columnList = "appointment_date, start_time"),
                @Index(name = "idx_appointment_customer_date", columnList = "customer_id, appointment_date"),
                @Index(name = "idx_appointment_status", columnList = "status")
        }
)
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private CustomerProfile customer;

    @ManyToOne
    @JoinColumn(name = "dentist_id")
    @JsonIgnore
    private DentistProfile dentist;

    @ManyToOne
    @JoinColumn(name = "service_id")
    private Services service;

    @ManyToOne
    @JoinColumn(name = "slot_id")
    @JsonIgnore
    private DentistSchedule slot;

    @Column(name = "appointment_date")
    private LocalDate date;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name="status")
    private AppointmentStatus status;

    @Column(name = "notes", columnDefinition = "NVARCHAR(MAX)")
    private String notes;

    @Column(name = "contact_channel", length = 20)
    private String contactChannel;

    @Column(name = "contact_value", length = 100)
    private String contactValue;

    @OneToMany(mappedBy = "appointment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("slotOrder ASC")
    private List<AppointmentSlot> appointmentSlots = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public CustomerProfile getCustomer() { return customer; }
    public void setCustomer(CustomerProfile customer) { this.customer = customer; }
    public DentistProfile getDentist() { return dentist; }
    public void setDentist(DentistProfile dentist) { this.dentist = dentist; }
    public Services getService() { return service; }
    public void setService(Services service) { this.service = service; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public AppointmentStatus getStatus() { return status; }
    public void setStatus(AppointmentStatus status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public DentistSchedule getSlot() { return slot; }
    public void setSlot(DentistSchedule slot) { this.slot = slot; }
    public String getContactChannel() { return contactChannel; }
    public void setContactChannel(String contactChannel) { this.contactChannel = contactChannel; }
    public String getContactValue() { return contactValue; }
    public void setContactValue(String contactValue) { this.contactValue = contactValue; }

    public List<AppointmentSlot> getAppointmentSlots() {
        return appointmentSlots;
    }

    public void setAppointmentSlots(List<AppointmentSlot> appointmentSlots) {
        this.appointmentSlots = appointmentSlots;
    }

    public void addAppointmentSlot(AppointmentSlot appointmentSlot) {
        appointmentSlots.add(appointmentSlot);
        appointmentSlot.setAppointment(this);
    }

    public void removeAppointmentSlot(AppointmentSlot appointmentSlot) {
        appointmentSlots.remove(appointmentSlot);
        appointmentSlot.setAppointment(null);
    }

    public void clearAppointmentSlots() {
        for (AppointmentSlot as : new ArrayList<>(appointmentSlots)) {
            removeAppointmentSlot(as);
        }
    }
}
