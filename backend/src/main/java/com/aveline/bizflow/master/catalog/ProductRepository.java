package com.aveline.bizflow.master.catalog;

import com.aveline.bizflow.master.RecordStatus;
import com.aveline.bizflow.master.StockType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query(
            value = """
                    select p from Product p
                    where (:status is null or p.status = :status)
                      and (:stockType is null or p.stockType = :stockType)
                      and (:categoryId is null or p.category.id = :categoryId)
                      and (:brandId is null or p.brand.id = :brandId)
                      and (
                        :search is null
                        or lower(p.productCode) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(p.name) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(p.shortName) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(p.barcode) like lower(concat('%', :search, '%')) escape '\\'
                      )
                    """,
            countQuery = """
                    select count(p) from Product p
                    where (:status is null or p.status = :status)
                      and (:stockType is null or p.stockType = :stockType)
                      and (:categoryId is null or p.category.id = :categoryId)
                      and (:brandId is null or p.brand.id = :brandId)
                      and (
                        :search is null
                        or lower(p.productCode) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(p.name) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(p.shortName) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(p.barcode) like lower(concat('%', :search, '%')) escape '\\'
                      )
                    """
    )
    Page<Product> search(
            @Param("search") String search,
            @Param("status") RecordStatus status,
            @Param("stockType") StockType stockType,
            @Param("categoryId") Long categoryId,
            @Param("brandId") Long brandId,
            Pageable pageable);

    @Query("""
            select p from Product p
            join fetch p.category
            join fetch p.brand
            join fetch p.unit
            where p.id in :ids
            """)
    List<Product> findDetailedByIdIn(@Param("ids") Collection<Long> ids);

    @Query("""
            select p from Product p
            join fetch p.category
            join fetch p.brand
            join fetch p.unit
            where p.id = :id
            """)
    Optional<Product> findDetailedById(@Param("id") Long id);

    Optional<Product> findByBarcode(String barcode);

    long countByCategory_Id(Long categoryId);

    long countByBrand_Id(Long brandId);

    long countByUnit_Id(Long unitId);

    @Query(value = "select count(*) from products where is_deleted = 0 and product_code = :code and id <> :id", nativeQuery = true)
    long countLiveCode(@Param("code") String code, @Param("id") long id);

    @Query(value = "select count(*) from products where is_deleted = 0 and barcode = :barcode and id <> :id", nativeQuery = true)
    long countLiveBarcode(@Param("barcode") String barcode, @Param("id") long id);

    @Query(value = "select product_id from product_barcode where is_deleted = 0 and barcode = :barcode", nativeQuery = true)
    List<Number> findAliasProductIds(@Param("barcode") String barcode);

    @Query(value = "select count(*) from product_barcode where is_deleted = 0 and barcode = :barcode and product_id <> :id", nativeQuery = true)
    long countAliasBarcode(@Param("barcode") String barcode, @Param("id") long id);

    @Query(value = "select product_id from product_variant where is_deleted = 0 and barcode = :barcode", nativeQuery = true)
    List<Number> findVariantProductIds(@Param("barcode") String barcode);

    @Query(value = "select count(*) from product_variant where is_deleted = 0 and barcode = :barcode and product_id <> :id", nativeQuery = true)
    long countVariantBarcode(@Param("barcode") String barcode, @Param("id") long id);
}
