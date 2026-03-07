export type OrderStatus =
    | 'PENDING'
    | 'STOCK_RESERVED'
    | 'COMPLETED'
    | 'STOCK_FAILED'
    | 'PAYMENT_FAILED'
    | 'CANCELLED';

export interface OrderItem {
    productId: string;
    productName: string;
    unitPrice: number;
    quantity: number;
    subtotal: number;
}

export interface Order {
    orderId: string;
    sagaId: string;
    userId: string;
    orderStatus: OrderStatus;
    totalAmount: number;
    deliveryAddress: string;
    items: OrderItem[];
    createdAt: string;
    updatedAt: string;
}

export interface CreateOrderItemRequest {
    productId: string;
    productName: string;
    unitPrice: number;
    quantity: number;
}

export interface CreateOrderRequest {
    userId: string;
    deliveryAddress: string;
    items: CreateOrderItemRequest[];
}
