package com.dentalclinic.model.appointment;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "appointment_slot")
public class AppointmentSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @ManyToOne
    @JoinColumn(name = "slot_id", nullable = false)
    private Slot slot;

    @Column(name = "slot_order", nullable = false)
    private int slotOrder;

    public AppointmentSlot() {
    }

    public AppointmentSlot(Appointment appointment, Slot slot, int slotOrder) {
        this.appointment = appointment;
        this.slot = slot;
        this.slotOrder = slotOrder;
    }

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

    public Slot getSlot() {
        return slot;
    }

    public void setSlot(Slot slot) {
        this.slot = slot;
    }

    public int getSlotOrder() {
        return slotOrder;
    }

    public void setSlotOrder(int slotOrder) {
        this.slotOrder = slotOrder;
    }

    public LocalDateTime getSlotTime() {
        return slot != null ? slot.getSlotTime() : null;
    }
}
