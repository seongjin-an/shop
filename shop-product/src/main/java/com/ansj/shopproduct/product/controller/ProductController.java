package com.ansj.shopproduct.product.controller;

import com.ansj.shopproduct.product.entity.ProductStatus;
import com.ansj.shopproduct.product.model.Product;
import com.ansj.shopproduct.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/products")
@RestController
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public Page<Product> getProducts(@RequestParam(defaultValue = "ACTIVE") ProductStatus productStatus,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return productService.getProducts(productStatus, page, size);
    }
}
