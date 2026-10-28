"use client";

import Image from "next/image";
import { useRouter } from "next/navigation";
import { History, LogOut, UsersRound, SlidersHorizontal } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/context/AuthContext";

/** Header compartilhado para manter a navegação e o visual consistentes. */
export default function AppHeader() {
  const { user, logout } = useAuth();
  const router = useRouter();

  if (!user) return null;

  function handleLogout() {
    logout();
    router.push("/login");
  }

  return (
    <header className="topbar home-topbar no-print">
      <div className="home-brand-mark" aria-hidden="true">
        <Image
          src="/solumnutri-symbol.png"
          alt=""
          width={38}
          height={35}
          priority
        />
      </div>
      <div className="home-brand-copy">
        <strong>SolumNutri</strong>
        <span>Inteligência para análise de solo</span>
      </div>

      <div className="home-header-actions">
        <span className="home-user-role">
          {user.role === "ADMIN" ? "Administrador" : "Usuário"}
        </span>
        <Button
          className="home-icon-button"
          variant="outline"
          size="icon"
          aria-label="Histórico de amostras"
          title="Histórico de amostras"
          onClick={() => router.push("/historico")}
        >
          <History size={22} />
        </Button>
        <Button
          className="home-icon-button"
          variant="outline"
          size="icon"
          aria-label="Parâmetros de interpretação"
          title="Parâmetros de interpretação"
          onClick={() => router.push("/parametros-interpretacao")}
        >
          <SlidersHorizontal size={22} />
        </Button>
        {user.role === "ADMIN" && (
          <Button
            className="home-icon-button"
            variant="outline"
            size="icon"
            aria-label="Gerenciar usuários"
            title="Gerenciar usuários"
            onClick={() => router.push("/admin/usuarios")}
          >
            <UsersRound size={22} />
          </Button>
        )}
        <Button
          className="home-icon-button"
          variant="outline"
          size="icon"
          aria-label="Sair da conta"
          title="Sair da conta"
          onClick={handleLogout}
        >
          <LogOut size={22} />
        </Button>
      </div>
    </header>
  );
}
