/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: false, // Prevents duplicate EventSource connections in React StrictMode
  async rewrites() {
    return [
      {
        source: "/edge-proxy/zone1/:path*",
        destination: "http://localhost:8085/:path*",
      },
      {
        source: "/edge-proxy/zone2/:path*",
        destination: "http://localhost:8086/:path*",
      },
    ];
  },
};

export default nextConfig;
