package com.aveline.bizflow.identity;

import com.aveline.bizflow.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.util.HashSet;
import java.util.Set;

@Getter
@Entity
@SQLRestriction("is_deleted = 0")
@Table(
        name = "roles",
        uniqueConstraints = @UniqueConstraint(name = "uk_roles_code", columnNames = "code")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppRole extends AuditableEntity {

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "system_role", nullable = false, columnDefinition = "bit(1)")
    private boolean systemRole;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<AppPermission> permissions = new HashSet<>();

    public static AppRole create(String code, String name, String description) {
        AppRole role = new AppRole();
        role.code = code;
        role.name = name;
        role.description = description;
        role.systemRole = false;
        return role;
    }

    public void rename(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void replacePermissions(Set<AppPermission> assigned) {
        this.permissions.clear();
        this.permissions.addAll(assigned);
    }

    public void retire() {
        markDeleted();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AppRole role)) {
            return false;
        }
        return getId() != null && getId().equals(role.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
