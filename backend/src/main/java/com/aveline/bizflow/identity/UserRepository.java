package com.aveline.bizflow.identity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByEmail(String email);

    @Query("""
            select distinct u from AppUser u
            left join fetch u.roles r
            left join fetch r.permissions
            where u.username = :username
            """)
    Optional<AppUser> findWithAccessByUsername(@Param("username") String username);

    @Query("""
            select distinct u from AppUser u
            left join fetch u.roles r
            left join fetch r.permissions
            where u.id = :id
            """)
    Optional<AppUser> findWithAccessById(@Param("id") Long id);

    @Query("""
            select distinct u from AppUser u
            left join fetch u.roles
            where u.id in :ids
            """)
    List<AppUser> findWithRolesByIdIn(@Param("ids") Collection<Long> ids);

    @Query(
            value = """
                    select u from AppUser u
                    where (:status is null or u.status = :status)
                      and (
                        :search is null
                        or lower(u.username) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(u.fullName) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(u.email) like lower(concat('%', :search, '%')) escape '\\'
                      )
                    """,
            countQuery = """
                    select count(u) from AppUser u
                    where (:status is null or u.status = :status)
                      and (
                        :search is null
                        or lower(u.username) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(u.fullName) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(u.email) like lower(concat('%', :search, '%')) escape '\\'
                      )
                    """
    )
    Page<AppUser> search(@Param("search") String search, @Param("status") UserStatus status, Pageable pageable);

    @Query("""
            select count(distinct u) from AppUser u
            join u.roles r
            where r.code = :code and u.status = com.aveline.bizflow.identity.UserStatus.ACTIVE
            """)
    long countActiveWithRole(@Param("code") String code);

    @Query("select count(distinct u) from AppUser u join u.roles r where r.id = :roleId")
    long countUsersWithRole(@Param("roleId") Long roleId);

    @Query(value = "select count(*) from users where username = :username", nativeQuery = true)
    long countUsernameIncludingDeleted(@Param("username") String username);

    @Query(value = "select count(*) from users where email = :email and id <> :id", nativeQuery = true)
    long countEmailExcluding(@Param("email") String email, @Param("id") long id);
}
