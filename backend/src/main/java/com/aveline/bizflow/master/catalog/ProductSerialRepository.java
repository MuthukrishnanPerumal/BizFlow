package com.aveline.bizflow.master.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductSerialRepository extends JpaRepository<ProductSerial, Long> {

    Optional<ProductSerial> findByProduct_IdAndSerialNumber(Long productId, String serialNumber);
}
