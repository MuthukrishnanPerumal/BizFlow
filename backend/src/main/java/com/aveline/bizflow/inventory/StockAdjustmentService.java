package com.aveline.bizflow.inventory;

import com.aveline.bizflow.audit.AuditOutcome;
import com.aveline.bizflow.audit.AuditRecorder;
import com.aveline.bizflow.common.api.LikePatterns;
import com.aveline.bizflow.common.api.PageRequests;
import com.aveline.bizflow.common.api.PageResponse;
import com.aveline.bizflow.common.exception.BusinessRuleException;
import com.aveline.bizflow.common.exception.ResourceNotFoundException;
import com.aveline.bizflow.document.DocumentNumberService;
import com.aveline.bizflow.document.DocumentSeries;
import com.aveline.bizflow.inventory.InventoryPostingService.Movement;
import com.aveline.bizflow.inventory.dto.InventoryDtos.AdjustmentRequest;
import com.aveline.bizflow.inventory.dto.InventoryDtos.AdjustmentResponse;
import com.aveline.bizflow.master.MasterText;
import com.aveline.bizflow.master.RecordStatus;
import com.aveline.bizflow.master.StockType;
import com.aveline.bizflow.master.catalog.Product;
import com.aveline.bizflow.master.catalog.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class StockAdjustmentService {

    private static final Set<String> SORTS = Set.of("adjustmentDate", "adjustmentNumber", "quantity", "totalValue");

    private final StockAdjustmentRepository adjustmentRepository;
    private final ProductRepository productRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final StockLocationService locationService;
    private final InventoryPostingService postingService;
    private final DocumentNumberService documentNumberService;
    private final AuditRecorder auditRecorder;

    public StockAdjustmentService(
            StockAdjustmentRepository adjustmentRepository,
            ProductRepository productRepository,
            InventoryBalanceRepository balanceRepository,
            StockLocationService locationService,
            InventoryPostingService postingService,
            DocumentNumberService documentNumberService,
            AuditRecorder auditRecorder) {
        this.adjustmentRepository = adjustmentRepository;
        this.productRepository = productRepository;
        this.balanceRepository = balanceRepository;
        this.locationService = locationService;
        this.postingService = postingService;
        this.documentNumberService = documentNumberService;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdjustmentResponse> list(
            String search,
            Long productId,
            Long locationId,
            AdjustmentDirection direction,
            LocalDate dateFrom,
            LocalDate dateTo,
            int page,
            int size,
            String sort) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new BusinessRuleException("DATE_RANGE", "dateFrom", "The start date cannot be after the end date");
        }
        Pageable pageable = PageRequests.of(page, size, sort, SORTS, "adjustmentDate");
        Page<StockAdjustment> result = adjustmentRepository.search(
                LikePatterns.contain(search), productId, locationId, direction, dateFrom, dateTo, pageable);
        return new PageResponse<>(
                result.getContent().stream().map(this::response).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdjustmentResponse get(Long id) {
        return response(adjustmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stock adjustment was not found")));
    }

    public AdjustmentResponse create(AdjustmentRequest request) {
        InventoryAccess.require("INVENTORY_ADJUST");
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product was not found"));
        if (product.getStockType() == StockType.NONE) {
            throw new BusinessRuleException("STOCK_TYPE", "productId", product.getName() + " is not a stocked product");
        }
        if (product.getStatus() != RecordStatus.ACTIVE) {
            throw new BusinessRuleException("PRODUCT_INACTIVE", "productId", product.getName() + " is inactive");
        }
        direction(request.direction(), request.reason());
        BigDecimal quantity = InventoryQuantities.quantity(
                request.quantity(), product.getUnit().isDecimalAllowed(), "quantity", "Quantity");
        String batch = InventoryLines.batch(product, request.batch());
        LocalDate expiry = InventoryLines.expiry(product, request.expiryDate());
        List<String> serials = InventoryLines.serials(product, quantity, request.serials());
        StockLocation location = locationService.requireActive(request.locationId());
        String reference = MasterText.optional(request.referenceNumber(), 64, "referenceNumber", "Reference number");
        String notes = MasterText.optional(request.notes(), 1000, "notes", "Notes");
        BigDecimal unitCost = cost(product, location, batch, expiry, request.direction(), quantity, request.unitCost());
        BigDecimal totalValue = InventoryMath.stockValue(quantity, unitCost);
        String number = documentNumberService.next(DocumentSeries.ADJUSTMENT, request.adjustmentDate().getYear());
        StockAdjustment adjustment = adjustmentRepository.save(StockAdjustment.post(
                number,
                location,
                product,
                batch,
                expiry,
                request.direction(),
                quantity,
                unitCost,
                totalValue,
                request.reason(),
                reference,
                request.adjustmentDate(),
                notes));
        StockTxnType type = request.reason() == AdjustmentReason.OPENING_STOCK
                ? StockTxnType.OPENING_STOCK
                : StockTxnType.STOCK_ADJUSTMENT;
        Movement movement = new Movement(
                product,
                batch,
                expiry,
                quantity,
                unitCost,
                totalValue,
                type,
                "STOCK_ADJUSTMENT",
                String.valueOf(adjustment.getId()),
                request.adjustmentDate(),
                InventoryQuantities.remarks(number + " " + request.reason().name(), notes),
                serials,
                location.getId(),
                number,
                true,
                null);
        if (request.direction() == AdjustmentDirection.INCREASE) {
            postingService.receive(movement);
        } else {
            postingService.issue(movement);
        }
        auditRecorder.record(
                "INVENTORY",
                "STOCK_ADJUSTED",
                AuditOutcome.SUCCESS,
                "STOCK_ADJUSTMENT",
                String.valueOf(adjustment.getId()),
                number + " " + request.direction().name().toLowerCase() + " " + quantity.toPlainString()
                        + " of " + product.getProductCode());
        return response(adjustment);
    }

    private BigDecimal cost(
            Product product,
            StockLocation location,
            String batch,
            LocalDate expiry,
            AdjustmentDirection direction,
            BigDecimal quantity,
            BigDecimal requested) {
        if (direction == AdjustmentDirection.DECREASE) {
            return postingService.quoteIssueUnitCost(product, location.getId(), batch, expiry, quantity);
        }
        if (requested != null) {
            return InventoryQuantities.money(requested, "unitCost", "Unit cost");
        }
        return balanceRepository.lockBucket(location.getId(), product.getId(), batch, expiry)
                .filter(bucket -> bucket.getQuantityOnHand().signum() > 0)
                .map(InventoryBalance::getUnitCost)
                .orElse(product.getPurchasePrice());
    }

    private void direction(AdjustmentDirection direction, AdjustmentReason reason) {
        boolean increaseOnly = reason == AdjustmentReason.OPENING_STOCK || reason == AdjustmentReason.FOUND;
        boolean decreaseOnly = reason == AdjustmentReason.DAMAGE
                || reason == AdjustmentReason.THEFT
                || reason == AdjustmentReason.WRITE_OFF;
        if (increaseOnly && direction != AdjustmentDirection.INCREASE) {
            throw new BusinessRuleException("DIRECTION", "direction", reason.name() + " can only increase stock");
        }
        if (decreaseOnly && direction != AdjustmentDirection.DECREASE) {
            throw new BusinessRuleException("DIRECTION", "direction", reason.name() + " can only decrease stock");
        }
    }

    private AdjustmentResponse response(StockAdjustment adjustment) {
        return new AdjustmentResponse(
                adjustment.getId(),
                adjustment.getAdjustmentNumber(),
                adjustment.getAdjustmentDate(),
                adjustment.getDirection(),
                adjustment.getReason(),
                adjustment.getQuantity(),
                adjustment.getUnitCost(),
                adjustment.getTotalValue(),
                adjustment.getReferenceNumber(),
                adjustment.getNotes(),
                adjustment.getBatchCode(),
                adjustment.getExpiryDate(),
                adjustment.getProduct().getId(),
                adjustment.getProduct().getProductCode(),
                adjustment.getProduct().getName(),
                adjustment.getLocation().getId(),
                adjustment.getLocation().getLocationCode(),
                adjustment.getLocation().getName(),
                adjustment.getCreatedBy(),
                adjustment.getVersion());
    }
}
