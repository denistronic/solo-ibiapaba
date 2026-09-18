"use client";

import { useEffect, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { ApiError } from "@/lib/api";
import {
  createUser,
  listUsers,
  updateUser,
  updateUserStatus,
  type ManagedUser,
} from "@/lib/users";
import type { UserRole } from "@/lib/auth";

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
      void reload();
    }
  }, [user]);

  // --- criação de usuário ---
  const [newName, setNewName] = useState("");
  const [newEmail, setNewEmail] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [newRole, setNewRole] = useState<UserRole>("USER");
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  async function handleCreate(event: FormEvent) {
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
      <main className="flex min-h-screen items-center justify-center">
        <p className="text-sm text-neutral-500">Carregando...</p>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-neutral-50 px-4 py-8">
      <div className="mx-auto max-w-4xl space-y-8">
        <div className="flex items-center justify-between">
          <h1 className="text-xl font-semibold text-neutral-900">
            Gestão de usuários
          </h1>
          <button
            onClick={() => router.push("/")}
            className="text-sm text-neutral-600 underline"
          >
            Voltar
          </button>
        </div>

        {/* Formulário de criação */}
        <section className="rounded-lg border border-neutral-200 bg-white p-6 shadow-sm">
          <h2 className="mb-4 text-sm font-semibold text-neutral-900">
            Novo usuário
          </h2>

          <form onSubmit={handleCreate} className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <input
              type="text"
              required
              placeholder="Nome"
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              className="rounded-md border border-neutral-300 px-3 py-2 text-sm outline-none focus:border-neutral-500"
            />
            <input
              type="email"
              required
              placeholder="E-mail"
              value={newEmail}
              onChange={(e) => setNewEmail(e.target.value)}
              className="rounded-md border border-neutral-300 px-3 py-2 text-sm outline-none focus:border-neutral-500"
            />
            <input
              type="password"
              required
              placeholder="Senha inicial"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              className="rounded-md border border-neutral-300 px-3 py-2 text-sm outline-none focus:border-neutral-500"
            />
            <select
              value={newRole}
              onChange={(e) => setNewRole(e.target.value as UserRole)}
              className="rounded-md border border-neutral-300 px-3 py-2 text-sm outline-none focus:border-neutral-500"
            >
              <option value="USER">Usuário</option>
              <option value="ADMIN">Administrador</option>
            </select>

            {createError && (
              <p className="sm:col-span-2 rounded-md bg-red-50 px-3 py-2 text-sm text-red-600">
                {createError}
              </p>
            )}

            <div className="sm:col-span-2">
              <button
                type="submit"
                disabled={creating}
                className="rounded-md bg-neutral-900 px-4 py-2 text-sm font-medium text-white hover:bg-neutral-800 disabled:opacity-50"
              >
                {creating ? "Criando..." : "Criar usuário"}
              </button>
            </div>
          </form>
        </section>

        {/* Lista de usuários */}
        <section className="rounded-lg border border-neutral-200 bg-white p-6 shadow-sm">
          <h2 className="mb-4 text-sm font-semibold text-neutral-900">
            Usuários cadastrados
          </h2>

          {error && (
            <p className="mb-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-600">
              {error}
            </p>
          )}
          {rowError && (
            <p className="mb-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-600">
              {rowError}
            </p>
          )}

          {loadingUsers && <p className="text-sm text-neutral-500">Carregando...</p>}

          {!loadingUsers && (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-neutral-200 text-neutral-500">
                    <th className="py-2 pr-4">Nome</th>
                    <th className="py-2 pr-4">E-mail</th>
                    <th className="py-2 pr-4">Perfil</th>
                    <th className="py-2 pr-4">Status</th>
                    <th className="py-2 pr-4">Ações</th>
                  </tr>
                </thead>
                <tbody>
                  {users.map((target) => (
                    <tr key={target.id} className="border-b border-neutral-100">
                      {editingId === target.id ? (
                        <>
                          <td className="py-2 pr-4">
                            <input
                              value={editName}
                              onChange={(e) => setEditName(e.target.value)}
                              className="w-full rounded-md border border-neutral-300 px-2 py-1"
                            />
                          </td>
                          <td className="py-2 pr-4">
                            <input
                              value={editEmail}
                              onChange={(e) => setEditEmail(e.target.value)}
                              className="w-full rounded-md border border-neutral-300 px-2 py-1"
                            />
                          </td>
                          <td className="py-2 pr-4">
                            <select
                              value={editRole}
                              onChange={(e) => setEditRole(e.target.value as UserRole)}
                              className="rounded-md border border-neutral-300 px-2 py-1"
                            >
                              <option value="USER">Usuário</option>
                              <option value="ADMIN">Administrador</option>
                            </select>
                          </td>
                          <td className="py-2 pr-4">
                            {target.active ? "Ativo" : "Inativo"}
                          </td>
                          <td className="py-2 pr-4 space-x-2 whitespace-nowrap">
                            <button
                              onClick={() => saveEdit(target.id)}
                              disabled={savingEdit}
                              className="rounded-md bg-neutral-900 px-2 py-1 text-xs text-white disabled:opacity-50"
                            >
                              Salvar
                            </button>
                            <button
                              onClick={cancelEdit}
                              className="rounded-md border border-neutral-300 px-2 py-1 text-xs"
                            >
                              Cancelar
                            </button>
                          </td>
                        </>
                      ) : (
                        <>
                          <td className="py-2 pr-4">{target.name}</td>
                          <td className="py-2 pr-4">{target.email}</td>
                          <td className="py-2 pr-4">
                            {target.role === "ADMIN" ? "Administrador" : "Usuário"}
                          </td>
                          <td className="py-2 pr-4">
                            <span
                              className={
                                target.active
                                  ? "text-green-600"
                                  : "text-neutral-400"
                              }
                            >
                              {target.active ? "Ativo" : "Inativo"}
                            </span>
                          </td>
                          <td className="py-2 pr-4 space-x-2 whitespace-nowrap">
                            <button
                              onClick={() => startEdit(target)}
                              className="rounded-md border border-neutral-300 px-2 py-1 text-xs"
                            >
                              Editar
                            </button>
                            <button
                              onClick={() => toggleStatus(target)}
                              className="rounded-md border border-neutral-300 px-2 py-1 text-xs"
                            >
                              {target.active ? "Desativar" : "Ativar"}
                            </button>
                          </td>
                        </>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>

              {!users.length && (
                <p className="text-sm text-neutral-500">Nenhum usuário cadastrado.</p>
              )}
            </div>
          )}
        </section>
      </div>
    </main>
  );
}
