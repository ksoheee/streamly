import type { NextConfig } from "next";

// 운영에서는 Caddy가 /api, WHIP/WHEP 를 나눠 준다. 로컬 개발에서만 Next.js가 대신 넘긴다.
const backend = process.env.BACKEND_URL ?? "http://localhost:8080";
const mediamtx = process.env.MEDIAMTX_URL ?? "http://localhost:8889";

const nextConfig: NextConfig = {
  output: "standalone",
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${backend}/api/:path*` },
      { source: "/:stream/whip", destination: `${mediamtx}/:stream/whip` },
      { source: "/:stream/whep", destination: `${mediamtx}/:stream/whep` },
      { source: "/:stream/whip/:session", destination: `${mediamtx}/:stream/whip/:session` },
      { source: "/:stream/whep/:session", destination: `${mediamtx}/:stream/whep/:session` },
    ];
  },
};

export default nextConfig;
