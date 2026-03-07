"use client";
import { useEffect, useRef, useState } from "react";
import { useParams } from "next/navigation";
import { getOrder } from "@/services/orderService";
import { Order, OrderStatus } from "@/types/order";

const SAGA_STEPS: { status: OrderStatus; label: string; desc: string }[] = [
    { status: "PENDING",        label: "주문 접수",    desc: "재고 예약 요청 중" },
    { status: "STOCK_RESERVED", label: "재고 예약 완료", desc: "결제 요청 중" },
    { status: "COMPLETED",      label: "주문 완료",    desc: "결제 성공" },
];

const TERMINAL_FAILED: Record<string, { label: string; color: string; desc: string }> = {
    STOCK_FAILED:   { label: "재고 부족",    color: "#dc2626", desc: "재고가 부족하여 주문이 취소되었습니다." },
    PAYMENT_FAILED: { label: "결제 실패",    color: "#d97706", desc: "결제에 실패했습니다. 재고 복구 중..." },
    CANCELLED:      { label: "주문 취소",    color: "#6b7280", desc: "보상 트랜잭션 완료. 재고가 복구되었습니다." },
};

function stepIndex(status: OrderStatus): number {
    return SAGA_STEPS.findIndex((s) => s.status === status);
}

export default function OrderStatusPage() {
    const { orderId } = useParams<{ orderId: string }>();
    const [order, setOrder] = useState<Order | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
    const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

    async function fetchOrder() {
        try {
            const data = await getOrder(orderId);
            setOrder(data);
            setLastUpdated(new Date());
            // 완료 또는 실패 상태면 폴링 중단
            if (["COMPLETED", "STOCK_FAILED", "CANCELLED"].includes(data.orderStatus)) {
                stopPolling();
            }
        } catch (e) {
            setError("주문 정보를 불러오지 못했습니다.");
        }
    }

    function startPolling() {
        intervalRef.current = setInterval(fetchOrder, 3000);
    }

    function stopPolling() {
        if (intervalRef.current) {
            clearInterval(intervalRef.current);
            intervalRef.current = null;
        }
    }

    useEffect(() => {
        fetchOrder();
        startPolling();
        return () => stopPolling();
    }, [orderId]);

    const isTerminalFailed = order && order.orderStatus in TERMINAL_FAILED;
    const currentStep = order ? stepIndex(order.orderStatus) : -1;
    const isPolling = intervalRef.current !== null;

    return (
        <>
            <style>{`
                .page { min-height:100vh; background:#f4f6f9; display:flex; flex-direction:column; align-items:center; padding:40px 24px; width:100%; }
                .back { align-self:flex-start; max-width:700px; width:100%; margin-bottom:16px; }
                .back a { font-size:14px; color:#555; text-decoration:none; }
                .back a:hover { color:#111; }
                .card { background:#fff; border:1px solid #e5e7eb; border-radius:12px; padding:32px; width:100%; max-width:700px; }
                .card-title { font-size:18px; font-weight:700; color:#111; margin-bottom:4px; }
                .order-id { font-size:12px; color:#9ca3af; font-family:monospace; margin-bottom:24px; }
                /* Steps */
                .steps { display:flex; align-items:flex-start; gap:0; margin-bottom:32px; }
                .step { flex:1; display:flex; flex-direction:column; align-items:center; position:relative; }
                .step:not(:last-child)::after { content:''; position:absolute; top:18px; left:50%; width:100%; height:2px; background:#e5e7eb; z-index:0; }
                .step:not(:last-child).done::after { background:#22c55e; }
                .step-dot { width:36px; height:36px; border-radius:50%; display:flex; align-items:center; justify-content:center; font-size:14px; font-weight:700; z-index:1; border:2px solid #e5e7eb; background:#fff; color:#9ca3af; }
                .step-dot.done { background:#22c55e; border-color:#22c55e; color:#fff; }
                .step-dot.active { background:#fff; border-color:#111; color:#111; }
                .step-label { margin-top:8px; font-size:12px; font-weight:500; color:#6b7280; text-align:center; }
                .step-label.active { color:#111; font-weight:600; }
                .step-label.done { color:#16a34a; }
                .step-desc { font-size:11px; color:#9ca3af; text-align:center; margin-top:2px; }
                /* Failed banner */
                .failed-banner { border-radius:8px; padding:16px 20px; margin-bottom:24px; }
                /* Info section */
                .info-grid { display:grid; grid-template-columns:1fr 1fr; gap:12px; margin-bottom:24px; }
                .info-item { background:#f9fafb; border-radius:8px; padding:12px 16px; }
                .info-label { font-size:11px; color:#9ca3af; font-weight:500; text-transform:uppercase; letter-spacing:.5px; }
                .info-value { font-size:15px; font-weight:600; color:#111; margin-top:4px; }
                /* Items table */
                .section-title { font-size:14px; font-weight:600; color:#374151; margin-bottom:12px; }
                .items-table { width:100%; border-collapse:collapse; font-size:14px; }
                .items-table th { text-align:left; padding:8px 12px; background:#f9fafb; color:#6b7280; font-weight:500; border-bottom:1px solid #e5e7eb; }
                .items-table td { padding:10px 12px; border-bottom:1px solid #f3f4f6; color:#111; }
                /* Footer */
                .footer { display:flex; justify-content:space-between; align-items:center; margin-top:20px; padding-top:16px; border-top:1px solid #f3f4f6; }
                .polling-badge { font-size:12px; color:#6b7280; display:flex; align-items:center; gap:6px; }
                .dot-anim { width:8px; height:8px; border-radius:50%; background:#22c55e; animation:pulse 1.5s infinite; }
                @keyframes pulse { 0%,100%{opacity:1} 50%{opacity:.3} }
                .btn { padding:8px 16px; border-radius:6px; font-size:13px; cursor:pointer; border:1px solid #d1d5db; background:#fff; color:#374151; }
                .btn:hover { background:#f9fafb; }
                .error { color:#dc2626; font-size:14px; }
            `}</style>

            <div className="page">
                <div className="back"><a href="/">← 상품 목록</a></div>
                <div className="card">
                    {error && <div className="error">{error}</div>}

                    {!order && !error && <div style={{color:'#9ca3af', textAlign:'center', padding:'40px 0'}}>불러오는 중...</div>}

                    {order && (
                        <>
                            <div className="card-title">주문 상태</div>
                            <div className="order-id">주문 ID: {order.orderId}</div>

                            {/* Saga 단계 표시 (정상 흐름) */}
                            {!isTerminalFailed && (
                                <div className="steps">
                                    {SAGA_STEPS.map((step, i) => {
                                        const isDone = currentStep > i;
                                        const isActive = currentStep === i;
                                        return (
                                            <div key={step.status} className={`step ${isDone ? 'done' : ''}`}>
                                                <div className={`step-dot ${isDone ? 'done' : isActive ? 'active' : ''}`}>
                                                    {isDone ? '✓' : i + 1}
                                                </div>
                                                <div className={`step-label ${isDone ? 'done' : isActive ? 'active' : ''}`}>{step.label}</div>
                                                {isActive && <div className="step-desc">{step.desc}</div>}
                                            </div>
                                        );
                                    })}
                                </div>
                            )}

                            {/* 실패/취소 상태 배너 */}
                            {isTerminalFailed && TERMINAL_FAILED[order.orderStatus] && (
                                <div className="failed-banner" style={{background:`${TERMINAL_FAILED[order.orderStatus].color}15`, border:`1px solid ${TERMINAL_FAILED[order.orderStatus].color}40`}}>
                                    <div style={{fontWeight:700, color:TERMINAL_FAILED[order.orderStatus].color, marginBottom:4}}>
                                        {TERMINAL_FAILED[order.orderStatus].label}
                                    </div>
                                    <div style={{fontSize:13, color:'#555'}}>{TERMINAL_FAILED[order.orderStatus].desc}</div>
                                </div>
                            )}

                            {/* 주문 정보 */}
                            <div className="info-grid">
                                <div className="info-item">
                                    <div className="info-label">상태</div>
                                    <div className="info-value">{order.orderStatus}</div>
                                </div>
                                <div className="info-item">
                                    <div className="info-label">총 금액</div>
                                    <div className="info-value">₩{Number(order.totalAmount).toLocaleString()}</div>
                                </div>
                                <div className="info-item" style={{gridColumn:'1/-1'}}>
                                    <div className="info-label">배송지</div>
                                    <div className="info-value">{order.deliveryAddress}</div>
                                </div>
                            </div>

                            {/* 주문 상품 목록 */}
                            <div className="section-title">주문 상품</div>
                            <table className="items-table">
                                <thead>
                                    <tr>
                                        <th>상품명</th>
                                        <th>단가</th>
                                        <th>수량</th>
                                        <th>소계</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {order.items.map((item) => (
                                        <tr key={item.productId}>
                                            <td>{item.productName}</td>
                                            <td>₩{Number(item.unitPrice).toLocaleString()}</td>
                                            <td>{item.quantity}</td>
                                            <td>₩{Number(item.subtotal).toLocaleString()}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>

                            <div className="footer">
                                <div className="polling-badge">
                                    {isPolling
                                        ? <><span className="dot-anim" /> 3초마다 자동 갱신 중</>
                                        : <span>갱신 완료</span>
                                    }
                                    {lastUpdated && <span style={{marginLeft:8}}>· {lastUpdated.toLocaleTimeString()}</span>}
                                </div>
                                <button className="btn" onClick={fetchOrder}>↻ 새로고침</button>
                            </div>
                        </>
                    )}
                </div>
            </div>
        </>
    );
}
