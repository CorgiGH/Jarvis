# Gate 2: Auth + Compliance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the single-user jarvis tutor into a multi-user app with email magic-link login, an AI-literacy first-login gate, a GDPR Settings/Me surface, and a cross-tenant isolation test — all on the existing Kotlin/Ktor + SQLite/Exposed stack.

**Architecture:** Kotlin-native. Magic-link auth extends the existing `UsersTable`/`SessionRepo`/token pattern. A new `magic_link_tokens` table holds single-use, short-TTL, email-bound tokens; Resend's HTTP API (called through the ktor-client already on the classpath) delivers the link email. Sessions are hardened (the stored lookup key is hashed, not the raw bearer sid). Compliance surfaces (AI-literacy gate, consent log, user preferences, GDPR data-subject-rights routes) are plain Ktor routes + Exposed tables. No Postgres, no Row-Level Security, no Node/Auth.js — cross-tenant isolation is enforced by app-layer `WHERE user_id = ?` scoping plus a CI test.

**Tech Stack:** Kotlin 2.0.21, Ktor 3.0.1 (`ktor-server-*` + `ktor-client-cio` — all already declared), Exposed 0.55.0, SQLite (xerial 3.45.3.0), JUnit5 (`useJUnitPlatform`), React 19 + React Router 7 (Vite) for the frontend. **No new Gradle or npm dependencies are required.**

---

## Council & spec reconciliation (read before starting)

This plan deliberately deviates from the literal redesign spec. The deviations are locked by council `1779273122` (`.claude/council-cache/council-1779273122.md`, verdict FLAWED → corrected scope):

- **The spec's "Auth.js v5 + Drizzle" is a stale error.** Auth.js/NextAuth is a JavaScript library and cannot run in the Kotlin JVM. Spec §15 falsely claims it is "already shipped" — verified false, zero references in the repo. Spec §17 line 1015 itself records that "Postgres" was a wrong initial assumption corrected by a `TutorDb.kt` grep. Auth is built Kotlin-native.
- **Postgres + Row-Level Security are NOT in Gate 2.** All five council agents flagged bundling a SQLite→Postgres cutover into the auth gate as scope creep that risks the 871 live FSRS cards for no payoff at ~10 users. The SQLite→Postgres migration is deferred to its own later gate with its own council and a rehearsed, reversible data migration. Gate 2 stays on SQLite + Exposed.
- **The `accounts` table from spec §5.1 is dropped.** It is the NextAuth OAuth-provider join table; jarvis uses magic-link only, no OAuth.
- **Spec §8 "Enable RLS" pre-launch item** is satisfied at the Gate 2 layer by app-layer `user_id` scoping + the cross-tenant isolation CI test in Phase 3. The literal RLS feature ships (if ever) with the Postgres gate.

If a future reader thinks this plan "ignores the spec" — it does not. It follows council `1779273122`, which supersedes the contaminated spec sections, the same way BRIDGE already documents spec §6.4 as stale.

---

## File Structure

**Backend — new files** (`src/main/kotlin/jarvis/tutor/`):
- `MagicLinkTokens.kt` — `MagicLinkTokensTable` + `MagicLinkRepo` (issue / atomic single-use consume).
- `MagicLinkMailer.kt` — `MagicLinkMailer` interface, `ResendMailer` (real), `LoggingMailer` (dev fallback), pure `buildResendPayload` function.
- `AiLiteracy.kt` — `AiLiteracyConfirmationTable` + `AiLiteracyRepo`.
- `ConsentLog.kt` — `ConsentLogTable` + `ConsentRepo`.
- `UserPreferences.kt` — `UserPreferencesTable` + `UserPreferencesRepo`.

**Backend — new file** (`src/main/kotlin/jarvis/web/`):
- `AuthHelpers.kt` — `ApplicationCall.userIdOrNull()` / `requireUser()` consolidating the cookie-read + session-validate + 401 pattern for all new Gate 2 routes.

**Backend — modified files:**
- `src/main/kotlin/jarvis/tutor/Users.kt` — add `email` + `lang` columns; add `findByEmail` / `upsertByEmail`.
- `src/main/kotlin/jarvis/tutor/Sessions.kt` — hash the stored sid lookup key.
- `src/main/kotlin/jarvis/web/TutorRoutes.kt` — register new tables in the `SchemaUtils.createMissingTablesAndColumns(...)` list (~line 2109); add the `TutorContext` mailer field; add new routes (`/auth/request-link`, `/auth/verify`, `/api/v1/me/*`); add the AI-literacy gate to the LLM routes; neutralize auto-session owner auto-login.
- `src/main/kotlin/jarvis/web/WebMain.kt` — whitelist `/api/v1/me/` from the legacy `jarvis_auth` static gate.

**Frontend — new files** (`tutor-web/src/components/`):
- `LoginPage.tsx` — email-entry + "check your email" screen.
- `AiLiteracyGate.tsx` — the `/welcome/ai-literacy` first-login literacy screen (RO + EN).
- `SettingsMe.tsx` — surface #12, the Settings / Me tab (account, AI-literacy status, consent, preferences, GDPR export/delete).

**Frontend — modified files:**
- `tutor-web/src/main.tsx` — register `/login`, `/welcome/ai-literacy`, `/me` routes.
- `tutor-web/src/App.tsx` — add `pathname` branches for the new routes; redirect to `/login` when unauthenticated.
- `tutor-web/src/api.ts` (or wherever the API client lives) — add the new endpoint calls.

**Tests — new files** (`src/test/kotlin/jarvis/tutor/` and `.../web/`):
- `MagicLinkRepoTest.kt`, `SessionRepoHardeningTest.kt`, `MagicLinkMailerTest.kt`, `AuthRequestLinkRouteTest.kt`, `AuthVerifyRouteTest.kt`, `AiLiteracyGateTest.kt`, `MeRoutesTest.kt`, `CrossTenantIsolationTest.kt`.

**Docs — new directory:**
- `docs/compliance/` — the 15 compliance documents (Phase 4).

---

## PHASE 1 — Auth core (magic-link multi-user)

### Task 1: Add `email` + `lang` to UsersTable

