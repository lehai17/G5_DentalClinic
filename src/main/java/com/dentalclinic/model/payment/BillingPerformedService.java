package com.dentalclinic.model.payment;

import com.dentalclinic.model.service.Services;
import jakarta.persistence.*;

@Entity
@Table(name = "billing_performed_service")
public class BillingPerformedService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "billing_note_id")
    private BillingNote billingNote;

    @ManyToOne
    @JoinColumn(name = "service_id")
    private Services service;

    @Column(nullable = false)
    private int qty;

    @Column(name = "tooth_no", length = 50)
    private String toothNo;

    // getters & setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public BillingNote getBillingNote() { return billingNote; }
    public void setBillingNote(BillingNote billingNote) { this.billingNote = billingNote; }

    public Services getService() { return service; }
    public void setService(Services service) { this.service = service; }

    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }

    public String getToothNo() { return toothNo; }
    public void setToothNo(String toothNo) { this.toothNo = toothNo; }
}