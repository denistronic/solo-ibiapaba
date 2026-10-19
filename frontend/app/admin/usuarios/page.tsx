"use client";

import { useEffect, useState, type FormEvent } from "react";
import { UserRoundPlus } from "lucide-react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { ApiError } from "@/lib/api";
import AppHeader from "@/components/AppHeader";
import {
  createUser,
  listUsers,
  updateUser,
  updateUserStatus,
  type ManagedUser,
} from "@/lib/users";
import type { UserRole } from "@/lib/auth";
import styles from "./usuarios.module.css";

export default function AdminUsuariosPage() {
  const { user, loading: authLoading } = useAuth();
  const router = useRouter();

  // Só ADMIN pode ver essa tela. Quem não é admin (ou não está logado)
  // é mandado de volta pra home.
  useEffect(() => {
    if (!authLoading && (!user || user.role !== "ADMIN")) {
      router.push("/");
    }
  }, [authLoading, user, router]);

  const [users, setUsers] = useState<ManagedUser[]>([]);
  const [loadingUsers, setLoadingUsers] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function reload() {
    setLoadingUsers(true);
    try {
      const data = await listUsers();
      setUsers(data);
      setError(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Não foi possível carregar os usuários.");
    } finally {
      setLoadingUsers(false);
    }
  }

  useEffect(() => {
    if (user?.role === "ADMIN") {
      // Aguarda a microtask para iniciar o carregamento fora do corpo do efeito.
      void Promise.resolve().then(() => reload());
    }
  }, [user]);

  // --- criação de usuário ---
  const [newName, setNewName] = useState("");
  const [newEmail, setNewEmail] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [newRole, setNewRole] = useState<UserRole>("USER");
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setCreating(true);
    setCreateError(null);

    try {
      await createUser({
        name: newName,
        email: newEmail,
        password: newPassword,
        role: newRole,
      });
      setNewName("");
      setNewEmail("");
      setNewPassword("");
      setNewRole("USER");
      await reload();
    } catch (err) {
      setCreateError(
        err instanceof ApiError ? err.message : "Não foi possível criar o usuário.",
      );
    } finally {
      setCreating(false);
    }
  }

  // --- edição inline ---
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editName, setEditName] = useState("");
  const [editEmail, setEditEmail] = useState("");
  const [editRole, setEditRole] = useState<UserRole>("USER");
  const [savingEdit, setSavingEdit] = useState(false);
  const [rowError, setRowError] = useState<string | null>(null);

  function startEdit(target: ManagedUser) {
    setEditingId(target.id);
    setEditName(target.name);
    setEditEmail(target.email);
    setEditRole(target.role);
    setRowError(null);
  }

  function cancelEdit() {
    setEditingId(null);
    setRowError(null);
  }

  async function saveEdit(id: string) {
    setSavingEdit(true);
    setRowError(null);
    try {
      await updateUser(id, { name: editName, email: editEmail, role: editRole });
      setEditingId(null);
      await reload();
    } catch (err) {
      setRowError(err instanceof ApiError ? err.message : "Não foi possível salvar.");
    } finally {
      setSavingEdit(false);
    }
  }

  async function toggleStatus(target: ManagedUser) {
    setRowError(null);
    try {
      await updateUserStatus(target.id, !target.active);
      await reload();
    } catch (err) {
      setRowError(err instanceof ApiError ? err.message : "Não foi possível alterar o status.");
    }
  }

  if (authLoading || !user || user.role !== "ADMIN") {
    return (
      <main className={`app-shell home-app-shell ${styles.page}`}>
        <div className="flex min-h-screen items-center justify-center">
          <p className="text-sm text-neutral-500">Carregando...</p>
        </div>
      </main>
    );
  }

  return (
    <main className={`app-shell home-app-shell ${styles.page}`}>
      <AppHeader />

      <div className={styles.content}>
        <div className={styles.pageHeading}>
          <div className={styles.pageHeadingCopy}>
            <p className={styles.eyebrow}>GESTÃO DE FERTILIDADE</p>
            <h1>Gerenciamento de usuários</h1>
            <p className={styles.pageDescription}>
              Cadastre, consulte e edite os usuários do sistema.
            </p>
          </div>
          <button
            type="button"
            onClick={() => router.push("/")}
            className={styles.backButton}
          >
            Voltar
          </button>
        </div>

        <section className={`${styles.card} ${styles.createCard}`} aria-labelledby="new-user-title">
          <div className={styles.cardHeading}>
            <span className={styles.sectionIcon} aria-hidden="true">
              <UserRoundPlus size={22} strokeWidth={2.2} />
            </span>
            <h2 id="new-user-title" className={styles.cardTitle}>
              Novo usuário
            </h2>
          </div>

          <form onSubmit={handleCreate} className={styles.createForm}>
            <input
              type="text"
              name="name"
              autoComplete="name"
              required
              placeholder="Nome"
              aria-label="Nome"
              value={newName}
              onChange={(event) => setNewName(event.target.value)}
              className={styles.input}
            />
            <input
              type="email"
              name="email"
              autoComplete="email"
              required
              placeholder="E-mail"
              aria-label="E-mail"
              value={newEmail}
              onChange={(event) => setNewEmail(event.target.value)}
              className={styles.input}
            />
            <input
              type="password"
              name="password"
              autoComplete="new-password"
              required
              placeholder="Senha"
              aria-label="Senha inicial"
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
              className={styles.input}
            />
            <select
              name="role"
              aria-label="Perfil do usuário"
              value={newRole}
              onChange={(event) => setNewRole(event.target.value as UserRole)}
              className={styles.select}
            >
              <option value="USER">Usuário</option>
              <option value="ADMIN">Administrador</option>
            </select>

            {createError && (
              <p className={styles.createError} role="alert">
                {createError}
              </p>
            )}

            <div className={styles.createActions}>
              <button type="submit" disabled={creating} className={styles.primaryButton}>
                {creating ? "Criando..." : "Criar usuário"}
              </button>
            </div>
          </form>
        </section>

        <section className={`${styles.card} ${styles.listCard}`} aria-labelledby="users-list-title">
          <h2 id="users-list-title" className={styles.cardTitle}>
            Usuários cadastrados
          </h2>

          {error && <p className={styles.errorMessage} role="alert">{error}</p>}
          {rowError && <p className={styles.errorMessage} role="alert">{rowError}</p>}

          {loadingUsers && <p className={styles.loadingMessage}>Carregando...</p>}

          {!loadingUsers && (
            <div className={styles.tableWrap}>
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th scope="col">Nome</th>
                    <th scope="col">E-mail</th>
                    <th scope="col">Perfil</th>
                    <th scope="col">Status</th>
                    <th scope="col">Ações</th>
                  </tr>
                </thead>
                <tbody>
                  {users.map((target) => (
                    <tr key={target.id}>
                      {editingId === target.id ? (
                        <>
                          <td>
                            <input
                              aria-label={`Nome de ${target.name}`}
                              value={editName}
                              onChange={(event) => setEditName(event.target.value)}
                              className={styles.tableInput}
                            />
                          </td>
                          <td>
                            <input
                              aria-label={`E-mail de ${target.name}`}
                              type="email"
                              value={editEmail}
                              onChange={(event) => setEditEmail(event.target.value)}
                              className={styles.tableInput}
                            />
                          </td>
                          <td>
                            <select
                              aria-label={`Perfil de ${target.name}`}
                              value={editRole}
                              onChange={(event) => setEditRole(event.target.value as UserRole)}
                              className={styles.tableSelect}
                            >
                              <option value="USER">Usuário</option>
                              <option value="ADMIN">Administrador</option>
                            </select>
                          </td>
                          <td>
                            <span className={target.active ? styles.statusActive : styles.statusInactive}>
                              {target.active ? "Ativo" : "Inativo"}
                            </span>
                          </td>
                          <td>
                            <div className={styles.actions}>
                              <button
                                type="button"
                                onClick={() => saveEdit(target.id)}
                                disabled={savingEdit}
                                className={styles.actionButton}
                              >
                                Salvar
                              </button>
                              <button
                                type="button"
                                onClick={cancelEdit}
                                className={styles.actionButton}
                              >
                                Cancelar
                              </button>
                            </div>
                          </td>
                        </>
                      ) : (
                        <>
                          <td>{target.name}</td>
                          <td>{target.email}</td>
                          <td>{target.role === "ADMIN" ? "Administrador" : "Usuário"}</td>
                          <td>
                            <span className={target.active ? styles.statusActive : styles.statusInactive}>
                              {target.active ? "Ativo" : "Inativo"}
                            </span>
                          </td>
                          <td>
                            <div className={styles.actions}>
                              <button
                                type="button"
                                onClick={() => startEdit(target)}
                                className={styles.actionButton}
                              >
                                Editar
                              </button>
                              <button
                                type="button"
                                onClick={() => toggleStatus(target)}
                                className={styles.actionButton}
                              >
                                {target.active ? "Desativar" : "Ativar"}
                              </button>
                            </div>
                          </td>
                        </>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>

              {!users.length && (
                <p className={styles.emptyMessage}>Nenhum usuário cadastrado.</p>
              )}
            </div>
          )}
        </section>
      </div>
    </main>
  );
}
