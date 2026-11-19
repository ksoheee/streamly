import type { Metadata, Viewport } from "next";
import { Suspense } from "react";
import "./globals.css";
import { AuthProvider } from "@/lib/auth";
import Header from "@/components/Header";
import Sidebar from "@/components/Sidebar";

export const metadata: Metadata = {
  title: "Streamly",
  description: "브라우저로 송출하고 시청하는 실시간 라이브 방송",
};

export const viewport: Viewport = { width: "device-width", initialScale: 1 };

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <body>
        <AuthProvider>
          <Suspense>
            <Header />
            <div className="shell">
              <Sidebar />
              <main className="container">{children}</main>
            </div>
          </Suspense>
        </AuthProvider>
      </body>
    </html>
  );
}
