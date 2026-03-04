package com.dentalclinic.repository;

import com.dentalclinic.model.wallet.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {
    Optional<Wallet> findByCustomer_User_Id(Long userId);
    Optional<Wallet> findByCustomer_Id(Long customerId);
}
