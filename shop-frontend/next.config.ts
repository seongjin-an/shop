import type { NextConfig } from "next";

const nextConfig: NextConfig = {
    async rewrites() {
        return [
            {
                source: "/api/:path*",
                destination: "http://localhost:8080/api/:path*",  // shop-user
            },
            {
                source: "/product-api/:path*",
                destination: "http://localhost:8081/api/:path*",  // shop-product
            },
            {
                source: "/order-api/:path*",
                destination: "http://localhost:8082/:path*",       // shop-order
            },
        ]
    }
};

export default nextConfig;
