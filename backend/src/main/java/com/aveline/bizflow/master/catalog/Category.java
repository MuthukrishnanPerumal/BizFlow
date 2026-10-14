package com.aveline.bizflow.master.catalog;

import com.aveline.bizflow.common.persistence.AuditableEntity;
import com.aveline.bizflow.master.RecordStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Entity
@Table(name = "categories")
@SQLRestriction("is_deleted = 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends AuditableEntity {

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RecordStatus status;

    public static Category create(String code, String name, String description, RecordStatus status) {
        Category category = new Category();
        category.code = code;
        category.name = name;
        category.description = description;
        category.status = status;
        return category;
    }

    public void update(String code, String name, String description, RecordStatus status) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.status = status;
    }

    public void retire() {
        markDeleted();
    }
}
