package com.ansj.shopproduct.product.controller;

import com.ansj.shopproduct.common.AggregateId;
import com.ansj.shopproduct.product.dto.CreateProductDto;
import com.ansj.shopproduct.product.entity.ProductStatus;
import com.ansj.shopproduct.product.model.Product;
import com.ansj.shopproduct.product.service.ProductService;
import com.ansj.shopproduct.usecase.ProductStockUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RequestMapping("/api/products")
@RestController
public class ProductController {

    private final ProductService productService;
    private final ProductStockUseCase productStockUseCase;

    @GetMapping
    public Page<Product> getProducts(@RequestParam(defaultValue = "ACTIVE") ProductStatus productStatus,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return productService.getProducts(productStatus, page, size);
    }

    @PostMapping
    public ResponseEntity<AggregateId> createProduct(@RequestBody CreateProductDto dto) {
        AggregateId result = productStockUseCase.createProductWithStock(dto);
        return ResponseEntity.ok(result);
    }
}
