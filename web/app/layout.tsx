import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "REA Chat — مساعد الهندسة العكسية",
  description: "مساعد ذكاء اصطناعي لتحليل التطبيقات عبر REA",
  applicationName: "REA Chat"
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="ar" dir="rtl"><body>{children}</body></html>;
}
