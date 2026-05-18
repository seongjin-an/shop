import type { NextConfig } from "next";

const gatewayUrl = process.env.GATEWAY_URL ?? "http://localhost:8090";

const nextConfig: NextConfig = {
    output: "standalone",
    async rewrites() {
        return [
            {
                source: "/gateway-api/:path*",
                destination: `${gatewayUrl}/:path*`,
            },
        ];
    },
};

export default nextConfig;
