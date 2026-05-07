import type { NextConfig } from "next";

const nextConfig: NextConfig = {
    async rewrites() {
        return [
            {
                source: "/gateway-api/:path*",
                destination: "http://localhost:8090/:path*",
            },
        ];
    },
};

export default nextConfig;
