package com.ansj.shopproduct.product.service;

import com.ansj.shopproduct.product.dto.CreateProductDto;
import com.ansj.shopproduct.product.entity.ProductEntity;
import com.ansj.shopproduct.product.entity.ProductStatus;
import com.ansj.shopproduct.product.model.Product;
import com.ansj.shopproduct.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public Long createProduct(CreateProductDto dto) {
        ProductEntity productEntity = dto.toEntity();
        return productRepository.save(productEntity).getProductId();
    }

    public Product getProduct(Long productId) {
        ProductEntity productEntity = getProductById(productId);
        return productEntity.toModel();
    }
    /* 검색은 나중에 CQRS 로 분리해보든가 하자. 우선은 상태로만 검색한다. */
    public Page<Product> getProducts(ProductStatus productStatus, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ProductEntity> productEntities = productRepository.findByProductStatusAndDeletedAtIsNull(productStatus, pageable);
        return productEntities.map(ProductEntity::toModel);
    }

    public boolean isOrderable(Long productId) {
        ProductEntity productEntity = getProductById(productId);
        return productEntity.getProductStatus() == ProductStatus.ACTIVE;
    }


    @Transactional
    public void changePrice(Long productId, BigDecimal newPrice) {
        ProductEntity productEntity = getProductById(productId);
        productEntity.changePrice(newPrice);
    }

    @Transactional
    public void deactivateProduct(Long productId) {
        ProductEntity productEntity = getProductById(productId);
        productEntity.deactivate();
    }

    @Transactional
    public void activateProduct(Long productId) {
        ProductEntity productEntity = getProductById(productId);
        productEntity.activate();
    }

    @Transactional
    public void deleteProduct(Long productId) {
        ProductEntity productEntity = getProductById(productId);
        productEntity.softDelete();
    }

    private ProductEntity getProductById(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품이 존재하지 않습니다."));
    }
}
