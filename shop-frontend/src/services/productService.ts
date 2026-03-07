import { CreateProductRequest, ProductPage } from '@/types/product';

export async function getProducts(page = 0, size = 20): Promise<ProductPage> {
    const res = await fetch(`/product-api/products?page=${page}&size=${size}&productStatus=ACTIVE`, {
        cache: 'no-store',
    });
    if (!res.ok) throw new Error('상품 목록 조회 실패');
    return res.json();
}

export async function createProduct(body: CreateProductRequest): Promise<{ id: string }> {
    const res = await fetch('/product-api/products', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    });
    if (!res.ok) throw new Error('상품 등록 실패');
    return res.json();
}
