export interface Product {
    productId: string;
    productName: string;
    productDesc: string;
    productPrice: number;
    productStatus: 'ACTIVE' | 'INACTIVE' | 'DELETED';
}

export interface ProductPage {
    content: Product[];
    totalElements: number;
    totalPages: number;
    number: number;
}

export interface CreateProductRequest {
    productName: string;
    productDesc: string;
    productPrice: number;
    quantity: number;
}
