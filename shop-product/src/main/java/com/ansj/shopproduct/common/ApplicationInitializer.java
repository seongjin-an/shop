package com.ansj.shopproduct.common;

import com.ansj.shopproduct.product.dto.CreateProductDto;
import com.ansj.shopproduct.usecase.ProductStockUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@RequiredArgsConstructor
@Component
public class ApplicationInitializer implements ApplicationRunner {

    private final ProductStockUseCase productStockUseCase;


    @Override
    public void run(ApplicationArguments args) throws Exception {
        initProduct();
    }

    private void initProduct() {
        CreateProductDto product = CreateProductDto.builder()
                .productName("비타민A")
                .productPrice(new BigDecimal(50000))
                .productDesc("비타민A 상품")
                .build();
        productStockUseCase.createProductWithStock(product, 10000);
    }
}
