package com.aveline.bizflow.sale;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SaleReturnItemRepository extends JpaRepository<SaleReturnItem, Long> {

    List<SaleReturnItem> findBySaleItem_Id(Long saleItemId);
}
