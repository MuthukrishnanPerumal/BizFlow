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
import com.aveline.bizflow.master.RecordStatus;
import com.aveline.bizflow.master.dto.CatalogDtos.BrandRequest;
import com.aveline.bizflow.master.dto.CatalogDtos.BrandResponse;
import com.aveline.bizflow.master.dto.CatalogDtos.BrandUpdateRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@Transactional
public class BrandService {

    private static final Set<String> SORTS = Set.of("code", "name", "status", "createdAt");

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final AuditRecorder auditRecorder;

    public BrandService(BrandRepository brandRepository, ProductRepository productRepository, AuditRecorder auditRecorder) {
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public PageResponse<BrandResponse> list(String search, RecordStatus status, int page, int size, String sort) {
        Pageable pageable = PageRequests.of(page, size, sort, SORTS, "createdAt");
        Page<Brand> result = brandRepository.search(LikePatterns.contain(search), status, pageable);
        return new PageResponse<>(
                result.getContent().stream().map(CatalogMapper::toBrand).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public BrandResponse get(Long id) {
        return CatalogMapper.toBrand(load(id));
    }

    public BrandResponse create(BrandRequest request) {
        String code = MasterText.code(request.code(), "code");
        String name = MasterText.required(request.name(), 160, "name", "Name");
        assertAvailable(code, name, 0L);
        Brand brand = Brand.create(code, name, MasterText.optional(request.description(), 1000, "description", "Description"), request.status());
        save(brand);
        auditRecorder.record("BRAND_CREATED", "create", AuditOutcome.SUCCESS, "brand", String.valueOf(brand.getId()), code);
        return CatalogMapper.toBrand(brand);
    }

    public BrandResponse update(Long id, BrandUpdateRequest request) {
        Brand brand = load(id);
        MasterVersions.check(brand.getVersion(), request.version());
        String code = MasterText.code(request.code(), "code");
        String name = MasterText.required(request.name(), 160, "name", "Name");
        assertAvailable(code, name, brand.getId());
        brand.update(code, name, MasterText.optional(request.description(), 1000, "description", "Description"), request.status());
        save(brand);
        auditRecorder.record("BRAND_UPDATED", "update", AuditOutcome.SUCCESS, "brand", String.valueOf(brand.getId()), code);
        return CatalogMapper.toBrand(brand);
    }

    public void delete(Long id) {
        Brand brand = load(id);
        if (productRepository.countByBrand_Id(id) > 0) {
            throw new BusinessRuleException(
                    "BRAND_IN_USE",
                    "This brand is used by products. Set it to inactive instead of deleting it.");
        }
        String code = brand.getCode();
        brand.retire();
        save(brand);
        auditRecorder.record("BRAND_DELETED", "delete", AuditOutcome.SUCCESS, "brand", String.valueOf(id), code);
    }

    private Brand load(Long id) {
        return brandRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Brand was not found"));
    }

    private void assertAvailable(String code, String name, long excludingId) {
        if (brandRepository.countLiveCode(code, excludingId) > 0) {
            throw new ConflictException("code", "A brand with this code already exists");
        }
        if (brandRepository.countLiveName(name, excludingId) > 0) {
            throw new ConflictException("name", "A brand with this name already exists");
        }
    }

    private void save(Brand brand) {
        try {
            brandRepository.saveAndFlush(brand);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("A brand with this code or name already exists");
        }
    }
}
