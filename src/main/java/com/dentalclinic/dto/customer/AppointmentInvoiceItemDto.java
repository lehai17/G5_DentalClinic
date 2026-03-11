package com.dentalclinic.dto.customer;

import java.math.BigDecimal;

public class AppointmentInvoiceItemDto {
    private String name;
    private Integer qty;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String toothNo;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getToothNo() { return toothNo; }
    public void setToothNo(String toothNo) { this.toothNo = toothNo; }
}
