# R5 — Daily email summary (Khoj automation parity)

## Context

Khoj's scheduled-automations email-back the result. jarvis's `runReflect` writes to `wiki.md` only — user has to remember to look. Email scales to daily without saturating push limits.

## Approaches

- **(a) Jakarta Mail dep + Gmail SMTP via app-password.** ✓ Picked. Stable, well-known, single new dep.
- **(b) curl-shell-out to transactional API (Resend/Mailgun).** Adds external service + paid tier risk. Rejected.
- **(c) Write to a file; user wires own cron+sendmail.** Two-step setup; user friction. Rejected.

## Design

**Dep:** `com.sun.mail:jakarta.mail:2.0.1` in `build.gradle.kts`.

**Config (env, not hardcoded):**
- `JARVIS_DAILY_EMAIL` = `true|false` (default false).
- `SMTP_HOST` (default `smtp.gmail.com`)
- `SMTP_PORT` (default `587`)
- `SMTP_USER` (Gmail address)
- `SMTP_PASS` (Gmail app-password)
- `SMTP_TO` (delivery recipient, default = SMTP_USER)

**New file:** `src/main/kotlin/jarvis/Smtp.kt` with `Smtp.sendIfEnabled(subject, body)` — reads env, no-op when disabled or creds missing, throws on actual SMTP error so caller can log.

**ReflectMain change:** after `MemoryWiki.append`, call `Smtp.sendIfEnabled("jarvis: daily reflection ($model)", reply)`. Wrap in try/catch — failed delivery logs but doesn't fail the cron.

## Edge cases

- Creds missing → silent no-op (loud-fail too noisy for first-deploy state).
- SMTP throws → System.err.println + return; do NOT crash reflect cron.
- Body too large → Gmail caps at 25 MB; daily reflection is <2 KB. Safe.

## Acceptance criteria

- Test for Smtp.sendIfEnabled when disabled (returns false, no network call).
- Test Smtp config parsing.
- Manual: user sets env, observes email arrival via existing PC reflect cron (next daily 08:00 local).

## LOC estimate

~50 LOC + 30 LOC tests.
