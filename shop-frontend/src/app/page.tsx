"use client";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/store/useAuthStore";
import { requestUserInfo } from "@/services/api";
import { getProducts } from "@/services/productService";
import { createOrder } from "@/services/orderService";
import { Product } from "@/types/product";
import { CreateOrderItemRequest } from "@/types/order";

interface OrderModal {
    product: Product;
    quantity: number;
    address: string;
}

export default function Home() {
    const router = useRouter();
    const { isLoggedIn, user, initUser } = useAuthStore();
    const [products, setProducts] = useState<Product[]>([]);
    const [loading, setLoading] = useState(true);
    const [modal, setModal] = useState<OrderModal | null>(null);
    const [ordering, setOrdering] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (isLoggedIn || user) {
            fetchProducts();
            return;
        }
        requestUserInfo().then((r) => {
            if (!r) { router.push("/login"); return; }
            initUser(r);
            fetchProducts();
        }).catch(() => router.push("/login"));
    }, []);

    async function fetchProducts() {
        try {
            const page = await getProducts();
            setProducts(page.content);
        } catch (e) {
            setError("상품 목록을 불러오지 못했습니다.");
        } finally {
            setLoading(false);
        }
    }

    async function handleOrder() {
        if (!modal) return;
        setOrdering(true);
        setError(null);
        try {
            const item: CreateOrderItemRequest = {
                productId: modal.product.productId,
                productName: modal.product.productName,
                unitPrice: modal.product.productPrice,
                quantity: modal.quantity,
            };
            const orderId = await createOrder({
                userId: user?.userId || 0,
                deliveryAddress: modal.address,
                items: [item],
            });
            setModal(null);
            router.push(`/orders/${orderId}`);
        } catch (e) {
            setError("주문 생성에 실패했습니다.");
        } finally {
            setOrdering(false);
        }
    }

    async function handleLogout() {
        await fetch("/api/logout", { method: "POST", credentials: "include" });
        router.push("/login");
    }

    return (
        <>
            <style>{`
                .header { display:flex; justify-content:space-between; align-items:center; padding:16px 32px; background:#fff; border-bottom:1px solid #e5e7eb; width:100%; }
                .header-left { display:flex; align-items:center; gap:24px; }
                .logo { font-size:20px; font-weight:700; color:#111; }
                .nav-link { color:#555; font-size:14px; cursor:pointer; text-decoration:none; }
                .nav-link:hover { color:#111; }
                .header-right { display:flex; align-items:center; gap:16px; }
                .user-name { font-size:14px; color:#555; }
                .btn { padding:8px 16px; border-radius:6px; font-size:14px; cursor:pointer; border:none; }
                .btn-outline { background:#fff; border:1px solid #d1d5db; color:#374151; }
                .btn-outline:hover { background:#f9fafb; }
                .btn-primary { background:#111; color:#fff; }
                .btn-primary:hover { background:#333; }
                .btn-sm { padding:6px 12px; font-size:13px; }
                .container { max-width:1100px; margin:0 auto; padding:32px 24px; width:100%; }
                .page-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:24px; }
                .page-title { font-size:22px; font-weight:700; color:#111; }
                .grid { display:grid; grid-template-columns:repeat(auto-fill, minmax(240px, 1fr)); gap:20px; }
                .card { background:#fff; border:1px solid #e5e7eb; border-radius:10px; padding:20px; display:flex; flex-direction:column; gap:10px; }
                .card-name { font-size:16px; font-weight:600; color:#111; }
                .card-desc { font-size:13px; color:#6b7280; flex:1; }
                .card-price { font-size:18px; font-weight:700; color:#111; }
                .empty { text-align:center; color:#9ca3af; padding:80px 0; }
                .overlay { position:fixed; inset:0; background:rgba(0,0,0,.4); display:flex; align-items:center; justify-content:center; z-index:100; }
                .modal { background:#fff; border-radius:12px; padding:32px; width:100%; max-width:440px; display:flex; flex-direction:column; gap:20px; }
                .modal-title { font-size:18px; font-weight:700; color:#111; }
                .field { display:flex; flex-direction:column; gap:6px; }
                .label { font-size:13px; font-weight:500; color:#374151; }
                .input { padding:10px 12px; border:1px solid #d1d5db; border-radius:6px; font-size:14px; outline:none; }
                .input:focus { border-color:#111; }
                .modal-actions { display:flex; gap:10px; justify-content:flex-end; }
                .error { color:#dc2626; font-size:13px; background:#fef2f2; border:1px solid #fecaca; border-radius:6px; padding:10px 14px; }
            `}</style>

            <header className="header">
                <div className="header-left">
                    <span className="logo">🛒 Shop</span>
                    <a className="nav-link" href="/products/create">+ 상품 등록</a>
                </div>
                <div className="header-right">
                    <span className="user-name">{user?.fullName}</span>
                    <button className="btn btn-outline btn-sm" onClick={handleLogout}>로그아웃</button>
                </div>
            </header>

            <div className="container">
                <div className="page-header">
                    <h2 className="page-title">상품 목록</h2>
                    <button className="btn btn-outline btn-sm" onClick={fetchProducts}>↻ 새로고침</button>
                </div>

                {error && <div className="error">{error}</div>}

                {loading ? (
                    <div className="empty">불러오는 중...</div>
                ) : products.length === 0 ? (
                    <div className="empty">
                        <p>등록된 상품이 없습니다.</p>
                        <a href="/products/create" className="nav-link" style={{color:'#111', fontWeight:600}}>상품 등록하기 →</a>
                    </div>
                ) : (
                    <div className="grid">
                        {products.map((p) => (
                            <div key={p.productId} className="card">
                                <div className="card-name">{p.productName}</div>
                                <div className="card-desc">{p.productDesc}</div>
                                <div className="card-price">₩{p.productPrice.toLocaleString()}</div>
                                <button
                                    className="btn btn-primary btn-sm"
                                    onClick={() => setModal({ product: p, quantity: 1, address: "" })}
                                >
                                    주문하기
                                </button>
                            </div>
                        ))}
                    </div>
                )}
            </div>

            {modal && (
                <div className="overlay" onClick={(e) => e.target === e.currentTarget && setModal(null)}>
                    <div className="modal">
                        <div className="modal-title">주문하기</div>
                        <div style={{fontSize:14, color:'#555'}}>
                            <strong>{modal.product.productName}</strong> — ₩{modal.product.productPrice.toLocaleString()}
                        </div>

                        <div className="field">
                            <label className="label">수량</label>
                            <input
                                type="number" min={1} className="input"
                                value={modal.quantity}
                                onChange={(e) => setModal({ ...modal, quantity: Math.max(1, Number(e.target.value)) })}
                            />
                        </div>

                        <div className="field">
                            <label className="label">배송지</label>
                            <input
                                type="text" className="input" placeholder="서울시 강남구 테헤란로 123"
                                value={modal.address}
                                onChange={(e) => setModal({ ...modal, address: e.target.value })}
                            />
                        </div>

                        <div style={{fontSize:12, color:'#9ca3af'}}>
                            총 결제금액: ₩{(modal.product.productPrice * modal.quantity).toLocaleString()}
                        </div>

                        {error && <div className="error">{error}</div>}

                        <div className="modal-actions">
                            <button className="btn btn-outline" onClick={() => setModal(null)}>취소</button>
                            <button
                                className="btn btn-primary"
                                onClick={handleOrder}
                                disabled={ordering || !modal.address.trim()}
                            >
                                {ordering ? "처리 중..." : "주문 확인"}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </>
    );
}
