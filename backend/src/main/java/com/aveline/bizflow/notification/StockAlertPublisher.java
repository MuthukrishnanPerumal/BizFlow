package com.aveline.bizflow.notification;

import com.aveline.bizflow.inventory.InventoryBalanceRepository;
import com.aveline.bizflow.master.StockType;
import com.aveline.bizflow.master.catalog.Product;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class StockAlertPublisher {

    private final InventoryBalanceRepository balanceRepository;
    private final AppNotificationService notifications;

    public StockAlertPublisher(InventoryBalanceRepository balanceRepository, AppNotificationService notifications) {
        this.balanceRepository = balanceRepository;
        this.notifications = notifications;
    }

    public void observe(Product product) {
        if (product == null || product.getStockType() == StockType.NONE || product.getId() == null) {
            return;
        }
        balanceRepository.flush();
        BigDecimal onHand = balanceRepository.quantityOnHand(product.getId());
        NotificationKind kind = StockSignals.classify(onHand, product.getReorderLevel());
        if (kind == null) {
            return;
        }
        NotificationSeverity severity = kind == NotificationKind.OUT_OF_STOCK
                ? NotificationSeverity.CRITICAL
                : NotificationSeverity.WARNING;
        String title = kind == NotificationKind.OUT_OF_STOCK ? "Out of stock" : "Low stock";
        String message = product.getProductCode() + " " + product.getName()
                + (kind == NotificationKind.OUT_OF_STOCK
                ? " is out of stock."
                : " is at or below its reorder level.");
        notifications.raise(kind, severity, title, message, "INVENTORY", "product", String.valueOf(product.getId()));
    }
}
