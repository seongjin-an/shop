"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { createProduct } from "@/services/productService";

export default function CreateProductPage() {
    const router = useRouter();
    const [form, setForm] = useState({ productName: "", productDesc: "", productPrice: "", quantity: "" });
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    function update(field: string, value: string) {
        setForm((prev) => ({ ...prev, [field]: value }));
    }

    async function handleSubmit(e: React.FormEvent) {
        e.preventDefault();
        setLoading(true);
        setError(null);
        try {
            await createProduct({
                productName: form.productName,
                productDesc: form.productDesc,
                productPrice: Number(form.productPrice),
                quantity: Number(form.quantity),
            });
            router.push("/");
        } catch (e) {
            setError("상품 등록에 실패했습니다.");
        } finally {
            setLoading(false);
        }
    }

    return (
        <>
            <style>{`
                .page { min-height:100vh; background:#f4f6f9; display:flex; flex-direction:column; align-items:center; padding:40px 24px; width:100%; }
                .back { align-self:flex-start; max-width:480px; width:100%; margin-bottom:16px; }
                .back a { font-size:14px; color:#555; text-decoration:none; }
                .back a:hover { color:#111; }
                .card { background:#fff; border:1px solid #e5e7eb; border-radius:12px; padding:32px; width:100%; max-width:480px; }
                .title { font-size:20px; font-weight:700; color:#111; margin-bottom:24px; }
                .field { display:flex; flex-direction:column; gap:6px; margin-bottom:18px; }
                .label { font-size:13px; font-weight:500; color:#374151; }
                .input { padding:10px 12px; border:1px solid #d1d5db; border-radius:6px; font-size:14px; outline:none; }
                .input:focus { border-color:#111; }
                .textarea { padding:10px 12px; border:1px solid #d1d5db; border-radius:6px; font-size:14px; outline:none; resize:vertical; min-height:80px; }
                .textarea:focus { border-color:#111; }
                .btn { width:100%; padding:12px; border-radius:6px; font-size:15px; font-weight:600; cursor:pointer; border:none; background:#111; color:#fff; }
                .btn:hover { background:#333; }
                .btn:disabled { background:#9ca3af; cursor:not-allowed; }
                .error { color:#dc2626; font-size:13px; background:#fef2f2; border:1px solid #fecaca; border-radius:6px; padding:10px 14px; margin-bottom:16px; }
            `}</style>

            <div className="page">
                <div className="back">
                    <a href="/">← 목록으로</a>
                </div>
                <div className="card">
                    <div className="title">상품 등록</div>
                    {error && <div className="error">{error}</div>}
                    <form onSubmit={handleSubmit}>
                        <div className="field">
                            <label className="label">상품명</label>
                            <input className="input" placeholder="비타민C 1000mg" required
                                value={form.productName} onChange={(e) => update("productName", e.target.value)} />
                        </div>
                        <div className="field">
                            <label className="label">상품 설명</label>
                            <textarea className="textarea" placeholder="상품에 대한 설명을 입력하세요"
                                value={form.productDesc} onChange={(e) => update("productDesc", e.target.value)} />
                        </div>
                        <div className="field">
                            <label className="label">판매 가격 (원)</label>
                            <input className="input" type="number" min={0} placeholder="30000" required
                                value={form.productPrice} onChange={(e) => update("productPrice", e.target.value)} />
                        </div>
                        <div className="field">
                            <label className="label">초기 재고 수량</label>
                            <input className="input" type="number" min={1} placeholder="100" required
                                value={form.quantity} onChange={(e) => update("quantity", e.target.value)} />
                        </div>
                        <button className="btn" type="submit" disabled={loading}>
                            {loading ? "등록 중..." : "상품 등록"}
                        </button>
                    </form>
                </div>
            </div>
        </>
    );
}
