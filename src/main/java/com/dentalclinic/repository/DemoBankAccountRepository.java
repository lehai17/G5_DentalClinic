package com.dentalclinic.repository;

import com.dentalclinic.model.wallet.DemoBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DemoBankAccountRepository extends JpaRepository<DemoBankAccount, Long> {
    Optional<DemoBankAccount> findByBankNameAndAccountNo(String bankName, String accountNo);
}
