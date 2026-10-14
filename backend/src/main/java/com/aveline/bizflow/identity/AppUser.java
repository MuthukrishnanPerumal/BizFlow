package com.aveline.bizflow.identity;

import com.aveline.bizflow.common.persistence.AuditableEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Getter
@Entity
@SQLRestriction("is_deleted = 0")
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_username", columnNames = "username"),
                @UniqueConstraint(name = "uk_users_email", columnNames = "email")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser extends AuditableEntity {

    @Column(nullable = false, length = 64)
    private String username;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(length = 32)
    private String phone;

    @JsonIgnore
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserStatus status;

    @Column(name = "last_login_at", columnDefinition = "datetime(6)")
    private Instant lastLoginAt;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "session_version", nullable = false)
    private int sessionVersion;

    @Column(name = "must_change_password", nullable = false, columnDefinition = "bit(1)")
    private boolean mustChangePassword;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<AppRole> roles = new HashSet<>();

    public static AppUser create(
            String username,
            String fullName,
            String email,
            String phone,
            String passwordHash,
            UserStatus status) {
        AppUser user = new AppUser();
        user.username = username;
        user.fullName = fullName;
        user.email = email;
        user.phone = phone;
        user.passwordHash = passwordHash;
        user.status = status;
        user.failedLoginCount = 0;
        user.sessionVersion = 0;
        user.mustChangePassword = true;
        return user;
    }

    public void updateProfile(String fullName, String email, String phone, UserStatus status) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.status = status;
    }

    public void replaceRoles(Set<AppRole> assigned) {
        this.roles.clear();
        this.roles.addAll(assigned);
    }

    public void recordSuccessfulLogin(Instant when) {
        this.lastLoginAt = when;
        this.failedLoginCount = 0;
    }

    public boolean registerFailedLogin(int threshold) {
        this.failedLoginCount++;
        if (this.failedLoginCount >= threshold) {
            this.status = UserStatus.LOCKED;
            return true;
        }
        return false;
    }

    public void changePassword(String passwordHash, boolean mustChangePassword) {
        this.passwordHash = passwordHash;
        this.mustChangePassword = mustChangePassword;
        this.failedLoginCount = 0;
        this.sessionVersion++;
    }

    public void retire() {
        markDeleted();
        this.sessionVersion++;
    }
}
