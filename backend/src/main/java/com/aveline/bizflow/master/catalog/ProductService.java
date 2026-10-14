package com.aveline.bizflow.master.catalog;

import com.aveline.bizflow.audit.AuditOutcome;
import com.aveline.bizflow.audit.AuditRecorder;
import com.aveline.bizflow.common.api.LikePatterns;
import com.aveline.bizflow.common.api.PageRequests;
import com.aveline.bizflow.common.api.PageResponse;
import com.aveline.bizflow.common.exception.BusinessRuleException;
import com.aveline.bizflow.common.exception.ConflictException;
import com.aveline.bizflow.common.exception.ResourceNotFoundException;
import com.aveline.bizflow.master.MasterText;
import com.aveline.bizflow.master.MasterVersions;
import com.aveline.bizflow.master.ProductRules;
import com.aveline.bizflow.master.RecordStatus;
import com.aveline.bizflow.master.StockType;
import com.aveline.bizflow.master.dto.CatalogDtos.ProductRequest;
import com.aveline.bizflow.master.dto.CatalogDtos.ProductResponse;
import com.aveline.bizflow.master.dto.CatalogDtos.ProductUpdateRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProductService {

    private static final Set<String> SORTS = Set.of("productCode", "name", "barcode", "sellingPrice", "status", "createdAt");

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final UnitRepository unitRepository;
    private final AuditRecorder auditRecorder;

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            BrandRepository brandRepository,
            UnitRepository unitRepository,
            AuditRecorder auditRecorder) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.unitRepository = unitRepository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(
            String search,
            RecordStatus status,
            StockType stockType,
            Long categoryId,
            Long brandId,
            int page,
            int size,
            String sort) {
        Pageable pageable = PageRequests.of(page, size, sort, SORTS, "createdAt");
        Page<Product> result = productRepository.search(
                LikePatterns.contain(search),
                status,
                stockType,
                categoryId,
                brandId,
                pageable);
        List<Long> ids = result.getContent().stream().map(Product::getId).toList();
        Map<Long, Product> detailed = ids.isEmpty()
                ? Map.of()
                : productRepository.findDetailedByIdIn(ids).stream()
                        .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<ProductResponse> items = ids.stream()
                .map(detailed::get)
                .filter(Objects::nonNull)
                .map(CatalogMapper::toProduct)
                .toList();
        return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return CatalogMapper.toProduct(load(id));
    }

    @Transactional(readOnly = true)
    public ProductResponse lookupBarcode(String raw) {
        String barcode = MasterText.barcode(raw);
        if (barcode == null) {
            throw new BusinessRuleException("INVALID_BARCODE", "barcode", "Enter a barcode");
        }
        Product direct = productRepository.findByBarcode(barcode).orElse(null);
        if (direct != null) {
            return CatalogMapper.toProduct(load(direct.getId()));
        }
        Long aliasId = firstId(productRepository.findAliasProductIds(barcode));
        if (aliasId == null) {
            aliasId = firstId(productRepository.findVariantProductIds(barcode));
        }
        if (aliasId == null) {
            throw new ResourceNotFoundException("No product uses this barcode");
        }
        return CatalogMapper.toProduct(load(aliasId));
    }

    public ProductResponse create(ProductRequest request) {
        Draft draft = draft(request, null);
        Product product = Product.create(
                draft.productCode(),
                draft.barcode(),
                draft.name(),
                draft.shortName(),
                draft.description(),
                draft.category(),
                draft.brand(),
                draft.unit(),
                draft.taxPercent(),
                draft.purchasePrice(),
                draft.sellingPrice(),
                draft.mrp(),
                draft.minimumSellingPrice(),
                draft.reorderLevel(),
                draft.maximumStockLevel(),
                draft.stockType(),
                draft.batchTracking(),
                draft.serialTracking(),
                draft.expiryTracking(),
                draft.status());
        save(product);
        auditRecorder.record(
                "PRODUCT_CREATED",
                "create",
                AuditOutcome.SUCCESS,
                "product",
                String.valueOf(product.getId()),
                product.getProductCode());
        return CatalogMapper.toProduct(load(product.getId()));
    }

    public ProductResponse update(Long id, ProductUpdateRequest request) {
        Product product = load(id);
        MasterVersions.check(product.getVersion(), request.version());
        Draft draft = draft(request, product);
        product.update(
                draft.productCode(),
                draft.barcode(),
                draft.name(),
                draft.shortName(),
                draft.description(),
                draft.category(),
                draft.brand(),
                draft.unit(),
                draft.taxPercent(),
                draft.purchasePrice(),
                draft.sellingPrice(),
                draft.mrp(),
                draft.minimumSellingPrice(),
                draft.reorderLevel(),
                draft.maximumStockLevel(),
                draft.stockType(),
                draft.batchTracking(),
                draft.serialTracking(),
                draft.expiryTracking(),
                draft.status());
        save(product);
        auditRecorder.record(
                "PRODUCT_UPDATED",
                "update",
                AuditOutcome.SUCCESS,
                "product",
                String.valueOf(product.getId()),
                product.getProductCode());
        return CatalogMapper.toProduct(load(product.getId()));
    }

    public void delete(Long id) {
        Product product = load(id);
        String code = product.getProductCode();
        product.retire();
        save(product);
        auditRecorder.record("PRODUCT_DELETED", "delete", AuditOutcome.SUCCESS, "product", String.valueOf(id), code);
    }

    private Draft draft(ProductRequest request, Product current) {
        return build(
                request.productCode(),
                request.barcode(),
                request.name(),
                request.shortName(),
                request.description(),
                request.categoryId(),
                request.brandId(),
                request.unitId(),
                request.taxPercent(),
                request.purchasePrice(),
                request.sellingPrice(),
                request.mrp(),
                request.minimumSellingPrice(),
                request.reorderLevel(),
                request.maximumStockLevel(),
                request.stockType(),
                request.batchTracking(),
                request.serialTracking(),
                request.expiryTracking(),
                request.status(),
                current);
    }

    private Draft draft(ProductUpdateRequest request, Product current) {
        return build(
                request.productCode(),
                request.barcode(),
                request.name(),
                request.shortName(),
                request.description(),
                request.categoryId(),
                request.brandId(),
                request.unitId(),
                request.taxPercent(),
                request.purchasePrice(),
                request.sellingPrice(),
                request.mrp(),
                request.minimumSellingPrice(),
                request.reorderLevel(),
                request.maximumStockLevel(),
                request.stockType(),
                request.batchTracking(),
                request.serialTracking(),
                request.expiryTracking(),
                request.status(),
                current);
    }

    private Draft build(
            String productCode,
            String barcode,
            String name,
            String shortName,
            String description,
            Long categoryId,
            Long brandId,
            Long unitId,
            BigDecimal taxPercent,
            BigDecimal purchasePrice,
            BigDecimal sellingPrice,
            BigDecimal mrp,
            BigDecimal minimumSellingPrice,
            BigDecimal reorderLevel,
            BigDecimal maximumStockLevel,
            StockType stockType,
            Boolean batchTracking,
            Boolean serialTracking,
            Boolean expiryTracking,
            RecordStatus status,
            Product current) {
        long excludingId = current == null ? 0L : current.getId();
        String code = MasterText.productCode(productCode);
        String normalizedBarcode = MasterText.barcode(barcode);
        assertCodeAvailable(code, excludingId);
        assertBarcodeAvailable(normalizedBarcode, excludingId);
        Category category = requireCategory(categoryId, current == null ? null : current.getCategory().getId());
        Brand brand = requireBrand(brandId, current == null ? null : current.getBrand().getId());
        UnitOfMeasure unit = requireUnit(unitId, current == null ? null : current.getUnit().getId());
        BigDecimal tax = MasterText.taxPercent(taxPercent);
        BigDecimal purchase = MasterText.money(purchasePrice, "purchasePrice", "Purchase price", true);
        BigDecimal selling = MasterText.money(sellingPrice, "sellingPrice", "Selling price", true);
        BigDecimal retail = MasterText.money(mrp, "mrp", "MRP", true);
        BigDecimal minimum = MasterText.money(minimumSellingPrice, "minimumSellingPrice", "Minimum selling price", true);
        BigDecimal reorder = MasterText.money(reorderLevel, "reorderLevel", "Reorder level", true);
        BigDecimal maximum = MasterText.money(maximumStockLevel, "maximumStockLevel", "Maximum stock", false);
        ProductRules.prices(purchase, selling, retail, minimum);
        ProductRules.stockLevels(reorder, maximum, unit.isDecimalAllowed());
        ProductRules.tracking(stockType, batchTracking, serialTracking, expiryTracking);
        return new Draft(
                code,
                normalizedBarcode,
                MasterText.required(name, 200, "name", "Name"),
                MasterText.optional(shortName, 80, "shortName", "Short name"),
                MasterText.optional(description, 2000, "description", "Description"),
                category,
                brand,
                unit,
                tax,
                purchase,
                selling,
                retail,
                minimum,
                reorder,
                maximum,
                stockType,
                batchTracking,
                serialTracking,
                expiryTracking,
                status);
    }

    private Product load(Long id) {
        return productRepository.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product was not found"));
    }

    private void assertCodeAvailable(String code, long excludingId) {
        if (productRepository.countLiveCode(code, excludingId) > 0) {
            throw new ConflictException("productCode", "A product with this code already exists");
        }
    }

    private void assertBarcodeAvailable(String barcode, long excludingId) {
        if (barcode == null) {
            return;
        }
        if (productRepository.countLiveBarcode(barcode, excludingId) > 0
                || productRepository.countAliasBarcode(barcode, excludingId) > 0
                || productRepository.countVariantBarcode(barcode, excludingId) > 0) {
            throw new ConflictException("barcode", "A product with this barcode already exists");
        }
    }

    private Category requireCategory(Long id, Long currentId) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("UNKNOWN_CATEGORY", "categoryId", "Select a category that exists"));
        if (category.getStatus() != RecordStatus.ACTIVE && !id.equals(currentId)) {
            throw new BusinessRuleException("INACTIVE_CATEGORY", "categoryId", "Select an active category");
        }
        return category;
    }

    private Brand requireBrand(Long id, Long currentId) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("UNKNOWN_BRAND", "brandId", "Select a brand that exists"));
        if (brand.getStatus() != RecordStatus.ACTIVE && !id.equals(currentId)) {
            throw new BusinessRuleException("INACTIVE_BRAND", "brandId", "Select an active brand");
        }
        return brand;
    }

    private UnitOfMeasure requireUnit(Long id, Long currentId) {
        UnitOfMeasure unit = unitRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("UNKNOWN_UNIT", "unitId", "Select a unit that exists"));
        if (unit.getStatus() != RecordStatus.ACTIVE && !id.equals(currentId)) {
            throw new BusinessRuleException("INACTIVE_UNIT", "unitId", "Select an active unit");
        }
        return unit;
    }

    private void save(Product product) {
        try {
            productRepository.saveAndFlush(product);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("The product code or barcode is already in use");
        }
    }

    private Long firstId(List<Number> ids) {
        if (ids == null || ids.isEmpty() || ids.get(0) == null) {
            return null;
        }
        return ids.get(0).longValue();
    }

    private record Draft(
            String productCode,
            String barcode,
            String name,
            String shortName,
            String description,
            Category category,
            Brand brand,
            UnitOfMeasure unit,
            BigDecimal taxPercent,
            BigDecimal purchasePrice,
            BigDecimal sellingPrice,
            BigDecimal mrp,
            BigDecimal minimumSellingPrice,
            BigDecimal reorderLevel,
            BigDecimal maximumStockLevel,
            StockType stockType,
            boolean batchTracking,
            boolean serialTracking,
            boolean expiryTracking,
            RecordStatus status
    ) {
    }
}
