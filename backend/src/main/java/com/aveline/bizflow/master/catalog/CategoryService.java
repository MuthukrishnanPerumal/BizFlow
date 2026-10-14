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
import com.aveline.bizflow.master.dto.CatalogDtos.CategoryRequest;
import com.aveline.bizflow.master.dto.CatalogDtos.CategoryResponse;
import com.aveline.bizflow.master.dto.CatalogDtos.CategoryUpdateRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@Transactional
public class CategoryService {

    private static final Set<String> SORTS = Set.of("code", "name", "status", "createdAt");

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final AuditRecorder auditRecorder;

    public CategoryService(
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            AuditRecorder auditRecorder) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> list(String search, RecordStatus status, int page, int size, String sort) {
        Pageable pageable = PageRequests.of(page, size, sort, SORTS, "createdAt");
        Page<Category> result = categoryRepository.search(LikePatterns.contain(search), status, pageable);
        return new PageResponse<>(
                result.getContent().stream().map(CatalogMapper::toCategory).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(Long id) {
        return CatalogMapper.toCategory(load(id));
    }

    public CategoryResponse create(CategoryRequest request) {
        String code = MasterText.code(request.code(), "code");
        String name = MasterText.required(request.name(), 160, "name", "Name");
        assertAvailable(code, name, 0L);
        Category category = Category.create(code, name, MasterText.optional(request.description(), 1000, "description", "Description"), request.status());
        save(category);
        auditRecorder.record("CATEGORY_CREATED", "create", AuditOutcome.SUCCESS, "category", String.valueOf(category.getId()), code);
        return CatalogMapper.toCategory(category);
    }

    public CategoryResponse update(Long id, CategoryUpdateRequest request) {
        Category category = load(id);
        MasterVersions.check(category.getVersion(), request.version());
        String code = MasterText.code(request.code(), "code");
        String name = MasterText.required(request.name(), 160, "name", "Name");
        assertAvailable(code, name, category.getId());
        category.update(code, name, MasterText.optional(request.description(), 1000, "description", "Description"), request.status());
        save(category);
        auditRecorder.record("CATEGORY_UPDATED", "update", AuditOutcome.SUCCESS, "category", String.valueOf(category.getId()), code);
        return CatalogMapper.toCategory(category);
    }

    public void delete(Long id) {
        Category category = load(id);
        if (productRepository.countByCategory_Id(id) > 0) {
            throw new BusinessRuleException(
                    "CATEGORY_IN_USE",
                    "This category is used by products. Set it to inactive instead of deleting it.");
        }
        String code = category.getCode();
        category.retire();
        save(category);
        auditRecorder.record("CATEGORY_DELETED", "delete", AuditOutcome.SUCCESS, "category", String.valueOf(id), code);
    }

    private Category load(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category was not found"));
    }

    private void assertAvailable(String code, String name, long excludingId) {
        if (categoryRepository.countLiveCode(code, excludingId) > 0) {
            throw new ConflictException("code", "A category with this code already exists");
        }
        if (categoryRepository.countLiveName(name, excludingId) > 0) {
            throw new ConflictException("name", "A category with this name already exists");
        }
    }

    private void save(Category category) {
        try {
            categoryRepository.saveAndFlush(category);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("A category with this code or name already exists");
        }
    }
}
