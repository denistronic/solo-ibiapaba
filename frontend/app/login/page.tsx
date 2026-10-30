"use client";

import Image from "next/image";
import { useEffect, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { passwordError } from "@/lib/validation";
import { ApiError } from "@/lib/api";
import styles from "./login.module.css";

export default function LoginPage() {
  const { login, user, loading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!loading && user) {
      router.push("/");
    }
  }, [loading, user, router]);

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    const invalidPassword = passwordError(password, false);
    if (invalidPassword) { setError(invalidPassword); return; }
    setSubmitting(true);

    try {
      await login(email, password);
      router.push("/");
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("Não foi possível entrar. Tente novamente.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className={styles.screen}>
      <section className={styles.loginCard} aria-labelledby="login-title">
        <header className={styles.brand}>
          <Image
            src="/solumnutri-logo-mark.png"
            alt=""
            aria-hidden="true"
            width={130}
            height={119}
            priority
            className={styles.brandMark}
          />
          <h1 id="login-title" className={styles.brandName}>
            SolumNutri
          </h1>
        </header>

        <form onSubmit={handleSubmit} className={styles.form}>
          <div className={styles.field}>
            <label htmlFor="email">E-mail</label>
            <input
              id="email"
              name="email"
              type="email"
              maxLength={180}
              autoComplete="username"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="voce@exemplo.com"
              disabled={submitting}
            />
          </div>

          <div className={styles.field}>
            <label htmlFor="password">Senha</label>
            <input
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="••••••••••••••"
              disabled={submitting}
            />
          </div>

          {error && (
            <p className={styles.error} role="alert">
              {error}
            </p>
          )}

          <button
            type="submit"
            disabled={submitting || loading}
            className={styles.submitButton}
          >
            {submitting ? "Entrando..." : "Entrar"}
          </button>
        </form>
      </section>
    </main>
  );
}
