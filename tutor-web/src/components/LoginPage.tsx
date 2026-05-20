import { useState } from "react";

/**
 * LoginPage — magic-link email entry screen.
 *
 * Brutalist mono style (JetBrains Mono, Ink/Paper/Accent tokens).
 * Self-contained — no router hooks. Route wiring is in App.tsx.
 *
 * On submit: POST /auth/request-link with { email, lang }.
 * Shows "check your email" state after response resolves regardless
 * of status detail — prevents email enumeration via UI feedback.
 */
export function LoginPage() {
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);
  const lang = "ro";

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    try {
      await fetch("/auth/request-link", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, lang }),
      });
    } catch (_) {
      // network error — still show the check-email state to avoid enumeration
    }
    setSent(true);
  }

  if (sent) {
    return (
      <div data-testid="login-sent" className="p-6 font-mono text-sm flex flex-col items-start gap-4 max-w-md">
        <h1 className="text-lg font-bold tracking-widest">CHECK YOUR EMAIL</h1>
        <p className="text-sm text-page-fg/80">
          Am trimis un link de autentificare la adresa introdusă.
        </p>
        <p className="text-xs text-page-fg/60">
          Poate dura câteva secunde. / It may take a few seconds.
        </p>
      </div>
    );
  }

  return (
    <div data-testid="login-page" className="p-6 font-mono text-sm flex flex-col items-start gap-4 max-w-md">
      <h1 className="text-lg font-bold tracking-widest">SIGN IN — JARVIS TUTOR</h1>
      <p className="text-sm text-page-fg/80">
        Enter your email to receive a sign-in link. No password needed.
      </p>
      <form
        onSubmit={handleSubmit}
        className="w-full flex flex-col gap-3"
      >
        <div className="flex flex-col gap-1">
          <label htmlFor="login-email-input" className="text-xs font-bold tracking-widest">
            EMAIL
          </label>
          <input
            id="login-email-input"
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={e => setEmail(e.target.value)}
            placeholder="you@example.com"
            className="border-2 border-border-strong px-3 py-2 text-sm bg-page-bg text-page-fg focus:outline-none focus:ring-2 focus:ring-ring-focus"
          />
        </div>
        <button
          type="submit"
          className="text-xs font-bold tracking-widest bg-panel-dark-bg text-panel-dark-fg px-4 py-2 hover:bg-page-fg disabled:opacity-50 self-start"
        >
          SEND LINK / TRIMITE LINK
        </button>
      </form>
    </div>
  );
}
