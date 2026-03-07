import { CreateOrderRequest, Order } from '@/types/order';

export async function createOrder(body: CreateOrderRequest): Promise<string> {
    const res = await fetch('/order-api/orders', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    });
    if (!res.ok) throw new Error('주문 생성 실패');
    // 201 Created, Location 헤더에서 orderId 추출
    const location = res.headers.get('Location') ?? '';
    return location.split('/').pop() ?? '';
}

export async function getOrder(orderId: string): Promise<Order> {
    const res = await fetch(`/order-api/orders/${orderId}`, { cache: 'no-store' });
    if (!res.ok) throw new Error('주문 조회 실패');
    return res.json();
}
