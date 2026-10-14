package com.aveline.bizflow.inventory;

import com.aveline.bizflow.master.RecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockLocationRepository extends JpaRepository<StockLocation, Long> {

    Optional<StockLocation> findByLocationCode(String locationCode);

    Optional<StockLocation> findByDefaultLocationTrue();

    boolean existsByLocationCode(String locationCode);

    List<StockLocation> findByStatusOrderByNameAsc(RecordStatus status);
}
