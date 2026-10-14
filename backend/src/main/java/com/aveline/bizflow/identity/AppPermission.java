package com.aveline.bizflow.identity;

import com.aveline.bizflow.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Entity
@SQLRestriction("is_deleted = 0")
@Table(
        name = "permissions",
        uniqueConstraints = @UniqueConstraint(name = "uk_permissions_code", columnNames = "code")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppPermission extends AuditableEntity {

    @Column(nullable = false, length = 64)
    private String code;

    @Column(name = "module_code", nullable = false, length = 64)
    private String moduleCode;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AppPermission permission)) {
            return false;
        }
        return getId() != null && getId().equals(permission.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
