import type { Metadata } from "next";
import "./globals.css";
import { AuthProvider } from "@/context/AuthContext";

export const metadata: Metadata = {
  title: "SolumNutri",
  description: "Inteligência para análise de solo: cadastro, interpretação e relatórios de fertilidade.",
  icons: {
    icon: "/solumnutri-symbol.png",
    shortcut: "/solumnutri-symbol.png",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="pt-BR">
      <body className="antialiased">
        <AuthProvider>{children}</AuthProvider>
      </body>
    </html>
  );
}