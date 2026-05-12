package com.bigfake.payments.repository;

import com.bigfake.payments.model.entity.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MerchantRepository extends JpaRepository<Merchant, Long> {

    Optional<Merchant> findByMerchantCode(String merchantCode);

    boolean existsByMerchantCodeAndIsActiveTrue(String merchantCode);
}
