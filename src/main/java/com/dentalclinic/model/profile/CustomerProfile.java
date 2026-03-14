package com.dentalclinic.model.profile;

import com.dentalclinic.model.user.User;
import jakarta.persistence.*;

@Entity
@Table(name = "customer_profile")
public class CustomerProfile {

    @Id
    private Long id;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "full_name", columnDefinition = "NVARCHAR(255)",nullable = false)
    private String fullName;

    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "address", columnDefinition = "NVARCHAR(500)")
    private String address;

    @Column(name = "withdraw_bank_name", columnDefinition = "NVARCHAR(120)")
    private String withdrawBankName;

    @Column(name = "withdraw_account_no", length = 10)
    private String withdrawAccountNo;


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getWithdrawBankName() { return withdrawBankName; }
    public void setWithdrawBankName(String withdrawBankName) { this.withdrawBankName = withdrawBankName; }
    public String getWithdrawAccountNo() { return withdrawAccountNo; }
    public void setWithdrawAccountNo(String withdrawAccountNo) { this.withdrawAccountNo = withdrawAccountNo; }
}
