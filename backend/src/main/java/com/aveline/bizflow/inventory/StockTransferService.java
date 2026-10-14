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
import com.aveline.bizflow.inventory.dto.InventoryDtos.TransferLineRequest;
import com.aveline.bizflow.inventory.dto.InventoryDtos.TransferLineResponse;
import com.aveline.bizflow.inventory.dto.InventoryDtos.TransferRequest;
import com.aveline.bizflow.inventory.dto.InventoryDtos.TransferResponse;
import com.aveline.bizflow.master.MasterText;
import com.aveline.bizflow.master.MasterVersions;
import com.aveline.bizflow.master.RecordStatus;
import com.aveline.bizflow.master.StockType;
import com.aveline.bizflow.master.catalog.Product;
import com.aveline.bizflow.master.catalog.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class StockTransferService {

    private static final Set<String> SORTS = Set.of("transferDate", "transferNumber", "status");

    private final StockTransferRepository transferRepository;
    private final ProductRepository productRepository;
    private final StockLocationService locationService;
    private final InventoryPostingService postingService;
    private final DocumentNumberService documentNumberService;
    private final AuditRecorder auditRecorder;

    public StockTransferService(
            StockTransferRepository transferRepository,
            ProductRepository productRepository,
            StockLocationService locationService,
            InventoryPostingService postingService,
            DocumentNumberService documentNumberService,
            AuditRecorder auditRecorder) {
        this.transferRepository = transferRepository;
        this.productRepository = productRepository;
        this.locationService = locationService;
        this.postingService = postingService;
        this.documentNumberService = documentNumberService;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransferResponse> list(
            String search,
            TransferStatus status,
            Long fromLocationId,
            Long toLocationId,
            LocalDate dateFrom,
            LocalDate dateTo,
            int page,
            int size,
            String sort) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new BusinessRuleException("DATE_RANGE", "dateFrom", "The start date cannot be after the end date");
        }
        Pageable pageable = PageRequests.of(page, size, sort, SORTS, "transferDate");
        Page<StockTransfer> result = transferRepository.search(
                LikePatterns.contain(search), status, fromLocationId, toLocationId, dateFrom, dateTo, pageable);
        return new PageResponse<>(
                result.getContent().stream().map(transfer -> response(transfer, false)).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public TransferResponse get(Long id) {
        return response(load(id), true);
    }

    public TransferResponse create(TransferRequest request) {
        InventoryAccess.require("INVENTORY_TRANSFER");
        StockLocation from = locationService.requireActive(request.fromLocationId());
        StockLocation to = locationService.requireActive(request.toLocationId());
        if (from.getId().equals(to.getId())) {
            throw new BusinessRuleException("SAME_LOCATION", "toLocationId", "Choose a different destination location");
        }
        String reason = MasterText.required(request.reason(), 200, "reason", "Reason");
        String notes = MasterText.optional(request.notes(), 1000, "notes", "Notes");
        String number = documentNumberService.next(DocumentSeries.TRANSFER, request.transferDate().getYear());
        StockTransfer transfer = StockTransfer.draft(number, from, to, request.transferDate(), reason, notes);
        int lineNumber = 1;
        Set<String> keys = new HashSet<>();
        for (TransferLineRequest line : request.lines()) {
            PreparedLine prepared = prepare(line, lineNumber);
            if (!keys.add(prepared.key())) {
                throw new BusinessRuleException("DUPLICATE_LINE", "lines", "This product and batch is already on the transfer");
            }
            transfer.addLine(StockTransferLine.create(
                    transfer,
                    lineNumber,
                    prepared.product(),
                    prepared.batch(),
                    prepared.expiry(),
                    prepared.quantity(),
                    prepared.serials()));
            lineNumber++;
        }
        StockTransfer saved = transferRepository.save(transfer);
        if (Boolean.TRUE.equals(request.complete())) {
            finish(saved);
            audit("STOCK_TRANSFER_COMPLETED", saved);
        }
        return response(saved, true);
    }

    public TransferResponse confirm(Long id, Long version) {
        InventoryAccess.require("INVENTORY_TRANSFER");
        StockTransfer transfer = lock(id, version);
        if (transfer.getStatus() != TransferStatus.DRAFT) {
            throw new BusinessRuleException("STATUS", "status", "Only a draft transfer can be confirmed");
        }
        for (StockTransferLine line : transfer.getLines()) {
            postingService.reserve(
                    line.getProduct(),
                    transfer.getFromLocation().getId(),
                    line.getBatchCode(),
                    line.getExpiryDate(),
                    line.getQuantity());
        }
        transfer.confirm();
        audit("STOCK_TRANSFER_CONFIRMED", transfer);
        return response(transfer, true);
    }

    public TransferResponse complete(Long id, Long version) {
        InventoryAccess.require("INVENTORY_TRANSFER");
        StockTransfer transfer = lock(id, version);
        if (transfer.getStatus() != TransferStatus.DRAFT && transfer.getStatus() != TransferStatus.CONFIRMED) {
            throw new BusinessRuleException("STATUS", "status", "This transfer cannot be completed");
        }
        finish(transfer);
        audit("STOCK_TRANSFER_COMPLETED", transfer);
        return response(transfer, true);
    }

    public TransferResponse cancel(Long id, Long version) {
        InventoryAccess.require("INVENTORY_TRANSFER");
        StockTransfer transfer = lock(id, version);
        if (transfer.getStatus() == TransferStatus.COMPLETED || transfer.getStatus() == TransferStatus.CANCELLED) {
            throw new BusinessRuleException("STATUS", "status", "This transfer cannot be cancelled");
        }
        if (transfer.getStatus() == TransferStatus.CONFIRMED) {
            for (StockTransferLine line : transfer.getLines()) {
                postingService.release(
                        line.getProduct(),
                        transfer.getFromLocation().getId(),
                        line.getBatchCode(),
                        line.getExpiryDate(),
                        line.getQuantity());
            }
        }
        transfer.cancel();
        audit("STOCK_TRANSFER_CANCELLED", transfer);
        return response(transfer, true);
    }

    private void finish(StockTransfer transfer) {
        if (transfer.getStatus() == TransferStatus.CONFIRMED) {
            for (StockTransferLine line : transfer.getLines()) {
                postingService.release(
                        line.getProduct(),
                        transfer.getFromLocation().getId(),
                        line.getBatchCode(),
                        line.getExpiryDate(),
                        line.getQuantity());
            }
        }
        for (StockTransferLine line : transfer.getLines()) {
            postingService.assertAvailable(
                    line.getProduct(),
                    transfer.getFromLocation().getId(),
                    line.getBatchCode(),
                    line.getExpiryDate(),
                    line.getQuantity());
            BigDecimal unitCost = postingService.quoteIssueUnitCost(
                    line.getProduct(),
                    transfer.getFromLocation().getId(),
                    line.getBatchCode(),
                    line.getExpiryDate(),
                    line.getQuantity());
            BigDecimal totalValue = InventoryMath.stockValue(line.getQuantity(), unitCost);
            line.assignCost(unitCost, totalValue);
            String remarks = InventoryQuantities.remarks(
                    "Transfer " + transfer.getTransferNumber(),
                    transfer.getReason());
            BigDecimal layerValue = postingService.issue(movement(
                    transfer,
                    line,
                    transfer.getFromLocation().getId(),
                    StockTxnType.STOCK_TRANSFER_OUT,
                    unitCost,
                    totalValue,
                    null,
                    remarks));
            BigDecimal layerCost = line.getQuantity().signum() == 0 || layerValue.signum() == 0
                    ? unitCost
                    : layerValue.divide(line.getQuantity(), 4, RoundingMode.HALF_UP);
            postingService.receive(movement(
                    transfer,
                    line,
                    transfer.getToLocation().getId(),
                    StockTxnType.STOCK_TRANSFER_IN,
                    unitCost,
                    totalValue,
                    layerCost,
                    remarks));
            postingService.relocateSerials(line.getProduct(), line.serialNumbers(), transfer.getToLocation().getId());
        }
        transfer.complete();
    }

    private Movement movement(
            StockTransfer transfer,
            StockTransferLine line,
            Long locationId,
            StockTxnType type,
            BigDecimal unitCost,
            BigDecimal totalValue,
            BigDecimal layerUnitCost,
            String remarks) {
        return new Movement(
                line.getProduct(),
                line.getBatchCode(),
                line.getExpiryDate(),
                line.getQuantity(),
                unitCost,
                totalValue,
                type,
                "STOCK_TRANSFER",
                String.valueOf(transfer.getId()),
                transfer.getTransferDate(),
                remarks,
                List.of(),
                locationId,
                transfer.getTransferNumber(),
                false,
                layerUnitCost);
    }

    private PreparedLine prepare(TransferLineRequest request, int lineNumber) {
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product was not found"));
        if (product.getStockType() == StockType.NONE) {
            throw new BusinessRuleException("STOCK_TYPE", "lines", product.getName() + " is not a stocked product");
        }
        if (product.getStatus() != RecordStatus.ACTIVE) {
            throw new BusinessRuleException("PRODUCT_INACTIVE", "lines", product.getName() + " is inactive");
        }
        BigDecimal quantity = InventoryQuantities.quantity(
                request.quantity(), product.getUnit().isDecimalAllowed(), "lines", "Quantity");
        String batch = InventoryLines.batch(product, request.batch());
        LocalDate expiry = InventoryLines.expiry(product, request.expiryDate());
        List<String> serials = InventoryLines.serials(product, quantity, request.serials());
        String key = product.getId() + "|" + batch + "|" + expiry;
        return new PreparedLine(product, batch, expiry, quantity, serials, key, lineNumber);
    }

    private StockTransfer lock(Long id, Long version) {
        StockTransfer transfer = transferRepository.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stock transfer was not found"));
        MasterVersions.check(transfer.getVersion(), version);
        transfer.getLines().size();
        return transfer;
    }

    private StockTransfer load(Long id) {
        StockTransfer transfer = transferRepository.findDetailed(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stock transfer was not found"));
        transfer.getLines().forEach(line -> line.getSerials().size());
        return transfer;
    }

    private void audit(String action, StockTransfer transfer) {
        auditRecorder.record(
                "INVENTORY",
                action,
                AuditOutcome.SUCCESS,
                "STOCK_TRANSFER",
                String.valueOf(transfer.getId()),
                transfer.getTransferNumber() + " " + transfer.getStatus().name());
    }

    private TransferResponse response(StockTransfer transfer, boolean includeLines) {
        List<TransferLineResponse> lines = includeLines
                ? transfer.getLines().stream().map(this::line).toList()
                : List.of();
        return new TransferResponse(
                transfer.getId(),
                transfer.getTransferNumber(),
                transfer.getTransferDate(),
                transfer.getFromLocation().getId(),
                transfer.getFromLocation().getLocationCode(),
                transfer.getFromLocation().getName(),
                transfer.getToLocation().getId(),
                transfer.getToLocation().getLocationCode(),
                transfer.getToLocation().getName(),
                transfer.getReason(),
                transfer.getNotes(),
                transfer.getStatus(),
                transfer.getCreatedBy(),
                transfer.getVersion(),
                lines);
    }

    private TransferLineResponse line(StockTransferLine line) {
        return new TransferLineResponse(
                line.getId(),
                line.getLineNumber(),
                line.getProduct().getId(),
                line.getProduct().getProductCode(),
                line.getProduct().getName(),
                line.getBatchCode(),
                line.getExpiryDate(),
                line.getQuantity(),
                line.getUnitCost(),
                line.getTotalValue(),
                line.serialNumbers());
    }

    private record PreparedLine(
            Product product,
            String batch,
            LocalDate expiry,
            BigDecimal quantity,
            List<String> serials,
            String key,
            int lineNumber) {
    }
}