**Files:**
- Modify: `src/main/kotlin/jarvis/tutor/Users.kt`
- Test: `src/test/kotlin/jarvis/tutor/UserRepoEmailTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/jarvis/tutor/UserRepoEmailTest.kt`:

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UserRepoEmailTest {
    private val dbDir = Files.createTempDirectory("userrepoemail")
    private val db = TutorDb.connect(dbDir.resolve("t.db").toString())
    init { transaction(db) { SchemaUtils.create(UsersTable) } }

    @AfterTest fun cleanup() { dbDir.toFile().deleteRecursively() }

    @Test fun `findByEmail returns the user when present`() {
        val repo = UserRepo(db)
        val u = User("01AAAAAAAAAAAAAAAAAAAAAAAA", "alex", UserScope.OWNER,
            Instant.now(), Instant.now(), email = "alex@example.com", lang = "ro")
        repo.insert(u)
        assertEquals("alex@example.com", repo.findByEmail("alex@example.com")?.email)
        assertNull(repo.findByEmail("nobody@example.com"))
    }

    @Test fun `upsertByEmail creates a new FRIEND user then returns the same on second call`() {
        val repo = UserRepo(db)
        val first = repo.upsertByEmail("friend@example.com", "en")
        assertEquals(UserScope.FRIEND, first.scope)
        assertEquals("en", first.lang)
        val second = repo.upsertByEmail("friend@example.com", "ro")
        assertEquals(first.id, second.id)            // same user, not a duplicate
        assertNotNull(repo.findByEmail("friend@example.com"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle test --tests "jarvis.tutor.UserRepoEmailTest"`
Expected: FAIL — `User` has no `email`/`lang` params; `UserRepo` has no `findByEmail`/`upsertByEmail`.

- [ ] **Step 3: Implement the changes in `Users.kt`**

Add the two columns to `UsersTable`, add the two fields to `User` (with defaults so existing positional `User(...)` callers still compile), update `toUser()`, and add the two repo methods. The full updated file:

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

enum class UserScope { OWNER, FRIEND }

data class User(
    val id: String,
    val name: String,
    val scope: UserScope,
    val createdAt: Instant,
    val lastSeenAt: Instant,
    val email: String? = null,
    val lang: String = "ro",
)

object UsersTable : Table("users") {
    val id = varchar("id", 26)
    val name = varchar("name", 64)
    val scope = varchar("scope", 16)
    val createdAt = timestamp("created_at")
    val lastSeenAt = timestamp("last_seen_at")
    val email = varchar("email", 320).nullable().uniqueIndex()
    val lang = varchar("lang", 8).default("ro")
    override val primaryKey = PrimaryKey(id)
}

class UserRepo(private val db: Database) {
    fun insert(u: User) = transaction(db) {
        UsersTable.insert {
            it[id] = u.id
            it[name] = u.name
            it[scope] = u.scope.name
            it[createdAt] = u.createdAt
            it[lastSeenAt] = u.lastSeenAt
            it[email] = u.email
            it[lang] = u.lang
        }
    }

    fun findById(id: String): User? = transaction(db) {
        UsersTable.selectAll().where { UsersTable.id eq id }.singleOrNull()?.toUser()
    }

    fun findByEmail(email: String): User? = transaction(db) {
        UsersTable.selectAll().where { UsersTable.email eq email }.singleOrNull()?.toUser()
    }

    /** Find the user with this email, or create a new FRIEND-scope user. */
    fun upsertByEmail(email: String, lang: String): User {
        findByEmail(email)?.let { return it }
        val now = Instant.now()
        val u = User(
            id = TutorTypes.ulid(),
            name = email.substringBefore('@'),
            scope = UserScope.FRIEND,
            createdAt = now,
            lastSeenAt = now,
            email = email,
            lang = lang,
        )
        insert(u)
        return u
    }

    fun touchLastSeen(id: String, ts: Instant) = transaction(db) {
        UsersTable.update({ UsersTable.id eq id }) { it[lastSeenAt] = ts }
    }

    private fun ResultRow.toUser() = User(
        id = this[UsersTable.id],
        name = this[UsersTable.name],
        scope = UserScope.valueOf(this[UsersTable.scope]),
        createdAt = this[UsersTable.createdAt],
        lastSeenAt = this[UsersTable.lastSeenAt],
        email = this[UsersTable.email],
        lang = this[UsersTable.lang],
    )
}
```

Note: `TutorTypes.ulid()` is the existing ULID generator used in `AuthSetupTest.kt`. If `UserRepo` cannot see it, qualify it as the test does (`TutorTypes.ulid()`); confirm the import path while implementing.

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle test --tests "jarvis.tutor.UserRepoEmailTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/Users.kt src/test/kotlin/jarvis/tutor/UserRepoEmailTest.kt
git commit -m "feat(auth): add email + lang to UsersTable + findByEmail/upsertByEmail"
```

---

### Task 2: MagicLinkTokensTable + MagicLinkRepo (single-use, TTL)

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/MagicLinkTokens.kt`
- Test: `src/test/kotlin/jarvis/tutor/MagicLinkRepoTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MagicLinkRepoTest {
    private val dbDir = Files.createTempDirectory("magiclink")
    private val db = TutorDb.connect(dbDir.resolve("t.db").toString())
    init { transaction(db) { SchemaUtils.create(MagicLinkTokensTable) } }

    @AfterTest fun cleanup() { dbDir.toFile().deleteRecursively() }

    @Test fun `issue then consume returns the claim`() {
        val repo = MagicLinkRepo(db)
        val raw = repo.issue("a@example.com", "ro", ttlSeconds = 900)
        val claim = repo.consume(raw)
        assertEquals("a@example.com", claim?.email)
        assertEquals("ro", claim?.lang)
    }

    @Test fun `a token consumes only once`() {
        val repo = MagicLinkRepo(db)
        val raw = repo.issue("b@example.com", "en", ttlSeconds = 900)
        assertEquals("b@example.com", repo.consume(raw)?.email)
        assertNull(repo.consume(raw))                 // second use rejected
    }

    @Test fun `an expired token does not consume`() {
        val repo = MagicLinkRepo(db)
        val raw = repo.issue("c@example.com", "ro", ttlSeconds = -1) // already expired
        assertNull(repo.consume(raw))
    }

    @Test fun `an unknown token returns null`() {
        assertNull(MagicLinkRepo(db).consume("deadbeef"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle test --tests "jarvis.tutor.MagicLinkRepoTest"`
Expected: FAIL — `MagicLinkTokensTable` / `MagicLinkRepo` do not exist.

- [ ] **Step 3: Create `MagicLinkTokens.kt`**

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant

object MagicLinkTokensTable : Table("magic_link_tokens") {
    val tokenHash = varchar("token_hash", 64)
    val email = varchar("email", 320)
    val lang = varchar("lang", 8)
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at")
    val consumedAt = timestamp("consumed_at").nullable()
    override val primaryKey = PrimaryKey(tokenHash)
}

data class MagicLinkClaim(val email: String, val lang: String)

class MagicLinkRepo(private val db: Database) {
    private val rng = SecureRandom()

    /** Generate a fresh raw token, store only its SHA-256 hash, return the raw. */
    fun issue(email: String, lang: String, ttlSeconds: Long): String {
        val raw = ByteArray(32).also { rng.nextBytes(it) }.toHex()
        val now = Instant.now()
        transaction(db) {
            MagicLinkTokensTable.insert {
                it[tokenHash] = sha256Hex(raw)
                it[MagicLinkTokensTable.email] = email
                it[MagicLinkTokensTable.lang] = lang
                it[createdAt] = now
                it[expiresAt] = now.plusSeconds(ttlSeconds)
                it[consumedAt] = null
            }
        }
        return raw
    }

    /**
     * Atomic single-use consume. The UPDATE both checks (unconsumed + unexpired)
     * and marks consumed in one statement; only a rows-affected count of 1 means
     * this caller won the token. A second call, or an expired token, affects 0 rows.
     */
    fun consume(raw: String): MagicLinkClaim? = transaction(db) {
        val hash = sha256Hex(raw)
        val now = Instant.now()
        val updated = MagicLinkTokensTable.update({
            (MagicLinkTokensTable.tokenHash eq hash) and
                MagicLinkTokensTable.consumedAt.isNull() and
                MagicLinkTokensTable.expiresAt.greater(now)
        }) {
            it[consumedAt] = now
        }
        if (updated != 1) return@transaction null
        MagicLinkTokensTable.selectAll()
            .where { MagicLinkTokensTable.tokenHash eq hash }
            .single()
            .let { MagicLinkClaim(it[MagicLinkTokensTable.email], it[MagicLinkTokensTable.lang]) }
    }

    private fun sha256Hex(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).toHex()
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle test --tests "jarvis.tutor.MagicLinkRepoTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/MagicLinkTokens.kt src/test/kotlin/jarvis/tutor/MagicLinkRepoTest.kt
git commit -m "feat(auth): magic_link_tokens table + single-use TTL consume"
```

---

### Task 3: Register new tables in the schema-creation list

**Files:**
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt` (the `SchemaUtils.createMissingTablesAndColumns(...)` call, ~line 2109-2131)

- [ ] **Step 1: Add the new table to the list**

In `installTutorContext()`, the call currently lists `UsersTable, TokensTable, SessionsTable, ...`. Add `MagicLinkTokensTable` (and, for later phases, leave a clear spot). Insert after `SessionsTable`:

```kotlin
        SchemaUtils.createMissingTablesAndColumns(
            UsersTable,
            TokensTable,
            SessionsTable,
            MagicLinkTokensTable,        // Gate 2 — magic-link auth
            TasksTable,
            // ... rest unchanged ...
        )
```

`createMissingTablesAndColumns` is idempotent and also adds missing columns — so the new `email`/`lang` columns on `UsersTable` (Task 1) are created on the next boot automatically. No data migration needed.

- [ ] **Step 2: Verify it compiles**

Run: `gradle compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/jarvis/web/TutorRoutes.kt
git commit -m "chore(auth): register magic_link_tokens in schema bootstrap"
```

---

### Task 4: Harden SessionRepo — store the hash of the sid, not the raw sid

**Files:**
- Modify: `src/main/kotlin/jarvis/tutor/Sessions.kt`
- Test: `src/test/kotlin/jarvis/tutor/SessionRepoHardeningTest.kt`

Rationale: today `SessionsTable.sid` stores the raw bearer session id. A leaked DB file or backup yields directly-usable live sessions. Fix: store `sha256(rawSid)` as the lookup key; the client cookie keeps the raw value. No schema change — both raw and hash are 64 hex chars.

- [ ] **Step 1: Write the failing test**

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class SessionRepoHardeningTest {
    private val dbDir = Files.createTempDirectory("sessionhard")
    private val db = TutorDb.connect(dbDir.resolve("t.db").toString())
    init { transaction(db) { SchemaUtils.create(SessionsTable) } }

    @AfterTest fun cleanup() { dbDir.toFile().deleteRecursively() }

    @Test fun `create returns a raw sid that resolves back to the user`() {
        val repo = SessionRepo(db)
        val raw = repo.create("user-1", ttlSeconds = 3600)
        assertEquals("user-1", repo.findUserId(raw))
    }

    @Test fun `the stored sid value is NOT the raw sid`() {
        val repo = SessionRepo(db)
        val raw = repo.create("user-2", ttlSeconds = 3600)
        val stored = transaction(db) {
            SessionsTable.selectAll().single()[SessionsTable.sid]
        }
        assertNotEquals(raw, stored)              // DB holds the hash, not the bearer token
    }

    @Test fun `a wrong sid does not resolve`() {
        val repo = SessionRepo(db)
        repo.create("user-3", ttlSeconds = 3600)
        assertNull(repo.findUserId("not-a-real-sid"))
    }

    @Test fun `revoke kills the session`() {
        val repo = SessionRepo(db)
        val raw = repo.create("user-4", ttlSeconds = 3600)
        repo.revoke(raw)
        assertNull(repo.findUserId(raw))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle test --tests "jarvis.tutor.SessionRepoHardeningTest"`
Expected: FAIL on `the stored sid value is NOT the raw sid` (today raw == stored).

- [ ] **Step 3: Implement the hashing in `Sessions.kt`**

Replace the `SessionRepo` class body (the `SessionsTable` object is unchanged):

```kotlin
class SessionRepo(private val db: Database) {
    private val rng = SecureRandom()

    /** Mint a fresh session; store sha256(sid); return the raw sid for the cookie. */
    fun create(userId: String, ttlSeconds: Long): String {
        val rawSid = ByteArray(32).also { rng.nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        val now = Instant.now()
        transaction(db) {
            SessionsTable.insert {
                it[SessionsTable.sid] = sha256Hex(rawSid)
                it[SessionsTable.userId] = userId
                it[SessionsTable.createdAt] = now
                it[SessionsTable.expiresAt] = now.plusSeconds(ttlSeconds)
            }
        }
        return rawSid
    }

    fun findUserId(rawSid: String): String? = transaction(db) {
        val now = Instant.now()
        SessionsTable.selectAll().where {
            (SessionsTable.sid eq sha256Hex(rawSid)) and (SessionsTable.expiresAt.greater(now))
        }.singleOrNull()?.get(SessionsTable.userId)
    }

    fun revoke(rawSid: String) = transaction(db) {
        SessionsTable.deleteWhere { SessionsTable.sid eq sha256Hex(rawSid) }
    }

    private fun sha256Hex(s: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle test --tests "jarvis.tutor.SessionRepoHardeningTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Run the existing auth test to check for regressions**

Run: `gradle test --tests "jarvis.tutor.AuthSetupTest"`
Expected: PASS — `AuthSetupTest` exercises `/auth/setup`, which calls `SessionRepo.create`/`findUserId`; the raw↔hash round-trip is transparent to callers. If it fails, the failure is a real regression — fix before committing.

> **Deploy note (record in the commit body):** existing live sessions in prod store raw sids; after this deploys, those rows will not match the new hashed lookup, so all current users are logged out once and must re-authenticate. This is a one-time, expected effect — not a bug.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/Sessions.kt src/test/kotlin/jarvis/tutor/SessionRepoHardeningTest.kt
git commit -m "fix(auth): store sha256(sid) not raw sid — leaked-backup hardening"
```

---

### Task 5: MagicLinkMailer — Resend HTTP delivery + dev fallback

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/MagicLinkMailer.kt`
- Test: `src/test/kotlin/jarvis/tutor/MagicLinkMailerTest.kt`

Design: a `MagicLinkMailer` interface so routes depend on an abstraction (route tests inject a fake — this avoids adding `ktor-client-mock`). `ResendMailer` is the real impl; `LoggingMailer` is the dev fallback used when `RESEND_API_KEY` is unset (it logs the link so a developer can click it). `buildResendPayload` is a pure function so the payload shape is unit-testable without any HTTP.

- [ ] **Step 1: Write the failing test**

```kotlin
package jarvis.tutor

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MagicLinkMailerTest {
    @Test fun `buildResendPayload targets the recipient and embeds the link`() {
        val payload = buildResendPayload(
            from = "jarvis@corgflix.duckdns.org",
            to = "student@example.com",
            link = "https://corgflix.duckdns.org/tutor/auth/verify?token=abc",
            lang = "ro",
        )
        assertEquals("student@example.com", payload.to.single())
        assertTrue(payload.html.contains("https://corgflix.duckdns.org/tutor/auth/verify?token=abc"))
        assertTrue(payload.subject.isNotBlank())
    }

    @Test fun `buildResendPayload uses Romanian copy for ro and English for en`() {
        val ro = buildResendPayload("f@x.io", "t@x.io", "https://x.io/l", "ro")
        val en = buildResendPayload("f@x.io", "t@x.io", "https://x.io/l", "en")
        assertTrue(ro.html.contains("Conectare") || ro.subject.contains("Conectare"))
        assertTrue(en.html.contains("Sign in") || en.subject.contains("Sign in"))
    }

    @Test fun `LoggingMailer never throws and reports not-sent`() = runBlocking {
        val result = LoggingMailer().send("t@x.io", "https://x.io/l", "ro")
        assertEquals(MailResult.LOGGED_ONLY, result)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle test --tests "jarvis.tutor.MagicLinkMailerTest"`
Expected: FAIL — none of these symbols exist.

- [ ] **Step 3: Create `MagicLinkMailer.kt`**

```kotlin
package jarvis.tutor

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

enum class MailResult { SENT, LOGGED_ONLY, FAILED }

@Serializable
data class ResendPayload(
    val from: String,
    val to: List<String>,
    val subject: String,
    val html: String,
)

/** Pure builder — unit-tested without any network. */
fun buildResendPayload(from: String, to: String, link: String, lang: String): ResendPayload {
    val subject = if (lang == "ro") "Conectare la Jarvis Tutor" else "Sign in to Jarvis Tutor"
    val body = if (lang == "ro") {
        "Apasă linkul pentru a te conecta (expiră în 15 minute):"
    } else {
        "Click the link to sign in (expires in 15 minutes):"
    }
    val html = """<div style="font-family:monospace">
        |<p>$body</p>
        |<p><a href="$link">$link</a></p>
        |</div>""".trimMargin()
    return ResendPayload(from = from, to = listOf(to), subject = subject, html = html)
}

interface MagicLinkMailer {
    suspend fun send(toEmail: String, link: String, lang: String): MailResult
}

/** Dev fallback: logs the link to stdout so a developer can click it. */
class LoggingMailer : MagicLinkMailer {
    override suspend fun send(toEmail: String, link: String, lang: String): MailResult {
        println("[MagicLink] (dev, no RESEND_API_KEY) link for $toEmail -> $link")
        return MailResult.LOGGED_ONLY
    }
}

/** Real delivery via the Resend REST API, using the ktor-client already on the classpath. */
class ResendMailer(
    private val apiKey: String,
    private val fromAddress: String,
    private val http: HttpClient,
) : MagicLinkMailer {
    override suspend fun send(toEmail: String, link: String, lang: String): MailResult {
        val payload = buildResendPayload(fromAddress, toEmail, link, lang)
        return try {
            val resp: HttpResponse = http.post("https://api.resend.com/emails") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            if (resp.status.isSuccess()) MailResult.SENT else MailResult.FAILED
        } catch (e: Exception) {
            println("[MagicLink] Resend send failed for $toEmail: ${e.message}")
            MailResult.FAILED
        }
    }
}
```

> The ktor-client used by `ResendMailer` must have `ContentNegotiation` + JSON installed for `setBody(payload)` to serialize. The codebase already declares `ktor-client-content-negotiation` and `ktor-serialization-kotlinx-json`. Task 6 constructs the client (or reuses an existing one) with `install(ContentNegotiation) { json() }`.

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle test --tests "jarvis.tutor.MagicLinkMailerTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/MagicLinkMailer.kt src/test/kotlin/jarvis/tutor/MagicLinkMailerTest.kt
git commit -m "feat(auth): MagicLinkMailer — Resend HTTP delivery + dev logging fallback"
```

---

### Task 6: Wire the mailer into TutorContext + `POST /auth/request-link`

**Files:**
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt` (the `TutorContext` definition; `installTutorContext()`; add the route inside `routing {}`)
- Test: `src/test/kotlin/jarvis/web/AuthRequestLinkRouteTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package jarvis.web

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import jarvis.tutor.*
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthRequestLinkRouteTest {
    @Test fun `request-link issues a token and calls the mailer, always 200`() = testApplication {
        val dbDir = Files.createTempDirectory("reqlink")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) { SchemaUtils.create(UsersTable, MagicLinkTokensTable) }
        val fake = FakeMailer()
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = fake))
            installTutorRoutes()
        }
        val r = client.post("/auth/request-link") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"student@example.com","lang":"ro"}""")
        }
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(r.bodyAsText().contains("ok"))
        assertEquals(1, fake.sent.size)
        assertEquals("student@example.com", fake.sent.single().first)
        assertTrue(fake.sent.single().second.contains("/auth/verify?token="))
    }

    @Test fun `a malformed email is rejected with 400`() = testApplication {
        val dbDir = Files.createTempDirectory("reqlink2")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) { SchemaUtils.create(UsersTable, MagicLinkTokensTable) }
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installTutorRoutes()
        }
        val r = client.post("/auth/request-link") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"not-an-email","lang":"ro"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }
}

/** Records send() calls instead of hitting the network. */
class FakeMailer : MagicLinkMailer {
    val sent = mutableListOf<Pair<String, String>>()  // (email, link)
    override suspend fun send(toEmail: String, link: String, lang: String): MailResult {
        sent += toEmail to link
        return MailResult.SENT
    }
}
```

> `testTutorContext(db, dbDir, mailer)` is a small test helper. If a builder for `TutorContext` does not already exist for tests, add one in this test file or a shared `src/test/kotlin/jarvis/web/TestContext.kt` that constructs a `TutorContext` with the same fields `installTutorContext()` uses, taking `mailer` as a parameter. While implementing, read the actual `TutorContext` definition first and match its fields.

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle test --tests "jarvis.web.AuthRequestLinkRouteTest"`
Expected: FAIL — `TutorContext` has no `mailer` field; `/auth/request-link` route does not exist.

- [ ] **Step 3: Add the `mailer` field to `TutorContext` + construct it**

Read the current `TutorContext` declaration and `installTutorContext()` in `TutorRoutes.kt`. Add a `mailer: MagicLinkMailer` field to the `TutorContext` data class. In `installTutorContext()`, after the schema block, construct it:

```kotlin
val resendKey = System.getenv("RESEND_API_KEY")
    ?: io.github.cdimascio.dotenv.dotenv { ignoreIfMissing = true }["RESEND_API_KEY"]
val mailFrom = System.getenv("JARVIS_MAGIC_LINK_FROM") ?: "jarvis@corgflix.duckdns.org"
val mailer: MagicLinkMailer = if (!resendKey.isNullOrBlank()) {
    val mailClient = io.ktor.client.HttpClient(io.ktor.client.engine.cio.CIO) {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            io.ktor.serialization.kotlinx.json.json()
        }
    }
    ResendMailer(resendKey, mailFrom, mailClient)
} else {
    LoggingMailer()
}
```

Pass `mailer` into the `TutorContext(...)` constructor. (Match the dotenv access pattern used in `jarvis/ApiKey.kt`.)

- [ ] **Step 4: Add the `POST /auth/request-link` route**

Inside the `routing {}` block in `installTutorRoutes()`, near the existing `get("/auth/setup")`, add:

```kotlin
post("/auth/request-link") {
    val ctx = application.attributes.getOrNull(TutorContextKey)
        ?: return@post call.respond(HttpStatusCode.InternalServerError, "no ctx")
    val body = call.receive<RequestLinkBody>()
    val email = body.email.trim().lowercase()
    if (!EMAIL_REGEX.matches(email)) {
        return@post call.respond(HttpStatusCode.BadRequest, """{"error":"invalid email"}""")
    }
    val lang = if (body.lang == "en") "en" else "ro"
    val baseUrl = System.getenv("JARVIS_PUBLIC_BASE_URL") ?: "https://corgflix.duckdns.org"
    val rawToken = MagicLinkRepo(ctx.db).issue(email, lang, ttlSeconds = 15 * 60)
    val link = "$baseUrl/tutor/auth/verify?token=$rawToken"
    ctx.mailer.send(email, link, lang)
    // Always 200 — never reveal whether an account exists (no enumeration).
    call.respond(HttpStatusCode.OK, """{"ok":true}""")
}
```

Add these top-level declarations in `TutorRoutes.kt` (or a small adjacent file):

```kotlin
@kotlinx.serialization.Serializable
data class RequestLinkBody(val email: String, val lang: String = "ro")

val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
```

> Route path note: `/auth/request-link` sits under `/auth/*`, which `WebMain.kt`'s static-gate whitelist already exempts — so it is reachable without the legacy `jarvis_auth` token. The link path `/tutor/auth/verify` is served by the `/auth/verify` route in Task 7 (the `/tutor` prefix is the SPA basename; the Ktor route is registered at `/auth/verify`). Confirm the host's URL prefix while implementing — if the backend serves `/auth/verify` without a `/tutor` prefix, set the link to `$baseUrl/auth/verify?token=...`.

- [ ] **Step 5: Run test to verify it passes**

Run: `gradle test --tests "jarvis.web.AuthRequestLinkRouteTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/jarvis/web/TutorRoutes.kt src/test/kotlin/jarvis/web/AuthRequestLinkRouteTest.kt
git commit -m "feat(auth): POST /auth/request-link — issue + email a magic link"
```

---

### Task 7: `GET /auth/verify` — consume token, mint session, redirect

**Files:**
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt` (add the route)
- Test: `src/test/kotlin/jarvis/web/AuthVerifyRouteTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package jarvis.web

import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import jarvis.tutor.*
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthVerifyRouteTest {
    @Test fun `a valid token mints a session, creates the user, and redirects`() = testApplication {
        val dbDir = Files.createTempDirectory("verify")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) { SchemaUtils.create(UsersTable, MagicLinkTokensTable, SessionsTable) }
        val raw = MagicLinkRepo(db).issue("new@example.com", "ro", 900)
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installTutorRoutes()
        }
        val c = createClient { install(HttpCookies); followRedirects = false }
        val r = c.get("/auth/verify?token=$raw")
        assertEquals(HttpStatusCode.Found, r.status)
        val cookies = r.headers.getAll(HttpHeaders.SetCookie) ?: emptyList()
        assertTrue(cookies.any { it.startsWith("jarvis_session=") && it.contains("HttpOnly") })
        assertNotNull(UserRepo(db).findByEmail("new@example.com"))
    }

    @Test fun `an already-consumed token does not mint a session`() = testApplication {
        val dbDir = Files.createTempDirectory("verify2")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) { SchemaUtils.create(UsersTable, MagicLinkTokensTable, SessionsTable) }
        val raw = MagicLinkRepo(db).issue("x@example.com", "ro", 900)
        MagicLinkRepo(db).consume(raw)             // burn it first
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installTutorRoutes()
        }
        val c = createClient { install(HttpCookies); followRedirects = false }
        val r = c.get("/auth/verify?token=$raw")
        val cookies = r.headers.getAll(HttpHeaders.SetCookie) ?: emptyList()
        assertTrue(cookies.none { it.startsWith("jarvis_session=") })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle test --tests "jarvis.web.AuthVerifyRouteTest"`
Expected: FAIL — `/auth/verify` route does not exist.

- [ ] **Step 3: Add the `GET /auth/verify` route**

Inside `routing {}`, model the cookie code on the existing `get("/auth/setup")` (it shows the exact `Cookie(...)` shape used for `jarvis_session`). `rng` is the same `SecureRandom` already in scope at the routes level.

```kotlin
get("/auth/verify") {
    val ctx = application.attributes.getOrNull(TutorContextKey)
        ?: return@get call.respond(HttpStatusCode.InternalServerError, "no ctx")
    val raw = call.request.queryParameters["token"]
    if (raw.isNullOrBlank()) {
        return@get call.respondRedirect("/tutor/login?error=missing")
    }
    val claim = MagicLinkRepo(ctx.db).consume(raw)
        ?: return@get call.respondRedirect("/tutor/login?error=expired")
    val user = UserRepo(ctx.db).upsertByEmail(claim.email, claim.lang)
    UserRepo(ctx.db).touchLastSeen(user.id, java.time.Instant.now())
    // Fresh sid every time — never adopt a client-supplied value (kills session fixation).
    val sid = SessionRepo(ctx.db).create(user.id, ttlSeconds = 60L * 60 * 24 * 14)
    val csrf = ByteArray(16).also { rng.nextBytes(it) }.joinToString("") { "%02x".format(it) }
    call.response.cookies.append(
        Cookie("jarvis_session", sid, httpOnly = true, secure = true,
            extensions = mapOf("SameSite" to "Strict"), path = "/", maxAge = 60 * 60 * 24 * 14),
    )
    call.response.cookies.append(
        Cookie("csrf", csrf, httpOnly = false, secure = true,
            extensions = mapOf("SameSite" to "Strict"), path = "/", maxAge = 60 * 60 * 24 * 14),
    )
    call.respondRedirect("/tutor/")
}
```

> Match the exact `Cookie(...)` argument shape used by the existing `get("/auth/setup")` route — if `/auth/setup` sets the `csrf` cookie differently, copy that. The point is parity with the working bootstrap route.

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle test --tests "jarvis.web.AuthVerifyRouteTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/web/TutorRoutes.kt src/test/kotlin/jarvis/web/AuthVerifyRouteTest.kt
git commit -m "feat(auth): GET /auth/verify — consume magic link, mint session, redirect"
```

---

### Task 8: AuthHelpers — consolidate the session-validate pattern for new routes

**Files:**
- Create: `src/main/kotlin/jarvis/web/AuthHelpers.kt`
- Test: `src/test/kotlin/jarvis/web/AuthHelpersTest.kt`

The repo reads `call.request.cookies["jarvis_session"]` + `SessionRepo.findUserId` at ~35 sites. A full migration of all 35 is out of Gate 2's scope (and risky). This task adds ONE helper that all NEW Gate 2 routes (`/api/v1/me/*`, the AI-literacy gate) use. Existing routes are left untouched and keep working.

- [ ] **Step 1: Write the failing test**

```kotlin
package jarvis.web

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.testApplication
import jarvis.tutor.*
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthHelpersTest {
    @Test fun `requireUser rejects a missing session with 401`() = testApplication {
        val dbDir = Files.createTempDirectory("authhelp")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) { SchemaUtils.create(UsersTable, SessionsTable) }
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            routing { get("/probe") { requireUser { uid -> call.respondText(uid) } } }
        }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/probe").status)
    }

    @Test fun `requireUser passes the user id through for a valid session`() = testApplication {
        val dbDir = Files.createTempDirectory("authhelp2")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) { SchemaUtils.create(UsersTable, SessionsTable) }
        UserRepo(db).insert(User(TutorTypes.ulid(), "u", UserScope.OWNER, Instant.now(), Instant.now()))
        val uid = UserRepo(db).let { transaction(db) { UsersTable.selectAll().single()[UsersTable.id] } }
        val sid = SessionRepo(db).create(uid, 3600)
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            routing { get("/probe") { requireUser { u -> call.respondText(u) } } }
        }
        val r = client.get("/probe") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(uid, r.bodyAsText())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle test --tests "jarvis.web.AuthHelpersTest"`
Expected: FAIL — `requireUser` does not exist.

- [ ] **Step 3: Create `AuthHelpers.kt`**

```kotlin
package jarvis.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import jarvis.tutor.SessionRepo

/** Resolve the user id from the jarvis_session cookie, or null. */
fun ApplicationCall.userIdOrNull(): String? {
    val ctx = application.attributes.getOrNull(TutorContextKey) ?: return null
    val sid = request.cookies["jarvis_session"] ?: return null
    return SessionRepo(ctx.db).findUserId(sid)
}

/** Run [block] with the authenticated user id, or respond 401 and skip it. */
suspend fun ApplicationCall.requireUser(block: suspend (userId: String) -> Unit) {
    val uid = userIdOrNull()
    if (uid == null) {
        respond(HttpStatusCode.Unauthorized, """{"error":"not authenticated"}""")
        return
    }
    block(uid)
}
```

> Confirm the `TutorContextKey` symbol and the `TutorContext.db` field name while implementing (both are used by existing routes in `TutorRoutes.kt` — match them exactly).

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle test --tests "jarvis.web.AuthHelpersTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/web/AuthHelpers.kt src/test/kotlin/jarvis/web/AuthHelpersTest.kt
git commit -m "feat(auth): requireUser/userIdOrNull helper for Gate 2 routes"
```

---

### Task 9: Neutralize auto-session owner auto-login

**Files:**
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt` (the `/api/v1/tutor/auto-session` route, ~line 200-231)
- Test: `src/test/kotlin/jarvis/web/AutoSessionGuardTest.kt`

Today `/api/v1/tutor/auto-session` mints a session for the OWNER for anyone who calls it — acceptable single-user, a hole once the app is multi-user (any visitor becomes OWNER). Gate 2 change: `auto-session` returns the CURRENT session's identity if one exists, but does NOT mint a new OWNER session for an unauthenticated caller — it returns 401 so the frontend routes to `/login`.

- [ ] **Step 1: Write the failing test**

```kotlin
package jarvis.web

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import jarvis.tutor.*
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class AutoSessionGuardTest {
    @Test fun `auto-session no longer auto-logs-in an unauthenticated caller`() = testApplication {
        val dbDir = Files.createTempDirectory("autosess")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) { SchemaUtils.create(UsersTable, SessionsTable, MagicLinkTokensTable) }
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installTutorRoutes()
        }
        val r = client.get("/api/v1/tutor/auto-session")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle test --tests "jarvis.web.AutoSessionGuardTest"`
Expected: FAIL — current `auto-session` mints an OWNER session and returns 200.

- [ ] **Step 3: Modify the `auto-session` route**

Read the current `/api/v1/tutor/auto-session` handler. Replace the "mint a session for the OWNER" branch so that when there is no valid `jarvis_session` cookie, it responds `401`. When a valid session exists, keep the existing behavior (return the session/csrf state). Concretely, the handler body becomes:

```kotlin
get("/api/v1/tutor/auto-session") {
    val ctx = application.attributes.getOrNull(TutorContextKey)
        ?: return@get call.respond(HttpStatusCode.InternalServerError, "no ctx")
    val sid = call.request.cookies["jarvis_session"]
    val uid = sid?.let { SessionRepo(ctx.db).findUserId(it) }
    if (uid == null) {
        return@get call.respond(HttpStatusCode.Unauthorized, """{"error":"login required"}""")
    }
    // existing authenticated-path response (csrf token refresh etc.) stays here
}
```

Preserve whatever the existing authenticated branch returns (CSRF token, user info) — only the unauthenticated branch changes from "mint OWNER session" to "401".

> If a developer-only frictionless login is still wanted, gate it behind an explicit `JARVIS_DEV_AUTOLOGIN=1` env check, never on by default. This keeps prod safe while preserving local dev convenience. Add it only if needed.

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle test --tests "jarvis.web.AutoSessionGuardTest"`
Expected: PASS.

- [ ] **Step 5: Run the full backend suite — this route is load-bearing**

Run: `gradle test`
Expected: PASS. The frontend depends on `auto-session`; some existing tests may assume the old auto-login. Any failure here is a real interaction to resolve — update the affected tests to seed a session first (the new correct behavior), do not weaken the guard.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/jarvis/web/TutorRoutes.kt src/test/kotlin/jarvis/web/AutoSessionGuardTest.kt
git commit -m "fix(auth): auto-session no longer auto-logs-in as OWNER (multi-user safety)"
```

---

### Task 10: Frontend — LoginPage + redirect-when-unauthenticated

**Files:**
- Create: `tutor-web/src/components/LoginPage.tsx`
- Modify: `tutor-web/src/main.tsx` (add `/login` route), `tutor-web/src/App.tsx` (pathname branch + unauthenticated redirect)
- Test: `tutor-web/src/components/LoginPage.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { LoginPage } from "./LoginPage";

describe("LoginPage", () => {
  it("posts the email to /auth/request-link and shows the check-email state", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ ok: true }) });
    vi.stubGlobal("fetch", fetchMock);
    render(<LoginPage />);
    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: "a@b.io" } });
    fireEvent.click(screen.getByRole("button", { name: /send|trimite/i }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("/auth/request-link"), expect.objectContaining({ method: "POST" }),
    ));
    expect(screen.getByText(/check your email|verifică/i)).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd tutor-web && npx vitest run src/components/LoginPage.test.tsx`
Expected: FAIL — `LoginPage` does not exist.

- [ ] **Step 3: Create `LoginPage.tsx`**

A brutalist-styled (JetBrains Mono, Ink/Paper/Accent palette — follow the existing `theme.ts` + sibling components in `tutor-web/src/components/`) screen with: an `<h1>`, an email `<input>` with an associated `<label htmlFor>`, a submit `<button>`, and a post-submit "check your email" message. On submit:

```tsx
const res = await fetch("/api/.." /* "/auth/request-link" — absolute path, no /tutor prefix needed for API */, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ email, lang }),
});
```

Use `"/auth/request-link"` as the path (the backend route registered in Task 6). After a resolved response, switch to the "check your email" state regardless of status detail (the backend always returns 200; show the same message even on network error to avoid leaking enumeration). Keep the component self-contained — no router hooks required.

- [ ] **Step 4: Wire the route**

In `tutor-web/src/main.tsx`, add alongside the existing `<Route>` entries:

```tsx
<Route path="/login" element={<App />} />
```

In `tutor-web/src/App.tsx`, import `LoginPage` and add a `pathname` branch at the TOP of the `<main>` switch (before the session-dependent branches):

```tsx
{here.pathname === "/login"
  ? <LoginPage />
  : /* ...existing branches... */ }
```

Then add the unauthenticated redirect: where `App.tsx` currently calls `auto-session` and gets a result, if `auto-session` now returns 401 (Task 9) and `here.pathname !== "/login"`, navigate to `/login`. Use the existing router navigation pattern already in `App.tsx` (it already uses `useLocation`; pair it with `useNavigate` from `react-router-dom`):

```tsx
// after the auto-session fetch resolves:
if (autoSessionStatus === 401 && here.pathname !== "/login"
    && here.pathname !== "/welcome/ai-literacy") {
  navigate("/login");
}
```

- [ ] **Step 5: Run test + typecheck**

Run: `cd tutor-web && npx vitest run src/components/LoginPage.test.tsx && npx tsc --noEmit`
Expected: test PASS, tsc clean.

- [ ] **Step 6: Commit**

```bash
git add tutor-web/src/components/LoginPage.tsx tutor-web/src/components/LoginPage.test.tsx tutor-web/src/main.tsx tutor-web/src/App.tsx
git commit -m "feat(auth): LoginPage + redirect-to-login when unauthenticated"
```

---

## PHASE 2 — Compliance surfaces

### Task 11: AiLiteracyConfirmationTable + AiLiteracyRepo

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/AiLiteracy.kt`
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt` (register the table)
- Test: `src/test/kotlin/jarvis/tutor/AiLiteracyRepoTest.kt`

`AI_LITERACY_VERSION` is the current literacy-text version (bump it when the literacy content materially changes — a user must re-confirm a new version).

- [ ] **Step 1: Write the failing test**

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiLiteracyRepoTest {
    private val dbDir = Files.createTempDirectory("ailit")
    private val db = TutorDb.connect(dbDir.resolve("t.db").toString())
    init { transaction(db) { SchemaUtils.create(AiLiteracyConfirmationTable) } }
    @AfterTest fun cleanup() { dbDir.toFile().deleteRecursively() }

    @Test fun `a user is unconfirmed until they confirm the current version`() {
        val repo = AiLiteracyRepo(db)
        assertFalse(repo.hasConfirmedCurrent("u1"))
        repo.confirm("u1", AI_LITERACY_VERSION, "ro")
        assertTrue(repo.hasConfirmedCurrent("u1"))
    }

    @Test fun `confirming an old version does not satisfy the current gate`() {
        val repo = AiLiteracyRepo(db)
        repo.confirm("u2", "v0.0-old", "ro")
        assertFalse(repo.hasConfirmedCurrent("u2"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle test --tests "jarvis.tutor.AiLiteracyRepoTest"`
Expected: FAIL — symbols do not exist.

- [ ] **Step 3: Create `AiLiteracy.kt`**

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

const val AI_LITERACY_VERSION = "tutor-v1.0"

object AiLiteracyConfirmationTable : Table("ai_literacy_confirmation") {
    val userId = varchar("user_id", 26)
    val version = varchar("version", 32)
    val lang = varchar("lang", 8)
    val confirmedAt = timestamp("confirmed_at")
    override val primaryKey = PrimaryKey(userId, version)
}

class AiLiteracyRepo(private val db: Database) {
    fun confirm(userId: String, version: String, lang: String) = transaction(db) {
        val exists = AiLiteracyConfirmationTable.selectAll().where {
            (AiLiteracyConfirmationTable.userId eq userId) and
                (AiLiteracyConfirmationTable.version eq version)
        }.any()
        if (!exists) {
            AiLiteracyConfirmationTable.insert {
                it[AiLiteracyConfirmationTable.userId] = userId
                it[AiLiteracyConfirmationTable.version] = version
                it[AiLiteracyConfirmationTable.lang] = lang
                it[confirmedAt] = Instant.now()
            }
        }
    }

    fun hasConfirmedCurrent(userId: String): Boolean = transaction(db) {
        AiLiteracyConfirmationTable.selectAll().where {
            (AiLiteracyConfirmationTable.userId eq userId) and
                (AiLiteracyConfirmationTable.version eq AI_LITERACY_VERSION)
        }.any()
    }
}
```

- [ ] **Step 4: Register the table** — add `AiLiteracyConfirmationTable` to the `SchemaUtils.createMissingTablesAndColumns(...)` list in `TutorRoutes.kt`.

- [ ] **Step 5: Run test to verify it passes**

Run: `gradle test --tests "jarvis.tutor.AiLiteracyRepoTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/AiLiteracy.kt src/main/kotlin/jarvis/web/TutorRoutes.kt src/test/kotlin/jarvis/tutor/AiLiteracyRepoTest.kt
git commit -m "feat(compliance): ai_literacy_confirmation table + versioned repo"
```

---

### Task 12: `POST /api/v1/me/ai-literacy/confirm` route

**Files:**
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt` (add route), `src/main/kotlin/jarvis/web/WebMain.kt` (whitelist `/api/v1/me/`)
- Test: `src/test/kotlin/jarvis/web/AiLiteracyConfirmRouteTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package jarvis.web

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import jarvis.tutor.*
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiLiteracyConfirmRouteTest {
    @Test fun `confirm records the confirmation for the session user`() = testApplication {
        val dbDir = Files.createTempDirectory("ailitroute")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) { SchemaUtils.create(UsersTable, SessionsTable, AiLiteracyConfirmationTable) }
        val uid = TutorTypes.ulid()
        UserRepo(db).insert(User(uid, "u", UserScope.FRIEND, Instant.now(), Instant.now()))
        val sid = SessionRepo(db).create(uid, 3600)
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installTutorRoutes()
        }
        val csrf = "test-csrf"
        val r = client.post("/api/v1/me/ai-literacy/confirm") {
            header("Cookie", "jarvis_session=$sid; csrf=$csrf")
            header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"lang":"ro"}""")
        }
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(AiLiteracyRepo(db).hasConfirmedCurrent(uid))
    }

    @Test fun `confirm without a session is 401`() = testApplication {
        val dbDir = Files.createTempDirectory("ailitroute2")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) { SchemaUtils.create(UsersTable, SessionsTable, AiLiteracyConfirmationTable) }
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installTutorRoutes()
        }
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/v1/me/ai-literacy/confirm").status)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle test --tests "jarvis.web.AiLiteracyConfirmRouteTest"`
Expected: FAIL — route does not exist.

- [ ] **Step 3: Whitelist `/api/v1/me/` in `WebMain.kt`**

In `WebMain.kt`, the static-gate interceptor whitelists public path prefixes. Add `/api/v1/me/` to that whitelist list (these routes do their own `jarvis_session` check, so the legacy `jarvis_auth` token must not be required). Match the existing whitelist syntax exactly.

- [ ] **Step 4: Add the route** — inside `routing {}`:

```kotlin
post("/api/v1/me/ai-literacy/confirm") {
    val ctx = application.attributes.getOrNull(TutorContextKey)
        ?: return@post call.respond(HttpStatusCode.InternalServerError, "no ctx")
    call.csrfProtect {
        call.requireUser { uid ->
            val body = runCatching { call.receive<ConfirmLiteracyBody>() }.getOrNull()
            val lang = if (body?.lang == "en") "en" else "ro"
            AiLiteracyRepo(ctx.db).confirm(uid, AI_LITERACY_VERSION, lang)
            call.respond(HttpStatusCode.OK, """{"ok":true}""")
        }
    }
}
```

Add the body type: `@kotlinx.serialization.Serializable data class ConfirmLiteracyBody(val lang: String = "ro")`.
`csrfProtect` is the existing helper in `jarvis/tutor/Csrf.kt`; `requireUser` is from Task 8.

- [ ] **Step 5: Run test to verify it passes**

Run: `gradle test --tests "jarvis.web.AiLiteracyConfirmRouteTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/jarvis/web/TutorRoutes.kt src/main/kotlin/jarvis/web/WebMain.kt src/test/kotlin/jarvis/web/AiLiteracyConfirmRouteTest.kt
git commit -m "feat(compliance): POST /api/v1/me/ai-literacy/confirm"
```

---

### Task 13: AI-literacy gate on the LLM endpoints

**Files:**
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt` (`POST /api/v1/sidekick/ask` ~line 1373, `POST /api/v1/sensor/screenshot` ~line 241)
- Test: `src/test/kotlin/jarvis/web/AiLiteracyGateTest.kt`

AI Act Article 4 + spec §8 critical-pre-launch item 3: no LLM endpoint may be reachable before the user has confirmed AI literacy.

- [ ] **Step 1: Write the failing test**

```kotlin
package jarvis.web

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import jarvis.tutor.*
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class AiLiteracyGateTest {
    private fun seed(): Triple<String, String, java.nio.file.Path> {
        val dbDir = Files.createTempDirectory("ailitgate")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) {
            SchemaUtils.create(UsersTable, SessionsTable, AiLiteracyConfirmationTable)
        }
        val uid = TutorTypes.ulid()
        UserRepo(db).insert(User(uid, "u", UserScope.FRIEND, Instant.now(), Instant.now()))
        return Triple(uid, SessionRepo(db).create(uid, 3600), dbDir)
    }

    @Test fun `sidekick ask is blocked 403 when the user has not confirmed literacy`() = testApplication {
        val (_, sid, dbDir) = seed()
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installTutorRoutes()
        }
        val r = client.post("/api/v1/sidekick/ask") {
            header("Cookie", "jarvis_session=$sid; csrf=c")
            header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"question":"hi"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, r.status)
        assert(r.bodyAsText().contains("ai-literacy"))
    }

    @Test fun `sidekick ask passes the gate once literacy is confirmed`() = testApplication {
        val (uid, sid, dbDir) = seed()
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        AiLiteracyRepo(db).confirm(uid, AI_LITERACY_VERSION, "ro")
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installTutorRoutes()
        }
        val r = client.post("/api/v1/sidekick/ask") {
            header("Cookie", "jarvis_session=$sid; csrf=c")
            header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"question":"hi"}""")
        }
        assertEquals(true, r.status != HttpStatusCode.Forbidden)  // not blocked by the literacy gate
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle test --tests "jarvis.web.AiLiteracyGateTest"`
Expected: FAIL on the first test (no gate yet — sidekick proceeds).

- [ ] **Step 3: Add a reusable gate helper to `AuthHelpers.kt`**

```kotlin
import io.ktor.server.application.ApplicationCall
import jarvis.tutor.AiLiteracyRepo

/** Require AI-literacy confirmation; respond 403 and skip [block] if not confirmed. */
suspend fun ApplicationCall.requireAiLiteracy(userId: String, block: suspend () -> Unit) {
    val ctx = application.attributes.getOrNull(TutorContextKey)
        ?: return respond(io.ktor.http.HttpStatusCode.InternalServerError, "no ctx")
    if (!AiLiteracyRepo(ctx.db).hasConfirmedCurrent(userId)) {
        respond(io.ktor.http.HttpStatusCode.Forbidden, """{"error":"ai-literacy-required"}""")
        return
    }
    block()
}
```

- [ ] **Step 4: Apply the gate to the LLM routes**

In the `POST /api/v1/sidekick/ask` handler, after the user id is resolved from the session and before the LLM call, wrap the LLM work:

```kotlin
call.requireAiLiteracy(userId) {
    // ...existing JarvisToolset().use { ts -> ts.chat(...) } LLM body...
}
```

Apply the same `requireAiLiteracy(userId) { ... }` wrap to `POST /api/v1/sensor/screenshot` around its vision-LLM call. Resolve `userId` via the route's existing session lookup (or `userIdOrNull()`); if there is no session, the existing 401 path runs first.

- [ ] **Step 5: Run test to verify it passes**

Run: `gradle test --tests "jarvis.web.AiLiteracyGateTest"`
Expected: PASS (2 tests). The second test asserts the response is *not 403* — it may be another status depending on LLM stubbing; that is fine, the assertion only checks the literacy gate is cleared.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/jarvis/web/AuthHelpers.kt src/main/kotlin/jarvis/web/TutorRoutes.kt src/test/kotlin/jarvis/web/AiLiteracyGateTest.kt
git commit -m "feat(compliance): AI-literacy gate blocks LLM endpoints until confirmed"
```

---

### Task 14: ConsentLogTable + ConsentRepo

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/ConsentLog.kt`
- Modify: `TutorRoutes.kt` (register table)
- Test: `src/test/kotlin/jarvis/tutor/ConsentRepoTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ConsentRepoTest {
    private val dbDir = Files.createTempDirectory("consent")
    private val db = TutorDb.connect(dbDir.resolve("t.db").toString())
    init { transaction(db) { SchemaUtils.create(ConsentLogTable) } }
    @AfterTest fun cleanup() { dbDir.toFile().deleteRecursively() }

    @Test fun `record then list returns the consent events newest-first`() {
        val repo = ConsentRepo(db)
        repo.record("u1", "tos", granted = true)
        repo.record("u1", "privacy", granted = true)
        val events = repo.listForUser("u1")
        assertEquals(2, events.size)
        assertEquals("privacy", events.first().consentType)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle test --tests "jarvis.tutor.ConsentRepoTest"`
Expected: FAIL — symbols do not exist.

- [ ] **Step 3: Create `ConsentLog.kt`**

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

object ConsentLogTable : Table("consent_log") {
    val id = long("id").autoIncrement()
    val userId = varchar("user_id", 26)
    val consentType = varchar("consent_type", 32)
    val granted = bool("granted")
    val recordedAt = timestamp("recorded_at")
    override val primaryKey = PrimaryKey(id)
}

data class ConsentEvent(val consentType: String, val granted: Boolean, val recordedAt: Instant)

class ConsentRepo(private val db: Database) {
    fun record(userId: String, consentType: String, granted: Boolean) = transaction(db) {
        ConsentLogTable.insert {
            it[ConsentLogTable.userId] = userId
            it[ConsentLogTable.consentType] = consentType
            it[ConsentLogTable.granted] = granted
            it[recordedAt] = Instant.now()
        }
    }

    fun listForUser(userId: String): List<ConsentEvent> = transaction(db) {
        ConsentLogTable.selectAll()
            .where { ConsentLogTable.userId eq userId }
            .orderBy(ConsentLogTable.recordedAt to SortOrder.DESC)
            .map { ConsentEvent(it[ConsentLogTable.consentType], it[ConsentLogTable.granted], it[ConsentLogTable.recordedAt]) }
    }
}
```

- [ ] **Step 4: Register the table** in the `TutorRoutes.kt` schema list.

- [ ] **Step 5: Run test to verify it passes**

Run: `gradle test --tests "jarvis.tutor.ConsentRepoTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/ConsentLog.kt src/main/kotlin/jarvis/web/TutorRoutes.kt src/test/kotlin/jarvis/tutor/ConsentRepoTest.kt
git commit -m "feat(compliance): consent_log table + repo"
```

---

### Task 15: UserPreferencesTable + UserPreferencesRepo

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/UserPreferences.kt`
- Modify: `TutorRoutes.kt` (register table)
- Test: `src/test/kotlin/jarvis/tutor/UserPreferencesRepoTest.kt`

Fields kept minimal for Gate 2: `hintMode` (static/llm/just-show-me), `loggingPausedUntil` (GDPR Art 18 restriction-of-processing handle). More preference fields land with later gates.

- [ ] **Step 1: Write the failing test**

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserPreferencesRepoTest {
    private val dbDir = Files.createTempDirectory("prefs")
    private val db = TutorDb.connect(dbDir.resolve("t.db").toString())
    init { transaction(db) { SchemaUtils.create(UserPreferencesTable) } }
    @AfterTest fun cleanup() { dbDir.toFile().deleteRecursively() }

    @Test fun `defaults are returned for an unknown user`() {
        val prefs = UserPreferencesRepo(db).get("nobody")
        assertEquals("static", prefs.hintMode)
        assertNull(prefs.loggingPausedUntil)
    }

    @Test fun `set then get round-trips`() {
        val repo = UserPreferencesRepo(db)
        val until = Instant.now().plusSeconds(3600)
        repo.set("u1", hintMode = "llm", loggingPausedUntil = until)
        val prefs = repo.get("u1")
        assertEquals("llm", prefs.hintMode)
        assertEquals(until.epochSecond, prefs.loggingPausedUntil?.epochSecond)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle test --tests "jarvis.tutor.UserPreferencesRepoTest"`
Expected: FAIL — symbols do not exist.

- [ ] **Step 3: Create `UserPreferences.kt`**

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
import java.time.Instant

object UserPreferencesTable : Table("user_preferences") {
    val userId = varchar("user_id", 26)
    val hintMode = varchar("hint_mode", 16).default("static")
    val loggingPausedUntil = timestamp("logging_paused_until").nullable()
    override val primaryKey = PrimaryKey(userId)
}

data class UserPreferences(val hintMode: String, val loggingPausedUntil: Instant?)

class UserPreferencesRepo(private val db: Database) {
    fun get(userId: String): UserPreferences = transaction(db) {
        UserPreferencesTable.selectAll().where { UserPreferencesTable.userId eq userId }
            .singleOrNull()
            ?.let { UserPreferences(it[UserPreferencesTable.hintMode], it[UserPreferencesTable.loggingPausedUntil]) }
            ?: UserPreferences(hintMode = "static", loggingPausedUntil = null)
    }

    fun set(userId: String, hintMode: String, loggingPausedUntil: Instant?) = transaction(db) {
        UserPreferencesTable.upsert {
            it[UserPreferencesTable.userId] = userId
            it[UserPreferencesTable.hintMode] = hintMode
            it[UserPreferencesTable.loggingPausedUntil] = loggingPausedUntil
        }
    }
}
```

> `Table.upsert` exists in Exposed 0.55.0. If the build cannot resolve it, replace with a select-then-insert-or-update inside the same `transaction` block.

- [ ] **Step 4: Register the table** in the `TutorRoutes.kt` schema list.

- [ ] **Step 5: Run test to verify it passes**

Run: `gradle test --tests "jarvis.tutor.UserPreferencesRepoTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/UserPreferences.kt src/main/kotlin/jarvis/web/TutorRoutes.kt src/test/kotlin/jarvis/tutor/UserPreferencesRepoTest.kt
git commit -m "feat(compliance): user_preferences table + repo"
```

---

### Task 16: DSR routes — `/api/v1/me/export`, `/delete`, `/restrict`

**Files:**
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt` (add 3 routes)
- Test: `src/test/kotlin/jarvis/web/MeRoutesTest.kt`

GDPR Article 15 (export), 17 (erasure), 18 (restriction). Scope for Gate 2: export = JSON of the user's own rows; delete = remove the user's rows + revoke sessions; restrict = set `loggingPausedUntil`.

- [ ] **Step 1: Write the failing test**

```kotlin
package jarvis.web

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import jarvis.tutor.*
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MeRoutesTest {
    private fun app(dbDir: java.nio.file.Path) =
        TutorDb.connect(dbDir.resolve("t.db").toString())

    @Test fun `export returns the caller's own account data as JSON`() = testApplication {
        val dbDir = Files.createTempDirectory("meexport")
        val db = app(dbDir)
        transaction(db) { SchemaUtils.create(UsersTable, SessionsTable, ConsentLogTable, UserPreferencesTable) }
        val uid = TutorTypes.ulid()
        UserRepo(db).insert(User(uid, "u", UserScope.FRIEND, Instant.now(), Instant.now(), email = "e@x.io"))
        val sid = SessionRepo(db).create(uid, 3600)
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installTutorRoutes()
        }
        val r = client.get("/api/v1/me/export") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(r.bodyAsText().contains("e@x.io"))
    }

    @Test fun `delete removes the user and revokes the session`() = testApplication {
        val dbDir = Files.createTempDirectory("medelete")
        val db = app(dbDir)
        transaction(db) { SchemaUtils.create(UsersTable, SessionsTable, ConsentLogTable, UserPreferencesTable) }
        val uid = TutorTypes.ulid()
        UserRepo(db).insert(User(uid, "u", UserScope.FRIEND, Instant.now(), Instant.now()))
        val sid = SessionRepo(db).create(uid, 3600)
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installTutorRoutes()
        }
        val r = client.post("/api/v1/me/delete") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
        }
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(null, UserRepo(db).findById(uid))
        assertEquals(null, SessionRepo(db).findUserId(sid))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle test --tests "jarvis.web.MeRoutesTest"`
Expected: FAIL — routes do not exist.

- [ ] **Step 3: Add the three routes** inside `routing {}`:

```kotlin
get("/api/v1/me/export") {
    val ctx = application.attributes.getOrNull(TutorContextKey)
        ?: return@get call.respond(HttpStatusCode.InternalServerError, "no ctx")
    call.requireUser { uid ->
        val user = UserRepo(ctx.db).findById(uid)
        val consent = ConsentRepo(ctx.db).listForUser(uid)
        val prefs = UserPreferencesRepo(ctx.db).get(uid)
        val export = MeExport(
            user = user,
            consentEvents = consent,
            preferences = prefs,
            aiLiteracyConfirmed = AiLiteracyRepo(ctx.db).hasConfirmedCurrent(uid),
            exportedAt = java.time.Instant.now().toString(),
        )
        call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=jarvis-export.json")
        call.respond(export)
    }
}

post("/api/v1/me/delete") {
    val ctx = application.attributes.getOrNull(TutorContextKey)
        ?: return@post call.respond(HttpStatusCode.InternalServerError, "no ctx")
    call.csrfProtect {
        call.requireUser { uid ->
            // Delete user-scoped rows. Extend this list as later gates add user-scoped tables.
            transaction(ctx.db) {
                ConsentLogTable.deleteWhere { ConsentLogTable.userId eq uid }
                UserPreferencesTable.deleteWhere { UserPreferencesTable.userId eq uid }
                AiLiteracyConfirmationTable.deleteWhere { AiLiteracyConfirmationTable.userId eq uid }
                FsrsCardsTable.deleteWhere { FsrsCardsTable.userId eq uid }
                KnowledgeGapsTable.deleteWhere { KnowledgeGapsTable.userId eq uid }
                SessionsTable.deleteWhere { SessionsTable.userId eq uid }
                UsersTable.deleteWhere { UsersTable.id eq uid }
            }
            call.respond(HttpStatusCode.OK, """{"deleted":true}""")
        }
    }
}

post("/api/v1/me/restrict") {
    val ctx = application.attributes.getOrNull(TutorContextKey)
        ?: return@post call.respond(HttpStatusCode.InternalServerError, "no ctx")
    call.csrfProtect {
        call.requireUser { uid ->
            val prefs = UserPreferencesRepo(ctx.db).get(uid)
            // Toggle: pause processing for 30 days, or clear an existing pause.
            val newUntil = if (prefs.loggingPausedUntil == null)
                java.time.Instant.now().plusSeconds(60L * 60 * 24 * 30) else null
            UserPreferencesRepo(ctx.db).set(uid, prefs.hintMode, newUntil)
            call.respond(HttpStatusCode.OK, """{"loggingPaused":${newUntil != null}}""")
        }
    }
}
```

Add the export DTO (make `User`, `ConsentEvent`, `UserPreferences` `@Serializable`, or map them into a serializable DTO — pick one and be consistent):

```kotlin
@kotlinx.serialization.Serializable
data class MeExport(
    val user: UserDto?,
    val consentEvents: List<ConsentEventDto>,
    val preferences: PreferencesDto,
    val aiLiteracyConfirmed: Boolean,
    val exportedAt: String,
)
```

Define `UserDto`/`ConsentEventDto`/`PreferencesDto` as `@Serializable` plain-string DTOs (convert `Instant` to `.toString()`). Do NOT annotate the domain `User`/`ConsentEvent` classes — keep serialization at the route boundary. Map domain → DTO in the route.

> Confirm `FsrsCardsTable` and `KnowledgeGapsTable` expose a `userId` column (they are user-scoped per the schema list). Use `deleteWhere` with the existing import already used in `Sessions.kt`/`Tokens.kt`.

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle test --tests "jarvis.web.MeRoutesTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/web/TutorRoutes.kt src/test/kotlin/jarvis/web/MeRoutesTest.kt
git commit -m "feat(compliance): GDPR DSR routes — /me/export, /me/delete, /me/restrict"
```

---

### Task 17: Frontend — `/welcome/ai-literacy` gate screen (RO + EN)

**Files:**
- Create: `tutor-web/src/components/AiLiteracyGate.tsx`
- Modify: `tutor-web/src/main.tsx`, `tutor-web/src/App.tsx`
- Test: `tutor-web/src/components/AiLiteracyGate.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { AiLiteracyGate } from "./AiLiteracyGate";

describe("AiLiteracyGate", () => {
  it("posts confirmation and calls onConfirmed", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ ok: true }) });
    vi.stubGlobal("fetch", fetchMock);
    const onConfirmed = vi.fn();
    render(<AiLiteracyGate lang="ro" onConfirmed={onConfirmed} />);
    fireEvent.click(screen.getByRole("button", { name: /confirm|am înțeles/i }));
    await waitFor(() => expect(onConfirmed).toHaveBeenCalled());
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/me/ai-literacy/confirm"),
      expect.objectContaining({ method: "POST" }),
    );
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd tutor-web && npx vitest run src/components/AiLiteracyGate.test.tsx`
Expected: FAIL — component does not exist.

- [ ] **Step 3: Create `AiLiteracyGate.tsx`**

A brutalist screen (follow `theme.ts` + existing components) presenting the AI-literacy content — what the AI tutor is, that it can be wrong, that the user should verify its output, that interactions are logged for the user's own learning record. Bilingual: render RO or EN copy by the `lang` prop. A single confirm `<button>`. On click, POST to `/api/v1/me/ai-literacy/confirm` with the CSRF header (read the `csrf` cookie — there is an existing helper in the frontend for this; reuse it) and `{lang}` body, then call the `onConfirmed` prop.

```tsx
export function AiLiteracyGate({ lang, onConfirmed }: { lang: "ro" | "en"; onConfirmed: () => void }) {
  // render bilingual literacy copy; on confirm:
  //   await fetch("/api/v1/me/ai-literacy/confirm", {
  //     method: "POST",
  //     headers: { "Content-Type": "application/json", "X-CSRF-Token": readCsrfCookie() },
  //     body: JSON.stringify({ lang }),
  //   });
  //   onConfirmed();
}
```

The literacy *copy* is content from compliance doc #4 (Phase 4). For Phase 2 ship a concise placeholder-free version (4-6 sentences per language stating: the tutor is an LLM; it can produce wrong answers; verify before trusting; you can withdraw consent in Settings). Phase 4 replaces this with the full §E text.

- [ ] **Step 4: Wire the route**

`main.tsx`: add `<Route path="/welcome/ai-literacy" element={<App />} />`.
`App.tsx`: import `AiLiteracyGate`; add a branch. The gate should also be FORCED: after `auto-session` resolves with a logged-in user, fetch `/api/v1/me/export` (or a lightweight `/api/v1/me/status` if you prefer to add one) to learn `aiLiteracyConfirmed`; if false and the path is not already `/welcome/ai-literacy`, `navigate("/welcome/ai-literacy")`. On `onConfirmed`, `navigate("/")`.

> Decision recorded: rather than adding a separate `/api/v1/me/status` endpoint, reuse `/api/v1/me/export` which already returns `aiLiteracyConfirmed`. If `export` is considered too heavy for a per-load check, a follow-up may add a slim status route — out of scope for Gate 2.

- [ ] **Step 5: Run test + typecheck**

Run: `cd tutor-web && npx vitest run src/components/AiLiteracyGate.test.tsx && npx tsc --noEmit`
Expected: test PASS, tsc clean.

- [ ] **Step 6: Commit**

```bash
git add tutor-web/src/components/AiLiteracyGate.tsx tutor-web/src/components/AiLiteracyGate.test.tsx tutor-web/src/main.tsx tutor-web/src/App.tsx
git commit -m "feat(compliance): /welcome/ai-literacy first-login gate screen"
```

---

### Task 18: Frontend — Settings / Me tab (surface #12)

**Files:**
- Create: `tutor-web/src/components/SettingsMe.tsx`
- Modify: `tutor-web/src/main.tsx`, `tutor-web/src/App.tsx`
- Test: `tutor-web/src/components/SettingsMe.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
import { describe, it, expect, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { SettingsMe } from "./SettingsMe";

describe("SettingsMe", () => {
  it("loads and shows the account email + AI-literacy status", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        user: { id: "u1", name: "alex", email: "alex@x.io", scope: "FRIEND", lang: "ro" },
        consentEvents: [],
        preferences: { hintMode: "static", loggingPausedUntil: null },
        aiLiteracyConfirmed: true,
        exportedAt: "2026-05-20T00:00:00Z",
      }),
    }));
    render(<SettingsMe />);
    await waitFor(() => expect(screen.getByText(/alex@x.io/)).toBeInTheDocument());
    expect(screen.getByText(/literacy/i)).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd tutor-web && npx vitest run src/components/SettingsMe.test.tsx`
Expected: FAIL — component does not exist.

- [ ] **Step 3: Create `SettingsMe.tsx`**

A brutalist Settings/Me tab. On mount, `fetch("/api/v1/me/export")` and render: account (name, email, scope, lang); AI-literacy status; consent-event list; `hintMode` preference; and three GDPR action buttons:
- **Export my data** — triggers a download of `/api/v1/me/export` (the route already sets `Content-Disposition: attachment`).
- **Pause logging / Resume logging** — POST `/api/v1/me/restrict` (with CSRF header), reflects `loggingPausedUntil`.
- **Delete my account** — POST `/api/v1/me/delete` (with CSRF header) behind a confirm step (a typed "DELETE" confirmation or a two-click confirm); on success, redirect to `/login`.

Follow the brutalist ruleset (mono, hard edges, single yellow CTA — the destructive Delete button must NOT be the yellow CTA; render it as a bordered danger control). Reuse the CSRF-cookie helper used elsewhere in the frontend.

- [ ] **Step 4: Wire the route**

`main.tsx`: add `<Route path="/me" element={<App />} />`.
`App.tsx`: import `SettingsMe`; add `here.pathname === "/me" ? <SettingsMe />`. Add a nav entry to the Me tab in the existing tutor nav bar (the nav is in `App.tsx` — there are already `workspace`/`tasks`/`review`/`trust` links; add `me`).

- [ ] **Step 5: Run test + typecheck**

Run: `cd tutor-web && npx vitest run src/components/SettingsMe.test.tsx && npx tsc --noEmit`
Expected: test PASS, tsc clean.

- [ ] **Step 6: Commit**

```bash
git add tutor-web/src/components/SettingsMe.tsx tutor-web/src/components/SettingsMe.test.tsx tutor-web/src/main.tsx tutor-web/src/App.tsx
git commit -m "feat(compliance): Settings/Me tab (surface 12) — account + GDPR DSR"
```

---

## PHASE 3 — Cross-tenant isolation test

### Task 19: Cross-tenant isolation CI test

**Files:**
- Create: `src/test/kotlin/jarvis/web/CrossTenantIsolationTest.kt`

This is the Gate 2 deliverable that satisfies spec §8 critical-pre-launch item 4 ("no user can read another user's data") without RLS — it proves app-layer `WHERE user_id` scoping holds. It is a test only (no production code), so it is its own task, not TDD.

- [ ] **Step 1: Write the test**

Seed two users A and B, each with their own FSRS cards. Authenticate as A. Hit every user-scoped read endpoint and assert A sees only A's rows, never B's. Cover at minimum `/api/v1/fsrs/due` and `/api/v1/gaps` (and `/api/v1/me/export`).

```kotlin
package jarvis.web

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import jarvis.tutor.*
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CrossTenantIsolationTest {
    @Test fun `user A cannot see user B's fsrs cards or gaps`() = testApplication {
        val dbDir = Files.createTempDirectory("crosstenant")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) {
            SchemaUtils.create(UsersTable, SessionsTable, FsrsCardsTable, KnowledgeGapsTable)
        }
        val a = TutorTypes.ulid(); val b = TutorTypes.ulid()
        UserRepo(db).insert(User(a, "A", UserScope.FRIEND, Instant.now(), Instant.now(), email = "a@x.io"))
        UserRepo(db).insert(User(b, "B", UserScope.FRIEND, Instant.now(), Instant.now(), email = "b@x.io"))
        // Seed one card/gap per user. Use the existing repos (FsrsCardRepo, KnowledgeGapRepo)
        // — read their insert signatures and seed a B-owned card with a unique marker
        // string, e.g. kc id "B-ONLY-KC".
        // ... seed code ...
        val sidA = SessionRepo(db).create(a, 3600)
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installTutorRoutes()
        }
        val due = client.get("/api/v1/fsrs/due") { header("Cookie", "jarvis_session=$sidA") }
        assertEquals(HttpStatusCode.OK, due.status)
        assertFalse(due.bodyAsText().contains("B-ONLY-KC"))   // A must never see B's data

        val gaps = client.get("/api/v1/gaps") { header("Cookie", "jarvis_session=$sidA") }
        assertFalse(gaps.bodyAsText().contains("B-ONLY-KC"))
    }
}
```

> While implementing: read the actual `FsrsCardRepo` / `KnowledgeGapRepo` insert signatures and the actual `/api/v1/fsrs/due` + `/api/v1/gaps` route paths/response shapes, and adjust the seed code + assertions to match. The invariant being tested is fixed: **a session for A must never return B's rows.** If any endpoint leaks cross-user data, that is a real Gate 2 bug — fix the route's `WHERE user_id` scoping, do not weaken the test.

- [ ] **Step 2: Run the test**

Run: `gradle test --tests "jarvis.web.CrossTenantIsolationTest"`
Expected: PASS. If it fails, a route is missing user scoping — fix the route.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/jarvis/web/CrossTenantIsolationTest.kt
git commit -m "test(compliance): cross-tenant isolation — app-layer user scoping"
```

---

### Task 20: Full-suite green + frontend render gate

**Files:** none (verification task)

- [ ] **Step 1: Run the full backend suite**

Run: `gradle test`
Expected: PASS. Baseline before Gate 2 was ~723/724 (one known unrelated flake `IntegrationHarnessTest.stateCacheConcurrentPersistNeverTearsJson`). Gate 2 adds ~10 new test classes. Any NEW failure is a Gate 2 regression — fix it.

- [ ] **Step 2: Run the frontend suite + typecheck**

Run: `cd tutor-web && npx vitest run && npx tsc --noEmit`
Expected: PASS (baseline 542/542 + the 3 new component tests), tsc clean.

- [ ] **Step 3: Build the frontend bundle**

Run: `cd tutor-web && npm run build`
Expected: build succeeds; note the new bundle hash.

- [ ] **Step 4: Manual render gate (per the load-bearing render-before-claim rule)**

Boot the backend locally, open a browser, and walk the full flow — do NOT claim Gate 2 shipped on green tests alone:
1. Visit `/tutor/` unauthenticated → redirected to `/tutor/login`.
2. Submit an email → "check your email"; with no `RESEND_API_KEY`, the magic link is printed to the server log (`LoggingMailer`). Copy it.
3. Open the link → redirected into the app, `jarvis_session` cookie set.
4. First login → forced to `/welcome/ai-literacy`; confirm → land in the workspace.
5. Before confirming literacy, a sidekick/LLM call returns 403 `ai-literacy-required`; after confirming, it is not blocked.
6. Open `/tutor/me` → account email, consent, preferences, GDPR buttons all render; "Export my data" downloads JSON; "Pause logging" toggles.
7. Capture zero 4xx/5xx on first paint of each new route + zero on-screen error text (the §18 interaction-smoke gate).

- [ ] **Step 5: Commit any fixes from the render gate, then the gate is done.**

---

## PHASE 4 — Compliance documents (separate effort — see note)

The 15 compliance documents from spec §8 are markdown legal/policy artifacts, not TDD'able code. Their authoritative content lives in "research wiki §E" (referenced by spec §8 line 658), which is **not located in this repo's tracked files** — it must be found (likely `docs/superpowers/research/2026-05-17-novice-pedagogy-tutor-redesign-research.md` or an external wiki) before these can be written without fabricating legal text.

**Recommendation:** plan Phase 4 as its own short writing-plans pass once research wiki §E is located. Writing GDPR/AI-Act legal documents from a placeholder is a compliance risk — each must be a faithful port of reviewed content. Do NOT auto-generate legal text.

Target deliverables (all under `docs/compliance/`), per spec §8:

| # | File | Governing standard |
|---|---|---|
| 1 | `PRIVACY.ro.md` | GDPR Art 13/14 (Romanian) |
| 2 | `PRIVACY.en.md` | GDPR Art 13/14 (English) |
| 3 | `COOKIES.md` | ePrivacy — records the "strictly-necessary only, no banner" decision |
| 4 | (AI-literacy copy) | AI Act Art 4 — feeds Task 17's `AiLiteracyGate` content |
| 5 | `ANNEX_IV.md` | AI Act Art 11 technical documentation |
| 6 | `RISK_REGISTER.md` | AI Act risk management (R-001..R-015) |
| 7 | `MODEL_CARD.md` | model transparency (10 components) |
| 8 | `DPA_REGISTER.md` | GDPR Art 28 processor register |
| 9 | (age-verification) | already implemented as code in Phase 1/2 — document the flow |
| 10 | (DSR UI+API) | Art 15/17/18/22 — implemented in Tasks 16+18; document it |
| 11 | `BREACH_NOTIFICATION_TEMPLATE.md` | GDPR Art 33/34 |
| 12 | `TIA-OpenRouter.md` | Schrems II transfer impact assessment |
| 13 | `EXCLUSIONS.md` | scope exclusions (9 categories) |
| 14 | `LAW-190-2018-ADDENDA.md` | Romanian Law 190/2018 |
| 15 | `PROHIBITED_FEATURES.md` | hard bans (emotion recognition, biometrics, CNP, ad-profiling, streaks, always-listening) |

Items 4, 9, 10 are already realized as code in Phases 1-2; their Phase 4 work is documentation only. Items 1-3, 5-8, 11-15 are content ports.

---

## Self-Review

**1. Spec coverage.** §14 Gate 2 row deliverables, mapped:
- "Postgres" → deliberately deferred (council `1779273122`) — documented in the reconciliation section. ✓ (intentional non-coverage)
- "Auth.js v5 + Drizzle" → replaced with Kotlin-native magic-link — documented. ✓ (intentional)
- "Resend magic-link" → Tasks 5-7. ✓
- "AI literacy first-login gate" → Tasks 11-13, 17. ✓
- "Settings/Me (§12)" → Tasks 16, 18 (surface #12 confirmed via §6.2). ✓
- "15 compliance docs" → Phase 4, flagged as a separate content-port effort. ✓ (scoped out with rationale)
- §8 item 4 "cross-tenant CI test green" → Task 19. ✓
- §8 item 3 "AI literacy gate before any LLM endpoint" → Task 13 (gates `sidekick/ask` + `sensor/screenshot`). ✓

**2. Placeholder scan.** No "TBD"/"implement later". Frontend tasks (10, 17, 18) describe components with concrete elements, endpoints, and test code; styling defers to the existing brutalist `theme.ts` by design (following an established codebase convention, not a placeholder). Several modify-tasks instruct "read the current X first" — that is a verification step for a MODIFY against code not quoted in full here, not a content gap; the exact change (new field/route/whitelist entry) is fully specified in each.

**3. Type consistency.** `MagicLinkClaim(email, lang)` produced by Task 2, consumed by Task 7. `MailResult` defined in Task 5, used in Tasks 5-6. `requireUser`/`userIdOrNull` defined in Task 8, used in Tasks 12, 13, 16. `requireAiLiteracy` defined in Task 13. `AI_LITERACY_VERSION` defined in Task 11, used in Tasks 11-13. `User` gains `email`/`lang` with defaults in Task 1 — existing positional `User(...)` callers (e.g. `AuthSetupTest`) still compile. `TutorContext` gains a `mailer` field in Task 6 — every later route task that needs it goes through `ctx`.

**4. Build+mount pairing.** Three new frontend components: `LoginPage` (Task 10 — mounted in `main.tsx` + `App.tsx` in the same task), `AiLiteracyGate` (Task 17 — mounted same task), `SettingsMe` (Task 18 — mounted same task + nav entry added). Every new component has its route-wiring + `App.tsx` branch in the same task body. ✓

**5. Component-reuse contract.** No task mounts an existing component in a new site with a new prop shape — the frontend components are all new and self-contained. The CSRF-cookie read helper is reused; Tasks 17/18 instruct reusing the existing frontend helper rather than re-rolling it. N/A otherwise.

**6. `data-testid` grep.** Spec §18 mandates an interaction-smoke gate but does not enumerate a fixed `data-testid` list for Gate 2 surfaces. Task 20 step 4 covers the §18 gate (first-paint selectors visible, zero 4xx/5xx, click-through, no error text) as a manual render walk. If executing strictly, add `data-testid` attributes to the login form, the literacy confirm button, and the Settings/Me action buttons during Tasks 10/17/18 so the smoke walk has stable selectors.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-20-gate2-auth-compliance.md`. Two execution options:

**1. Subagent-Driven (recommended)** — dispatch a fresh subagent per task, review between tasks, fast iteration. This is the Gate 1 workflow (implementer + 2-stage review per task) that worked well.

**2. Inline Execution** — execute tasks in this session using executing-plans, batch execution with checkpoints for review.

---

## Post-Gate-2 follow-ups (recorded per council `1779305355`)

Tracked items shipped with Gate 2. Not blockers for the local merge to `main` — to be closed in a later gate or a focused pass.

1. **FK declarations on 3 Gate-2 tables.** `ai_literacy_confirmation`, `consent_log`, `user_preferences` declare `userId` as a plain `varchar` without `.references(UsersTable.id)`; ~10 sibling user-scoped tables declare the FK. Low severity — every `userId` is a server-resolved authenticated id (no orphan-row path), and SQLite enforces FKs only with `PRAGMA foreign_keys=ON` — but it is a consistency gap. Closing it = add `.references(UsersTable.id)` to the 3 tables AND update their repo tests (`AiLiteracyRepoTest`, `ConsentRepoTest`, `UserPreferencesRepoTest`) to `SchemaUtils.create(UsersTable, ...)` + seed each test user as a real `UsersTable` row (an FK-on insert would otherwise fail). Note: `magic_link_tokens` correctly has NO user FK — it is keyed by email before a user row may exist.

**Resolved before merge (no longer deferred):** the `/me/delete` GDPR Art-17 erasure now covers every user-scoped table (`AuditLinesTable` excluded by design — append-only hash-chain, Art 17(3)(b)).

**Deploy gate:** Gate 2 merges to `main` locally; deploying it to the VPS is a separate deliberate step.
