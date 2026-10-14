package com.aveline.bizflow.purchase;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseReturnItemRepository extends JpaRepository<PurchaseReturnItem, Long> {

    List<PurchaseReturnItem> findByPurchaseItem_Id(Long purchaseItemId);
}
