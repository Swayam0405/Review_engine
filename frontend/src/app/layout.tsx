import type { Metadata } from "next";
import type { ReactNode } from "react";
import { AuthProvider } from "../providers/AuthProvider";
import "./globals.scss";

export const metadata: Metadata = {
  title: "Review Engine",
  description: "Review Engine frontend application.",
};

export default function RootLayout({
  children,
}: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="en">
      <body>
        <AuthProvider>{children}</AuthProvider>
      </body>
    </html>
  );
}
