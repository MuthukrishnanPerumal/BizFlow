package com.aveline.bizflow.inventory;

import com.aveline.bizflow.common.exception.BusinessRuleException;
import com.aveline.bizflow.notification.StockAlertPublisher;
import com.aveline.bizflow.master.RecordStatus;
import com.aveline.bizflow.master.StockType;
import com.aveline.bizflow.master.catalog.Product;
import com.aveline.bizflow.master.catalog.ProductBatch;
import com.aveline.bizflow.master.catalog.ProductBatchRepository;
import com.aveline.bizflow.master.catalog.ProductSerial;
import com.aveline.bizflow.master.catalog.ProductSerialRepository;
import org.springframework.data.domain.AuditorAware;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class InventoryPostingService {

    private static final BigDecimal ZERO = InventoryMath.ZERO;

    private final InventoryBalanceRepository balanceRepository;
    private final StockLedgerRepository ledgerRepository;
    private final InventoryCostLayerRepository layerRepository;
    private final ProductBatchRepository batchRepository;
    private final ProductSerialRepository serialRepository;
    private final StockLocationService locationService;
    private final InventoryPolicy policy;
    private final JdbcTemplate jdbcTemplate;
    private final AuditorAware<String> auditorAware;
    private final Clock clock;
    private final StockAlertPublisher stockAlertPublisher;

    public InventoryPostingService(
            InventoryBalanceRepository balanceRepository,
            StockLedgerRepository ledgerRepository,
            InventoryCostLayerRepository layerRepository,
            ProductBatchRepository batchRepository,
            ProductSerialRepository serialRepository,
            StockLocationService locationService,
            InventoryPolicy policy,
            JdbcTemplate jdbcTemplate,
            AuditorAware<String> auditorAware,
            Clock clock,
            StockAlertPublisher stockAlertPublisher) {
        this.balanceRepository = balanceRepository;
        this.ledgerRepository = ledgerRepository;
        this.layerRepository = layerRepository;
        this.batchRepository = batchRepository;
        this.serialRepository = serialRepository;
        this.locationService = locationService;
        this.policy = policy;
        this.jdbcTemplate = jdbcTemplate;
        this.auditorAware = auditorAware;
        this.clock = clock;
        this.stockAlertPublisher = stockAlertPublisher;
    }

    public void receive(Movement movement) {
        if (movement.product().getStockType() == StockType.NONE) {
            return;
        }
        StockLocation location = locationService.requireActive(movement.locationId());
        ensureBatch(movement);
        if (movement.affectSerials()) {
            for (String serial : movement.serials()) {
                receiveSerial(movement.product(), serial, location.getId());
            }
        }
        apply(movement, location, true);
        addLayer(location, movement);
    }

    public BigDecimal issue(Movement movement) {
        if (movement.product().getStockType() == StockType.NONE) {
            return ZERO;
        }
        StockLocation location = locationService.requireActive(movement.locationId());
        if (movement.affectSerials()) {
            for (String serial : movement.serials()) {
                issueSerial(movement.product(), serial);
            }
        }
        apply(movement, location, false);
        return consumeLayers(location, movement);
    }

    public BigDecimal quoteIssueUnitCost(
            Product product,
            String batchCode,
            LocalDate expiry,
            BigDecimal quantity) {
        return quoteIssueUnitCost(product, null, batchCode, expiry, quantity);
    }

    public BigDecimal quoteIssueUnitCost(
            Product product,
            Long locationId,
            String batchCode,
            LocalDate expiry,
            BigDecimal quantity) {
        if (product.getStockType() == StockType.NONE) {
            return ZERO;
        }
        StockLocation location = locationService.requireActive(locationId);
        String batch = batchKey(batchCode);
        InventoryBalance bucket = balanceRepository
                .lockBucket(location.getId(), product.getId(), batch, expiry)
                .orElse(null);
        BigDecimal available = bucket == null
                ? ZERO
                : InventoryMath.available(bucket.getQuantityOnHand(), bucket.getReservedQuantity());
        if (!policy.allowNegativeStock() && available.compareTo(quantity) < 0) {
            throw insufficient(product);
        }
        BigDecimal average = bucket == null ? product.getPurchasePrice() : bucket.getUnitCost();
        if (policy.valuationMethod() == ValuationMethod.FIFO && bucket != null) {
            return fifoQuote(location, product, batch, expiry, quantity, average);
        }
        return average;
    }

    public void assertAvailable(Product product, Long locationId, String batchCode, LocalDate expiry, BigDecimal quantity) {
        if (product.getStockType() == StockType.NONE) {
            return;
        }
        StockLocation location = locationService.requireActive(locationId);
        InventoryBalance bucket = balanceRepository
                .lockBucket(location.getId(), product.getId(), batchKey(batchCode), expiry)
                .orElse(null);
        BigDecimal available = bucket == null
                ? ZERO
                : InventoryMath.available(bucket.getQuantityOnHand(), bucket.getReservedQuantity());
        if (available.compareTo(quantity) < 0) {
            throw insufficient(product);
        }
    }

    public void reserve(Product product, Long locationId, String batchCode, LocalDate expiry, BigDecimal quantity) {
        if (product.getStockType() == StockType.NONE) {
            return;
        }
        StockLocation location = locationService.requireActive(locationId);
        String batch = batchKey(batchCode);
        ensureBucket(product.getId(), location.getId(), batch, expiry);
        InventoryBalance bucket = lock(location.getId(), product.getId(), batch, expiry);
        BigDecimal available = InventoryMath.available(bucket.getQuantityOnHand(), bucket.getReservedQuantity());
        if (available.compareTo(quantity) < 0) {
            throw insufficient(product);
        }
        bucket.reserve(quantity);
    }

    public void release(Product product, Long locationId, String batchCode, LocalDate expiry, BigDecimal quantity) {
        if (product.getStockType() == StockType.NONE) {
            return;
        }
        StockLocation location = locationService.requireActive(locationId);
        String batch = batchKey(batchCode);
        InventoryBalance bucket = lock(location.getId(), product.getId(), batch, expiry);
        if (bucket.getReservedQuantity().compareTo(quantity) < 0) {
            throw new BusinessRuleException(
                    "RESERVATION",
                    "quantity",
                    product.getName() + " does not have that much reserved stock");
        }
        bucket.release(quantity);
    }

    public void relocateSerials(Product product, List<String> serials, Long locationId) {
        if (serials == null || serials.isEmpty()) {
            return;
        }
        StockLocation location = locationService.requireActive(locationId);
        for (String serialNumber : serials) {
            ProductSerial serial = serialRepository.findByProduct_IdAndSerialNumber(product.getId(), serialNumber)
                    .orElseThrow(() -> new BusinessRuleException("SERIAL", "serials", "Serial " + serialNumber + " is not in stock"));
            if (serial.getStatus() != RecordStatus.ACTIVE) {
                throw new BusinessRuleException("SERIAL", "serials", "Serial " + serialNumber + " is not in stock");
            }
            serial.place(location.getId());
        }
    }

    private void apply(Movement movement, StockLocation location, boolean inbound) {
        if (movement.quantity().signum() <= 0) {
            throw new BusinessRuleException("QUANTITY", "quantity", "Quantity must be greater than zero");
        }
        if (movement.unitCost().signum() < 0 || movement.totalValue().signum() < 0) {
            throw new BusinessRuleException("NEGATIVE_AMOUNT", "unitCost", "Stock value cannot be negative");
        }
        String batchCode = batchKey(movement.batchCode());
        ensureBucket(movement.product().getId(), location.getId(), batchCode, movement.expiryDate());
        InventoryBalance bucket = lock(location.getId(), movement.product().getId(), batchCode, movement.expiryDate());
        BigDecimal oldQty = bucket.getQuantityOnHand();
        BigDecimal oldValue = oldQty.multiply(bucket.getUnitCost()).setScale(4, RoundingMode.HALF_UP);
        BigDecimal newQty;
        BigDecimal newCost;
        if (inbound) {
            newQty = oldQty.add(movement.quantity());
            newCost = InventoryMath.weightedAverage(oldQty, bucket.getUnitCost(), movement.quantity(), movement.totalValue());
        } else {
            BigDecimal available = InventoryMath.available(oldQty, bucket.getReservedQuantity());
            if (!policy.allowNegativeStock() && available.compareTo(movement.quantity()) < 0) {
                throw insufficient(movement.product());
            }
            newQty = oldQty.subtract(movement.quantity());
            if (newQty.signum() == 0) {
                newCost = ZERO;
            } else if (newQty.signum() < 0) {
                newCost = bucket.getUnitCost();
            } else {
                BigDecimal newValue = oldValue.subtract(movement.totalValue());
                if (newValue.signum() < 0) {
                    newValue = ZERO;
                }
                newCost = newValue.divide(newQty, 4, RoundingMode.HALF_UP);
            }
        }
        bucket.apply(newQty, newCost);
        String referenceNumber = movement.referenceNumber();
        if (referenceNumber == null || referenceNumber.isBlank()) {
            referenceNumber = movement.referenceId();
        }
        String ledgerBatch = batchCode.isEmpty() ? null : batchCode;
        ledgerRepository.save(StockLedgerEntry.record(
                movement.entryDate(),
                movement.product(),
                movement.transactionType(),
                movement.referenceType(),
                movement.referenceId(),
                referenceNumber,
                location,
                ledgerBatch,
                movement.expiryDate(),
                inbound ? movement.quantity() : ZERO,
                inbound ? ZERO : movement.quantity(),
                newQty,
                movement.unitCost(),
                movement.totalValue(),
                movement.remarks()));
        stockAlertPublisher.observe(movement.product());
    }

    private void addLayer(StockLocation location, Movement movement) {
        BigDecimal layerCost = movement.layerUnitCost() == null ? movement.unitCost() : movement.layerUnitCost();
        layerRepository.save(InventoryCostLayer.open(
                location,
                movement.product(),
                batchKey(movement.batchCode()),
                movement.expiryDate(),
                movement.entryDate(),
                movement.quantity(),
                layerCost,
                movement.referenceType(),
                movement.referenceId()));
    }

    private BigDecimal consumeLayers(StockLocation location, Movement movement) {
        String batch = batchKey(movement.batchCode());
        List<InventoryCostLayer> layers = layerRepository.lockOpenLayers(
                location.getId(),
                movement.product().getId(),
                batch,
                movement.expiryDate());
        BigDecimal remaining = movement.quantity();
        BigDecimal value = ZERO;
        for (InventoryCostLayer layer : layers) {
            if (remaining.signum() == 0) {
                break;
            }
            BigDecimal take = layer.getQuantityRemaining().min(remaining);
            value = value.add(take.multiply(layer.getUnitCost()));
            layer.consume(take);
            remaining = remaining.subtract(take);
        }
        if (remaining.signum() > 0) {
            value = value.add(remaining.multiply(movement.unitCost()));
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal fifoQuote(
            StockLocation location,
            Product product,
            String batch,
            LocalDate expiry,
            BigDecimal quantity,
            BigDecimal fallbackAverage) {
        List<InventoryCostLayer> layers = layerRepository.lockOpenLayers(location.getId(), product.getId(), batch, expiry);
        BigDecimal remaining = quantity;
        BigDecimal value = ZERO;
        for (InventoryCostLayer layer : layers) {
            if (remaining.signum() == 0) {
                break;
            }
            BigDecimal take = layer.getQuantityRemaining().min(remaining);
            value = value.add(take.multiply(layer.getUnitCost()));
            remaining = remaining.subtract(take);
        }
        if (remaining.signum() > 0) {
            value = value.add(remaining.multiply(fallbackAverage));
        }
        if (quantity.signum() == 0) {
            return ZERO;
        }
        return value.divide(quantity, 4, RoundingMode.HALF_UP);
    }

    private InventoryBalance lock(Long locationId, Long productId, String batchCode, LocalDate expiry) {
        return balanceRepository.lockBucket(locationId, productId, batchCode, expiry)
                .orElseThrow(() -> new IllegalStateException("Inventory balance was not created"));
    }

    private void ensureBucket(Long productId, Long locationId, String batchCode, LocalDate expiry) {
        String actor = auditorAware.getCurrentAuditor().orElse("system");
        Timestamp now = Timestamp.from(clock.instant());
        jdbcTemplate.update(
                """
                INSERT INTO inventory_balance (
                    location_id, product_id, batch_code, expiry_date, quantity_on_hand, reserved_quantity, unit_cost,
                    created_at, updated_at, created_by, updated_by, is_deleted, version
                ) VALUES (?, ?, ?, ?, 0, 0, 0, ?, ?, ?, ?, 0, 0)
                ON DUPLICATE KEY UPDATE updated_at = updated_at
                """,
                locationId,
                productId,
                batchCode,
                expiry,
                now,
                now,
                actor,
                actor);
    }

    private void ensureBatch(Movement movement) {
        String batchCode = batchKey(movement.batchCode());
        if (batchCode.isEmpty()) {
            return;
        }
        ProductBatch batch = batchRepository.findByProduct_IdAndBatchCode(movement.product().getId(), batchCode).orElse(null);
        if (batch == null) {
            batchRepository.save(ProductBatch.open(movement.product(), batchCode, movement.expiryDate()));
            return;
        }
        if (batch.getExpiresOn() != null
                && movement.expiryDate() != null
                && !batch.getExpiresOn().equals(movement.expiryDate())) {
            throw new BusinessRuleException(
                    "BATCH_EXPIRY",
                    "batch",
                    "Batch " + batchCode + " is already recorded with a different expiry date");
        }
        batch.noteExpiry(movement.expiryDate());
    }

    private void receiveSerial(Product product, String serialNumber, Long locationId) {
        ProductSerial serial = serialRepository.findByProduct_IdAndSerialNumber(product.getId(), serialNumber).orElse(null);
        if (serial == null) {
            ProductSerial created = ProductSerial.receive(product, serialNumber);
            created.place(locationId);
            serialRepository.save(created);
            return;
        }
        if (serial.getStatus() == RecordStatus.ACTIVE) {
            throw new BusinessRuleException("SERIAL", "serials", "Serial " + serialNumber + " is already in stock");
        }
        serial.activate();
        serial.place(locationId);
    }

    private void issueSerial(Product product, String serialNumber) {
        ProductSerial serial = serialRepository.findByProduct_IdAndSerialNumber(product.getId(), serialNumber)
                .orElseThrow(() -> new BusinessRuleException("SERIAL", "serials", "Serial " + serialNumber + " is not in stock"));
        if (serial.getStatus() != RecordStatus.ACTIVE) {
            throw new BusinessRuleException("SERIAL", "serials", "Serial " + serialNumber + " is not in stock");
        }
        serial.deactivate();
    }

    private BusinessRuleException insufficient(Product product) {
        return new BusinessRuleException(
                "INSUFFICIENT_STOCK",
                "quantity",
                product.getName() + " does not have enough stock");
    }

    private String batchKey(String batchCode) {
        return batchCode == null ? "" : batchCode;
    }

    public record Movement(
            Product product,
            String batchCode,
            LocalDate expiryDate,
            BigDecimal quantity,
            BigDecimal unitCost,
            BigDecimal totalValue,
            StockTxnType transactionType,
            String referenceType,
            String referenceId,
            LocalDate entryDate,
            String remarks,
            List<String> serials,
            Long locationId,
            String referenceNumber,
            boolean affectSerials,
            BigDecimal layerUnitCost) {

        public Movement {
            serials = serials == null ? List.of() : List.copyOf(serials);
        }

        public Movement(
                Product product,
                String batchCode,
                LocalDate expiryDate,
                BigDecimal quantity,
                BigDecimal unitCost,
                BigDecimal totalValue,
                StockTxnType transactionType,
                String referenceType,
                String referenceId,
                LocalDate entryDate,
                String remarks,
                List<String> serials) {
            this(
                    product,
                    batchCode,
                    expiryDate,
                    quantity,
                    unitCost,
                    totalValue,
                    transactionType,
                    referenceType,
                    referenceId,
                    entryDate,
                    remarks,
                    serials,
                    null,
                    null,
                    true,
                    null);
        }
    }
}
