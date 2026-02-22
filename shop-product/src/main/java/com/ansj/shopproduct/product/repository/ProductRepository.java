package com.ansj.shopproduct.product.repository;

import com.ansj.shopproduct.product.entity.ProductEntity;
import com.ansj.shopproduct.product.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {
    List<ProductEntity> findByProductStatus(ProductStatus productStatus);
    Page<ProductEntity> findByProductStatusAndDeletedAtIsNull(ProductStatus productStatus, Pageable pageable);
}
