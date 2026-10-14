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
import com.aveline.bizflow.master.dto.CatalogDtos.UnitRequest;
import com.aveline.bizflow.master.dto.CatalogDtos.UnitResponse;
import com.aveline.bizflow.master.dto.CatalogDtos.UnitUpdateRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@Transactional
public class UnitService {

    private static final Set<String> SORTS = Set.of("name", "shortName", "status", "createdAt");

    private final UnitRepository unitRepository;
    private final ProductRepository productRepository;
    private final AuditRecorder auditRecorder;

    public UnitService(UnitRepository unitRepository, ProductRepository productRepository, AuditRecorder auditRecorder) {
        this.unitRepository = unitRepository;
        this.productRepository = productRepository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public PageResponse<UnitResponse> list(String search, RecordStatus status, int page, int size, String sort) {
        Pageable pageable = PageRequests.of(page, size, sort, SORTS, "createdAt");
        Page<UnitOfMeasure> result = unitRepository.search(LikePatterns.contain(search), status, pageable);
        return new PageResponse<>(
                result.getContent().stream().map(CatalogMapper::toUnit).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public UnitResponse get(Long id) {
        return CatalogMapper.toUnit(load(id));
    }

    public UnitResponse create(UnitRequest request) {
        String name = MasterText.required(request.name(), 80, "name", "Name");
        String shortName = MasterText.shortName(request.shortName());
        assertAvailable(name, shortName, 0L);
        UnitOfMeasure unit = UnitOfMeasure.create(
                name,
                shortName,
                MasterText.optional(request.description(), 500, "description", "Description"),
                request.decimalAllowed(),
                request.status());
        save(unit);
        auditRecorder.record("UNIT_CREATED", "create", AuditOutcome.SUCCESS, "unit", String.valueOf(unit.getId()), shortName);
        return CatalogMapper.toUnit(unit);
    }

    public UnitResponse update(Long id, UnitUpdateRequest request) {
        UnitOfMeasure unit = load(id);
        MasterVersions.check(unit.getVersion(), request.version());
        String name = MasterText.required(request.name(), 80, "name", "Name");
        String shortName = MasterText.shortName(request.shortName());
        assertAvailable(name, shortName, unit.getId());
        if (unit.isDecimalAllowed() && !request.decimalAllowed() && productRepository.countByUnit_Id(unit.getId()) > 0) {
            throw new BusinessRuleException(
                    "UNIT_IN_USE",
                    "decimalAllowed",
                    "This unit is already used by products, so decimal quantities must stay allowed");
        }
        unit.update(
                name,
                shortName,
                MasterText.optional(request.description(), 500, "description", "Description"),
                request.decimalAllowed(),
                request.status());
        save(unit);
        auditRecorder.record("UNIT_UPDATED", "update", AuditOutcome.SUCCESS, "unit", String.valueOf(unit.getId()), shortName);
        return CatalogMapper.toUnit(unit);
    }

    public void delete(Long id) {
        UnitOfMeasure unit = load(id);
        if (productRepository.countByUnit_Id(id) > 0) {
            throw new BusinessRuleException(
                    "UNIT_IN_USE",
                    "This unit is used by products. Set it to inactive instead of deleting it.");
        }
        String shortName = unit.getShortName();
        unit.retire();
        save(unit);
        auditRecorder.record("UNIT_DELETED", "delete", AuditOutcome.SUCCESS, "unit", String.valueOf(id), shortName);
    }

    private UnitOfMeasure load(Long id) {
        return unitRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Unit was not found"));
    }

    private void assertAvailable(String name, String shortName, long excludingId) {
        if (unitRepository.countLiveShortName(shortName, excludingId) > 0) {
            throw new ConflictException("shortName", "A unit with this short name already exists");
        }
        if (unitRepository.countLiveName(name, excludingId) > 0) {
            throw new ConflictException("name", "A unit with this name already exists");
        }
    }

    private void save(UnitOfMeasure unit) {
        try {
            unitRepository.saveAndFlush(unit);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("A unit with this name or short name already exists");
        }
    }
}
