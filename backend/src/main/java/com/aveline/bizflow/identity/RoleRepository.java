package com.aveline.bizflow.identity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<AppRole, Long> {

    Optional<AppRole> findByCode(String code);

    @Query("""
            select distinct r from AppRole r
            left join fetch r.permissions
            where r.id = :id
            """)
    Optional<AppRole> findWithPermissionsById(@Param("id") Long id);

    @Query("""
            select distinct r from AppRole r
            left join fetch r.permissions
            where r.id in :ids
            """)
    List<AppRole> findWithPermissionsByIdIn(@Param("ids") Collection<Long> ids);

    @Query(
            value = """
                    select r from AppRole r
                    where :search is null
                       or lower(r.code) like lower(concat('%', :search, '%')) escape '\\'
                       or lower(r.name) like lower(concat('%', :search, '%')) escape '\\'
                    """,
            countQuery = """
                    select count(r) from AppRole r
                    where :search is null
                       or lower(r.code) like lower(concat('%', :search, '%')) escape '\\'
                       or lower(r.name) like lower(concat('%', :search, '%')) escape '\\'
                    """
    )
    Page<AppRole> search(@Param("search") String search, Pageable pageable);

    @Query(value = "select count(*) from roles where code = :code", nativeQuery = true)
    long countCodeIncludingDeleted(@Param("code") String code);
}
