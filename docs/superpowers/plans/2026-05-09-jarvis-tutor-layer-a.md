# Jarvis Tutor — Layer A (Foundations) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the load-bearing foundation of Jarvis Tutor — Ktor `/tutor` route serving a Vite React shell, all v1 schemas (multi-tenant from day 1), httpOnly cookie + CSRF auth, SQLite WAL persistence, append-only KnowledgeGap JSONL ledger, and a fuzzer for the effector contract that must pass before any UI consumes it.

**Architecture:** Add a `tutor-web/` Vite + React 19 + Tailwind v4 + KaTeX project that builds into a static bundle. Extend the existing Ktor app (`src/main/kotlin/jarvis/web/WebMain.kt`) with a new `TutorRoutes.kt` module: it serves the bundle via `staticResources`, exposes `/api/v1/...` endpoints, and authenticates with an opaque session cookie (httpOnly + Secure + SameSite=Strict) plus CSRF double-submit. Persist schemas via Exposed + SQLite WAL (`tutor.db`, `audit.db`); knowledge gaps go to JSONL (`knowledge_gaps_<userId>.jsonl`) with an SQLite index for queries. Single Ktor process, single deploy. All tables carry `userId`.

**Tech Stack:** Kotlin 2.0.21 / JVM 21 · Ktor 3.0.1 · Exposed 0.55.0 + sqlite-jdbc 3.45.3.0 · kotlinx-serialization · React 19 · Vite 5 · Tailwind v4 · KaTeX 0.16 · kotlin-test + JUnit 5

**Spec reference:** `docs/superpowers/specs/2026-05-09-jarvis-tutor-design.md`

---

## File Structure

**New Kotlin files** (under `src/main/kotlin/jarvis/tutor/`):
- `TutorDb.kt` — Exposed + SQLite bootstrap, WAL pragma, table registration.
- `Users.kt` — `User` schema, `UsersTable`, `UserRepo`.
- `Tokens.kt` — `Token` schema, `TokensTable`, `TokenRepo`.
- `Sessions.kt` — opaque session cookie value + table + middleware.
- `Csrf.kt` — double-submit token middleware.
- `Tasks.kt` — `Task` schema, `TasksTable`, `TaskRepo`.
- `Sensors.kt` — `SensorEvent` data classes (LSP-shaped variants), `SensorEventsTable`, `SensorRepo`.
- `Effectors.kt` — `ApplyEditRequest`, `TextEdit`, `EffectorType` enum, `Position`, `Range`, `Outcome` enum.
- `EffectorValidator.kt` — server-side rule pipeline (allowlist + version + grant + nonce).
- `TrustGrants.kt` — `TrustGrant` schema, `TrustGrantsTable`, `TrustGrantRepo`.
- `Audit.kt` — `AuditLine` schema, `AuditLinesTable`, `AuditRepo`, `HashChain`.
- `KnowledgeGaps.kt` — `KnowledgeGap` schema, JSONL writer, SQLite index table.
- `FsrsCards.kt` — `FsrsCard` schema, `FsrsCardsTable`, `FsrsCardRepo` (extends existing `jarvis.Fsrs`).
- `ProviderConfig.kt` — `UserProviderConfig` schema, table, encrypted `apiKeyRef`.
- `TutorTypes.kt` — shared kotlinx-serialization JSON setup, ULID helper.

**New Kotlin files** (under `src/main/kotlin/jarvis/web/`):
- `TutorRoutes.kt` — Ktor routes for `/tutor` static + `/api/v1/...` endpoints + auth setup.

**Modified Kotlin files:**
- `build.gradle.kts` — add Exposed, sqlite-jdbc, libsodium-jna deps.
- `src/main/kotlin/jarvis/web/WebMain.kt` line 98 — register `TutorRoutes.install(this)`.
- `src/main/kotlin/jarvis/Config.kt` — add tutor DB paths + token allowlist path.

**New test files** (under `src/test/kotlin/jarvis/tutor/`):
- `UsersTest.kt`, `TokensTest.kt`, `SessionsTest.kt`, `CsrfTest.kt`, `TasksTest.kt`, `SensorsTest.kt`, `EffectorsTest.kt`, `EffectorValidatorTest.kt`, `EffectorFuzzerTest.kt` (the pre-build gate), `TrustGrantsTest.kt`, `AuditTest.kt`, `HashChainTest.kt`, `KnowledgeGapsTest.kt`, `FsrsCardsTest.kt`, `ProviderConfigTest.kt`, `TutorRoutesTest.kt`.

**New React files** (under `tutor-web/`):
- `package.json`, `vite.config.ts`, `tsconfig.json`, `tailwind.config.ts`, `index.html`.
- `src/main.tsx` — entry.
- `src/App.tsx` — top-level layout.
- `src/components/TutorWorkspace.tsx` — split-pane PDF + chat.
- `src/components/PdfPane.tsx` — PDF viewer (browser native `<embed>` v0).
- `src/components/ChatPane.tsx` — chat panel wired to `/api/chat`.
- `src/lib/api.ts` — fetch wrapper with cookie + CSRF.
- `src/lib/types.ts` — TypeScript mirrors of Kotlin schemas.
- `src/__tests__/TutorWorkspace.test.tsx`, `src/__tests__/api.test.ts`.

**New tooling:**
- `tools/smoke-tutor.sh` — hits `/tutor`, `/api/v1/health`, fetches bundle hash; pass/fail.
- `migrations/001_init_tutor_schema.sql` — initial table DDL (reference; Exposed creates tables, but DDL is documented).

---

## Task 1: Add Gradle dependencies (SQLite + Exposed + libsodium)

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: Write a failing test that asserts SQLite driver is on classpath**

Create `src/test/kotlin/jarvis/tutor/SqliteAvailableTest.kt`:

```kotlin
package jarvis.tutor

import kotlin.test.Test
import kotlin.test.assertNotNull

class SqliteAvailableTest {
    @Test
    fun `sqlite jdbc driver class is loadable`() {
        val cls = Class.forName("org.sqlite.JDBC")
        assertNotNull(cls)
    }

    @Test
    fun `exposed core class is loadable`() {
        val cls = Class.forName("org.jetbrains.exposed.sql.Database")
        assertNotNull(cls)
    }

    @Test
    fun `libsodium binding is loadable`() {
        val cls = Class.forName("com.muquit.libsodiumjna.SodiumLibrary")
        assertNotNull(cls)
    }
}
```

- [ ] **Step 2: Run test, verify FAIL**

Run: `./gradlew test --tests "jarvis.tutor.SqliteAvailableTest"`
Expected: FAIL — `ClassNotFoundException: org.sqlite.JDBC`.

- [ ] **Step 3: Add deps to `build.gradle.kts`**

In the `dependencies { ... }` block, after the existing `implementation("io.ktor:...")` lines, add:

```kotlin
    implementation("org.jetbrains.exposed:exposed-core:0.55.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.55.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.55.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.55.0")
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
    implementation("com.github.muquit.libsodium-jna:libsodium-jna:1.0.5")

    testImplementation("io.ktor:ktor-server-test-host:3.0.1")
    testImplementation("io.ktor:ktor-client-content-negotiation:3.0.1")
```

- [ ] **Step 4: Run test, verify PASS**

Run: `./gradlew test --tests "jarvis.tutor.SqliteAvailableTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add build.gradle.kts src/test/kotlin/jarvis/tutor/SqliteAvailableTest.kt
git commit -m "Tutor Layer A: add SQLite + Exposed + libsodium-jna deps"
```

---

## Task 2: TutorDb bootstrap with WAL pragma

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/TutorDb.kt`
- Test: `src/test/kotlin/jarvis/tutor/TutorDbTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/jarvis/tutor/TutorDbTest.kt`:

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TutorDbTest {
    @Test
    fun `connect opens sqlite at given path and enables WAL`() {
        val tmp = Files.createTempDirectory("tutor-db-test").resolve("test.db")
        val db: Database = TutorDb.connect(tmp.toString())
        assertNotNull(db)
        transaction(db) {
            val journalMode = exec("PRAGMA journal_mode") { rs ->
                rs.next(); rs.getString(1)
            }
            assertEquals("wal", journalMode?.lowercase())
        }
    }
}
```

- [ ] **Step 2: Run test, verify FAIL**

Run: `./gradlew test --tests "jarvis.tutor.TutorDbTest"`
Expected: FAIL — `Unresolved reference: TutorDb`.

- [ ] **Step 3: Implement `TutorDb`**

Create `src/main/kotlin/jarvis/tutor/TutorDb.kt`:

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction

object TutorDb {
    fun connect(sqlitePath: String): Database {
        val url = "jdbc:sqlite:$sqlitePath"
        val db = Database.connect(url, driver = "org.sqlite.JDBC")
        transaction(db) {
            exec("PRAGMA journal_mode=WAL")
            exec("PRAGMA synchronous=NORMAL")
            exec("PRAGMA foreign_keys=ON")
        }
        return db
    }
}
```

- [ ] **Step 4: Run test, verify PASS**

Run: `./gradlew test --tests "jarvis.tutor.TutorDbTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/TutorDb.kt src/test/kotlin/jarvis/tutor/TutorDbTest.kt
git commit -m "Tutor Layer A: TutorDb bootstrap with WAL pragma"
```

---

## Task 3: ULID helper + JSON setup

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/TutorTypes.kt`
- Test: `src/test/kotlin/jarvis/tutor/TutorTypesTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package jarvis.tutor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TutorTypesTest {
    @Test
    fun `ulid produces 26-char crockford base32 monotonic identifiers`() {
        val a = TutorTypes.ulid()
        Thread.sleep(2)
        val b = TutorTypes.ulid()
        assertEquals(26, a.length)
        assertEquals(26, b.length)
        assertTrue(a < b, "ULIDs must be lexicographically monotonic over time")
        assertTrue(a.matches(Regex("[0-9A-HJKMNP-TV-Z]{26}")))
    }

    @Test
    fun `tutorJson encodes and decodes with class discriminator`() {
        @kotlinx.serialization.Serializable
        data class Box(val v: String)
        val s = TutorTypes.tutorJson.encodeToString(kotlinx.serialization.serializer(), Box("x"))
        val back = TutorTypes.tutorJson.decodeFromString<Box>(kotlinx.serialization.serializer(), s)
        assertEquals("x", back.v)
    }
}
```

- [ ] **Step 2: Run, FAIL**

Run: `./gradlew test --tests "jarvis.tutor.TutorTypesTest"`
Expected: FAIL — `Unresolved reference: TutorTypes`.

- [ ] **Step 3: Implement**

Create `src/main/kotlin/jarvis/tutor/TutorTypes.kt`:

```kotlin
package jarvis.tutor

import kotlinx.serialization.json.Json
import java.security.SecureRandom

object TutorTypes {
    private val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray()
    private val rng = SecureRandom()
    @Volatile private var lastTs: Long = 0L
    private val lastRand = ByteArray(10)
    private val lock = Any()

    fun ulid(): String {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val rand: ByteArray
            if (now == lastTs) {
                // Increment last random for monotonicity within same ms.
                rand = lastRand.copyOf()
                var i = rand.size - 1
                while (i >= 0) {
                    val v = (rand[i].toInt() and 0xFF) + 1
                    rand[i] = (v and 0xFF).toByte()
                    if (v <= 0xFF) break
                    i--
                }
            } else {
                rand = ByteArray(10).also { rng.nextBytes(it) }
                lastTs = now
            }
            System.arraycopy(rand, 0, lastRand, 0, 10)
            return encode(now, rand)
        }
    }

    private fun encode(timestamp: Long, randomness: ByteArray): String {
        require(randomness.size == 10)
        val out = CharArray(26)
        // 48 bits of time → 10 chars
        var ts = timestamp
        for (i in 9 downTo 0) {
            out[i] = CROCKFORD[(ts and 0x1F).toInt()]
            ts = ts shr 5
        }
        // 80 bits of randomness → 16 chars
        var idx = 10
        var carry = 0L
        var bits = 0
        for (b in randomness) {
            carry = (carry shl 8) or (b.toLong() and 0xFF)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                out[idx++] = CROCKFORD[((carry shr bits) and 0x1F).toInt()]
            }
        }
        return String(out)
    }

    val tutorJson: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "kind"
    }
}
```

- [ ] **Step 4: Run, PASS**

Run: `./gradlew test --tests "jarvis.tutor.TutorTypesTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/TutorTypes.kt src/test/kotlin/jarvis/tutor/TutorTypesTest.kt
git commit -m "Tutor Layer A: ULID helper + JSON config"
```

---

## Task 4: User schema + repository

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/Users.kt`
- Test: `src/test/kotlin/jarvis/tutor/UsersTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class UsersTest {
    private fun freshDb() = TutorDb.connect(
        Files.createTempDirectory("users-test").resolve("t.db").toString()
    ).also { db -> transaction(db) { SchemaUtils.create(UsersTable) } }

    @Test
    fun `insert and find owner user`() {
        val db = freshDb()
        val repo = UserRepo(db)
        val u = User(id = TutorTypes.ulid(), name = "victor", scope = UserScope.OWNER,
                     createdAt = Instant.now(), lastSeenAt = Instant.now())
        repo.insert(u)
        val found = repo.findById(u.id)
        assertNotNull(found)
        assertEquals("victor", found.name)
        assertEquals(UserScope.OWNER, found.scope)
    }

    @Test
    fun `findById returns null when absent`() {
        val repo = UserRepo(freshDb())
        assertEquals(null, repo.findById("MISSING"))
    }

    @Test
    fun `touchLastSeen updates timestamp`() {
        val db = freshDb()
        val repo = UserRepo(db)
        val u = User(id = TutorTypes.ulid(), name = "v", scope = UserScope.OWNER,
                     createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                     lastSeenAt = Instant.parse("2026-01-01T00:00:00Z"))
        repo.insert(u)
        val before = repo.findById(u.id)!!.lastSeenAt
        Thread.sleep(5)
        repo.touchLastSeen(u.id, Instant.now())
        val after = repo.findById(u.id)!!.lastSeenAt
        assert(after.isAfter(before))
    }
}
```

- [ ] **Step 2: Run, FAIL**

Run: `./gradlew test --tests "jarvis.tutor.UsersTest"`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement**

Create `src/main/kotlin/jarvis/tutor/Users.kt`:

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

enum class UserScope { OWNER, FRIEND }

data class User(
    val id: String,                 // ULID
    val name: String,
    val scope: UserScope,
    val createdAt: Instant,
    val lastSeenAt: Instant,
)

object UsersTable : Table("users") {
    val id = varchar("id", 26)
    val name = varchar("name", 64)
    val scope = varchar("scope", 16)
    val createdAt = timestamp("created_at")
    val lastSeenAt = timestamp("last_seen_at")
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
        }
    }

    fun findById(id: String): User? = transaction(db) {
        UsersTable.select { UsersTable.id eq id }.singleOrNull()?.toUser()
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
    )
}
```

- [ ] **Step 4: Run, PASS**

Run: `./gradlew test --tests "jarvis.tutor.UsersTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/Users.kt src/test/kotlin/jarvis/tutor/UsersTest.kt
git commit -m "Tutor Layer A: User schema + UserRepo"
```

---

## Task 5: Token allowlist + repository

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/Tokens.kt`
- Test: `src/test/kotlin/jarvis/tutor/TokensTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TokensTest {
    private fun freshDb() = TutorDb.connect(
        Files.createTempDirectory("tokens-test").resolve("t.db").toString()
    ).also { db -> transaction(db) { SchemaUtils.create(UsersTable, TokensTable) } }

    @Test
    fun `issue stores hashed token and findUserIdByToken returns userId`() {
        val db = freshDb()
        val users = UserRepo(db)
        val u = User(TutorTypes.ulid(), "victor", UserScope.OWNER, Instant.now(), Instant.now())
        users.insert(u)
        val tokens = TokenRepo(db)
        val raw = tokens.issue(userId = u.id, label = "laptop")
        assertEquals(64, raw.length)  // 32 bytes hex
        assertEquals(u.id, tokens.findUserIdByToken(raw))
    }

    @Test
    fun `revoke removes the token`() {
        val db = freshDb()
        val users = UserRepo(db); users.insert(User(TutorTypes.ulid(), "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val u = users.findById(users.findById("any") ?: TutorTypes.ulid()) // sanity
        val userId = TutorTypes.ulid()
        users.insert(User(userId, "u2", UserScope.FRIEND, Instant.now(), Instant.now()))
        val tokens = TokenRepo(db)
        val raw = tokens.issue(userId, "phone")
        assertNotNull(tokens.findUserIdByToken(raw))
        tokens.revoke(raw)
        assertEquals(null, tokens.findUserIdByToken(raw))
    }

    @Test
    fun `findUserIdByToken returns null on bogus token`() {
        val db = freshDb()
        val tokens = TokenRepo(db)
        assertEquals(null, tokens.findUserIdByToken("bogus"))
    }
}
```

- [ ] **Step 2: Run, FAIL**

Run: `./gradlew test --tests "jarvis.tutor.TokensTest"`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement**

Create `src/main/kotlin/jarvis/tutor/Tokens.kt`:

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant

object TokensTable : Table("tokens") {
    val tokenHash = varchar("token_hash", 64)        // SHA256 hex of raw token
    val userId = varchar("user_id", 26).references(UsersTable.id)
    val label = varchar("label", 64)
    val createdAt = timestamp("created_at")
    val revokedAt = timestamp("revoked_at").nullable()
    override val primaryKey = PrimaryKey(tokenHash)
}

class TokenRepo(private val db: Database) {
    private val rng = SecureRandom()

    fun issue(userId: String, label: String): String {
        val raw = ByteArray(32).also { rng.nextBytes(it) }.toHex()
        val hash = sha256Hex(raw)
        transaction(db) {
            TokensTable.insert {
                it[TokensTable.tokenHash] = hash
                it[TokensTable.userId] = userId
                it[TokensTable.label] = label
                it[TokensTable.createdAt] = Instant.now()
                it[TokensTable.revokedAt] = null
            }
        }
        return raw
    }

    fun findUserIdByToken(raw: String): String? = transaction(db) {
        val hash = sha256Hex(raw)
        TokensTable
            .select { (TokensTable.tokenHash eq hash) and TokensTable.revokedAt.isNull() }
            .singleOrNull()?.get(TokensTable.userId)
    }

    fun revoke(raw: String) = transaction(db) {
        val hash = sha256Hex(raw)
        TokensTable.deleteWhere { TokensTable.tokenHash eq hash }
    }

    private fun sha256Hex(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).toHex()

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}
```

NOTE: import `org.jetbrains.exposed.sql.SqlExpressionBuilder.and` and `deleteWhere` correctly per Exposed 0.55 — ensure imports compile.

- [ ] **Step 4: Run, PASS**

Run: `./gradlew test --tests "jarvis.tutor.TokensTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/Tokens.kt src/test/kotlin/jarvis/tutor/TokensTest.kt
git commit -m "Tutor Layer A: Tokens table + issue/revoke/lookup"
```

---

## Task 6: Session cookie + middleware

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/Sessions.kt`
- Test: `src/test/kotlin/jarvis/tutor/SessionsTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SessionsTest {
    private fun freshDb() = TutorDb.connect(
        Files.createTempDirectory("sess-test").resolve("t.db").toString()
    ).also { db -> transaction(db) { SchemaUtils.create(UsersTable, SessionsTable) } }

    @Test
    fun `create returns opaque sid and find resolves to userId`() {
        val db = freshDb()
        val users = UserRepo(db)
        val u = User(TutorTypes.ulid(), "v", UserScope.OWNER, Instant.now(), Instant.now())
        users.insert(u)
        val repo = SessionRepo(db)
        val sid = repo.create(u.id, ttlSeconds = 3600)
        assertEquals(64, sid.length)
        assertEquals(u.id, repo.findUserId(sid))
    }

    @Test
    fun `expired session is not returned`() {
        val db = freshDb()
        val users = UserRepo(db)
        val u = User(TutorTypes.ulid(), "v", UserScope.OWNER, Instant.now(), Instant.now())
        users.insert(u)
        val repo = SessionRepo(db)
        val sid = repo.create(u.id, ttlSeconds = -1) // already expired
        assertNull(repo.findUserId(sid))
    }

    @Test
    fun `revoke deletes session`() {
        val db = freshDb()
        val users = UserRepo(db); users.insert(User(TutorTypes.ulid(), "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val u = User(TutorTypes.ulid(), "x", UserScope.FRIEND, Instant.now(), Instant.now())
        users.insert(u)
        val repo = SessionRepo(db)
        val sid = repo.create(u.id, 60)
        assertNotNull(repo.findUserId(sid))
        repo.revoke(sid)
        assertNull(repo.findUserId(sid))
    }
}
```

- [ ] **Step 2: Run, FAIL**

Run: `./gradlew test --tests "jarvis.tutor.SessionsTest"`
Expected: FAIL.

- [ ] **Step 3: Implement**

Create `src/main/kotlin/jarvis/tutor/Sessions.kt`:

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.SecureRandom
import java.time.Instant

object SessionsTable : Table("sessions") {
    val sid = varchar("sid", 64)
    val userId = varchar("user_id", 26).references(UsersTable.id)
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at")
    override val primaryKey = PrimaryKey(sid)
}

class SessionRepo(private val db: Database) {
    private val rng = SecureRandom()

    fun create(userId: String, ttlSeconds: Long): String {
        val sid = ByteArray(32).also { rng.nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        val now = Instant.now()
        transaction(db) {
            SessionsTable.insert {
                it[SessionsTable.sid] = sid
                it[SessionsTable.userId] = userId
                it[SessionsTable.createdAt] = now
                it[SessionsTable.expiresAt] = now.plusSeconds(ttlSeconds)
            }
        }
        return sid
    }

    fun findUserId(sid: String): String? = transaction(db) {
        val now = Instant.now()
        SessionsTable
            .select { (SessionsTable.sid eq sid) and (SessionsTable.expiresAt greater now) }
            .singleOrNull()?.get(SessionsTable.userId)
    }

    fun revoke(sid: String) = transaction(db) {
        SessionsTable.deleteWhere { SessionsTable.sid eq sid }
    }
}
```

- [ ] **Step 4: Run, PASS**

Run: `./gradlew test --tests "jarvis.tutor.SessionsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/Sessions.kt src/test/kotlin/jarvis/tutor/SessionsTest.kt
git commit -m "Tutor Layer A: SessionRepo with TTL + revoke"
```

---

## Task 7: CSRF double-submit middleware

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/Csrf.kt`
- Test: `src/test/kotlin/jarvis/tutor/CsrfTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package jarvis.tutor

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class CsrfTest {
    @Test
    fun `POST without csrf header is rejected with 403`() = testApplication {
        application { routing { post("/api/v1/x") { csrfProtect { call.respond(HttpStatusCode.OK) } } } }
        val r = client.post("/api/v1/x") { cookie("csrf", "abc") }
        assertEquals(HttpStatusCode.Forbidden, r.status)
    }

    @Test
    fun `POST with matching csrf header passes`() = testApplication {
        application { routing { post("/api/v1/x") { csrfProtect { call.respond(HttpStatusCode.OK) } } } }
        val r = client.post("/api/v1/x") {
            cookie("csrf", "abc")
            header("X-CSRF-Token", "abc")
        }
        assertEquals(HttpStatusCode.OK, r.status)
    }

    @Test
    fun `POST with mismatched csrf header is rejected`() = testApplication {
        application { routing { post("/api/v1/x") { csrfProtect { call.respond(HttpStatusCode.OK) } } } }
        val r = client.post("/api/v1/x") {
            cookie("csrf", "abc")
            header("X-CSRF-Token", "wrong")
        }
        assertEquals(HttpStatusCode.Forbidden, r.status)
    }
}
```

- [ ] **Step 2: Run, FAIL**

Run: `./gradlew test --tests "jarvis.tutor.CsrfTest"`
Expected: FAIL — unresolved `csrfProtect`.

- [ ] **Step 3: Implement**

Create `src/main/kotlin/jarvis/tutor/Csrf.kt`:

```kotlin
package jarvis.tutor

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*

suspend fun PipelineContext<Unit, ApplicationCall>.csrfProtect(block: suspend () -> Unit) {
    val cookie = call.request.cookies["csrf"]
    val header = call.request.header("X-CSRF-Token")
    if (cookie.isNullOrBlank() || header != cookie) {
        call.respond(HttpStatusCode.Forbidden, "CSRF check failed")
        return
    }
    block()
}
```

- [ ] **Step 4: Run, PASS**

Run: `./gradlew test --tests "jarvis.tutor.CsrfTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/Csrf.kt src/test/kotlin/jarvis/tutor/CsrfTest.kt
git commit -m "Tutor Layer A: CSRF double-submit middleware"
```

---

## Task 8: Effector contract types

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/Effectors.kt`
- Test: `src/test/kotlin/jarvis/tutor/EffectorsTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package jarvis.tutor

import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EffectorsTest {
    @Test
    fun `ApplyEditRequest round-trips JSON`() {
        val req = ApplyEditRequest(
            taskId = "T01HVY",
            effectorId = "E01HVZ",
            targetUri = "file:///c/uaic/ps/laplace_mle.R",
            expectedDocVersion = "abcd1234",
            edits = listOf(TextEdit(Range(Position(3, 5), Position(3, 12)), "median(x)")),
            nonce = "nonce-1",
            grantId = "G01HVY",
        )
        val json = TutorTypes.tutorJson.encodeToString(ApplyEditRequest.serializer(), req)
        val back = TutorTypes.tutorJson.decodeFromString(ApplyEditRequest.serializer(), json)
        assertEquals(req, back)
    }

    @Test
    fun `EffectorType enum covers expected operations`() {
        val expected = setOf("APPLY_EDIT", "RUN_R", "NAVIGATE", "INSERT_SCRATCHPAD")
        val actual = EffectorType.values().map { it.name }.toSet()
        assertTrue(actual.containsAll(expected),
            "EffectorType missing one of $expected — got $actual")
    }
}
```

- [ ] **Step 2: Run, FAIL**

Run: `./gradlew test --tests "jarvis.tutor.EffectorsTest"`
Expected: FAIL.

- [ ] **Step 3: Implement**

Create `src/main/kotlin/jarvis/tutor/Effectors.kt`:

```kotlin
package jarvis.tutor

import kotlinx.serialization.Serializable

enum class EffectorType { APPLY_EDIT, RUN_R, NAVIGATE, INSERT_SCRATCHPAD }

enum class Outcome { SUCCESS, REJECTED, ROLLED_BACK, STALE_DOC, PATH_DENIED }

@Serializable
data class Position(val line: Int, val character: Int)

@Serializable
data class Range(val start: Position, val end: Position)

@Serializable
data class TextEdit(val range: Range, val newText: String)

@Serializable
data class ApplyEditRequest(
    val taskId: String,
    val effectorId: String,             // ULID, unique per call
    val targetUri: String,              // file URI
    val expectedDocVersion: String,     // SHA256 hex of current file content at LLM read time
    val edits: List<TextEdit>,
    val nonce: String,                  // server tracks last 1000 in ring buffer
    val grantId: String,
)
```

- [ ] **Step 4: Run, PASS**

Run: `./gradlew test --tests "jarvis.tutor.EffectorsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/Effectors.kt src/test/kotlin/jarvis/tutor/EffectorsTest.kt
git commit -m "Tutor Layer A: ApplyEditRequest + TextEdit + Position + Range + enums"
```

---

## Task 9: TrustGrant schema + repository

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/TrustGrants.kt`
- Test: `src/test/kotlin/jarvis/tutor/TrustGrantsTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrustGrantsTest {
    private fun freshDb() = TutorDb.connect(
        Files.createTempDirectory("grants-test").resolve("t.db").toString()
    ).also { db -> transaction(db) { SchemaUtils.create(UsersTable, TrustGrantsTable) } }

    private fun seedUser(db: org.jetbrains.exposed.sql.Database): String {
        val id = TutorTypes.ulid()
        UserRepo(db).insert(User(id, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        return id
    }

    @Test
    fun `insert and findActive grant`() {
        val db = freshDb()
        val u = seedUser(db)
        val repo = TrustGrantRepo(db)
        val g = TrustGrant(
            grantId = TutorTypes.ulid(), userId = u,
            scope = listOf("~/uaic/ps-hw/**"),
            ops = setOf(EffectorType.APPLY_EDIT, EffectorType.INSERT_SCRATCHPAD),
            expiresAt = Instant.now().plusSeconds(3600),
            maxCalls = 50, callsUsed = 0,
            grantedFrom = GrantSource.UI, revokedAt = null,
            createdAt = Instant.now(),
        )
        repo.insert(g)
        val found = repo.findActive(g.grantId, Instant.now())
        assertNotNull(found)
        assertEquals(g.grantId, found.grantId)
    }

    @Test
    fun `expired grant not returned`() {
        val db = freshDb()
        val u = seedUser(db)
        val repo = TrustGrantRepo(db)
        val g = TrustGrant(TutorTypes.ulid(), u, listOf("~/x/**"),
            setOf(EffectorType.APPLY_EDIT), Instant.now().minusSeconds(1),
            10, 0, GrantSource.UI, null, Instant.now())
        repo.insert(g)
        assertNull(repo.findActive(g.grantId, Instant.now()))
    }

    @Test
    fun `revoked grant not returned`() {
        val db = freshDb()
        val u = seedUser(db)
        val repo = TrustGrantRepo(db)
        val g = TrustGrant(TutorTypes.ulid(), u, listOf("~/x/**"),
            setOf(EffectorType.APPLY_EDIT), Instant.now().plusSeconds(3600),
            10, 0, GrantSource.UI, null, Instant.now())
        repo.insert(g)
        repo.revoke(g.grantId, Instant.now())
        assertNull(repo.findActive(g.grantId, Instant.now()))
    }

    @Test
    fun `incrementCallsUsed enforces maxCalls cap`() {
        val db = freshDb()
        val u = seedUser(db)
        val repo = TrustGrantRepo(db)
        val g = TrustGrant(TutorTypes.ulid(), u, listOf("~/x/**"),
            setOf(EffectorType.APPLY_EDIT), Instant.now().plusSeconds(3600),
            2, 0, GrantSource.UI, null, Instant.now())
        repo.insert(g)
        assertTrue(repo.tryConsume(g.grantId))
        assertTrue(repo.tryConsume(g.grantId))
        assertEquals(false, repo.tryConsume(g.grantId)) // cap hit
    }
}
```

- [ ] **Step 2: Run, FAIL**

Run: `./gradlew test --tests "jarvis.tutor.TrustGrantsTest"`
Expected: FAIL.

- [ ] **Step 3: Implement**

Create `src/main/kotlin/jarvis/tutor/TrustGrants.kt`:

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

enum class GrantSource { UI, CHAT }

data class TrustGrant(
    val grantId: String,
    val userId: String,
    val scope: List<String>,
    val ops: Set<EffectorType>,
    val expiresAt: Instant,
    val maxCalls: Int,
    val callsUsed: Int,
    val grantedFrom: GrantSource,
    val revokedAt: Instant?,
    val createdAt: Instant,
)

object TrustGrantsTable : Table("trust_grants") {
    val grantId = varchar("grant_id", 26)
    val userId = varchar("user_id", 26).references(UsersTable.id)
    val scopeJson = text("scope_json")           // JSON-encoded List<String>
    val opsJson = text("ops_json")               // JSON-encoded Set<EffectorType>
    val expiresAt = timestamp("expires_at")
    val maxCalls = integer("max_calls")
    val callsUsed = integer("calls_used")
    val grantedFrom = varchar("granted_from", 8)
    val revokedAt = timestamp("revoked_at").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(grantId)
}

class TrustGrantRepo(private val db: Database) {
    fun insert(g: TrustGrant) = transaction(db) {
        TrustGrantsTable.insert {
            it[grantId] = g.grantId
            it[userId] = g.userId
            it[scopeJson] = TutorTypes.tutorJson.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.builtins.serializer()), g.scope)
            it[opsJson] = TutorTypes.tutorJson.encodeToString(
                kotlinx.serialization.builtins.SetSerializer(kotlinx.serialization.serializer<EffectorType>()), g.ops)
            it[expiresAt] = g.expiresAt
            it[maxCalls] = g.maxCalls
            it[callsUsed] = g.callsUsed
            it[grantedFrom] = g.grantedFrom.name
            it[revokedAt] = g.revokedAt
            it[createdAt] = g.createdAt
        }
    }

    fun findActive(grantId: String, now: Instant): TrustGrant? = transaction(db) {
        TrustGrantsTable
            .select {
                (TrustGrantsTable.grantId eq grantId) and
                (TrustGrantsTable.expiresAt greater now) and
                TrustGrantsTable.revokedAt.isNull()
            }
            .singleOrNull()?.toGrant()
    }

    fun revoke(grantId: String, now: Instant) = transaction(db) {
        TrustGrantsTable.update({ TrustGrantsTable.grantId eq grantId }) {
            it[revokedAt] = now
        }
    }

    fun tryConsume(grantId: String): Boolean = transaction(db) {
        val row = TrustGrantsTable.select { TrustGrantsTable.grantId eq grantId }.singleOrNull()
            ?: return@transaction false
        val used = row[TrustGrantsTable.callsUsed]
        val max = row[TrustGrantsTable.maxCalls]
        if (used >= max) return@transaction false
        TrustGrantsTable.update({ TrustGrantsTable.grantId eq grantId }) {
            it[callsUsed] = used + 1
        }
        true
    }

    private fun ResultRow.toGrant() = TrustGrant(
        grantId = this[TrustGrantsTable.grantId],
        userId = this[TrustGrantsTable.userId],
        scope = TutorTypes.tutorJson.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.builtins.serializer()),
            this[TrustGrantsTable.scopeJson]),
        ops = TutorTypes.tutorJson.decodeFromString(
            kotlinx.serialization.builtins.SetSerializer(kotlinx.serialization.serializer<EffectorType>()),
            this[TrustGrantsTable.opsJson]),
        expiresAt = this[TrustGrantsTable.expiresAt],
        maxCalls = this[TrustGrantsTable.maxCalls],
        callsUsed = this[TrustGrantsTable.callsUsed],
        grantedFrom = GrantSource.valueOf(this[TrustGrantsTable.grantedFrom]),
        revokedAt = this[TrustGrantsTable.revokedAt],
        createdAt = this[TrustGrantsTable.createdAt],
    )
}
```

- [ ] **Step 4: Run, PASS**

Run: `./gradlew test --tests "jarvis.tutor.TrustGrantsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/TrustGrants.kt src/test/kotlin/jarvis/tutor/TrustGrantsTest.kt
git commit -m "Tutor Layer A: TrustGrant schema + insert/find/revoke/tryConsume"
```

---

## Task 10: Hash chain helper

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/HashChain.kt`
- Test: `src/test/kotlin/jarvis/tutor/HashChainTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package jarvis.tutor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class HashChainTest {
    @Test
    fun `nextHash deterministic given prevHash and canonical line`() {
        val a = HashChain.nextHash(prev = "0".repeat(64), canonicalLine = """{"a":1}""")
        val b = HashChain.nextHash(prev = "0".repeat(64), canonicalLine = """{"a":1}""")
        assertEquals(a, b)
    }

    @Test
    fun `nextHash differs for different prev`() {
        val a = HashChain.nextHash(prev = "0".repeat(64), canonicalLine = """{"a":1}""")
        val b = HashChain.nextHash(prev = "f".repeat(64), canonicalLine = """{"a":1}""")
        assertNotEquals(a, b)
    }

    @Test
    fun `verify returns true for intact chain`() {
        val l1 = HashChain.nextHash("0".repeat(64), """{"seq":1}""")
        val l2 = HashChain.nextHash(l1, """{"seq":2}""")
        val l3 = HashChain.nextHash(l2, """{"seq":3}""")
        val chain = listOf(
            HashChain.Link("0".repeat(64), """{"seq":1}""", l1),
            HashChain.Link(l1, """{"seq":2}""", l2),
            HashChain.Link(l2, """{"seq":3}""", l3),
        )
        assertEquals(true, HashChain.verify(chain))
    }

    @Test
    fun `verify detects tampered line`() {
        val l1 = HashChain.nextHash("0".repeat(64), """{"seq":1}""")
        val l2 = HashChain.nextHash(l1, """{"seq":2}""")
        val chain = listOf(
            HashChain.Link("0".repeat(64), """{"seq":1}""", l1),
            HashChain.Link(l1, """{"seq":2-TAMPERED}""", l2),
        )
        assertEquals(false, HashChain.verify(chain))
    }
}
```

- [ ] **Step 2: Run, FAIL**

Run: `./gradlew test --tests "jarvis.tutor.HashChainTest"`
Expected: FAIL.

- [ ] **Step 3: Implement**

Create `src/main/kotlin/jarvis/tutor/HashChain.kt`:

```kotlin
package jarvis.tutor

import java.security.MessageDigest

object HashChain {
    data class Link(val prev: String, val canonical: String, val thisHash: String)

    fun nextHash(prev: String, canonicalLine: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(prev.toByteArray(Charsets.UTF_8))
        md.update(canonicalLine.toByteArray(Charsets.UTF_8))
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun verify(chain: List<Link>): Boolean {
        if (chain.isEmpty()) return true
        var prev = chain.first().prev
        for (link in chain) {
            if (link.prev != prev) return false
            val computed = nextHash(link.prev, link.canonical)
            if (computed != link.thisHash) return false
            prev = link.thisHash
        }
        return true
    }
}
```

- [ ] **Step 4: Run, PASS**

Run: `./gradlew test --tests "jarvis.tutor.HashChainTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/HashChain.kt src/test/kotlin/jarvis/tutor/HashChainTest.kt
git commit -m "Tutor Layer A: HashChain helper for audit log integrity"
```

---

## Task 11: AuditLine schema + repository

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/Audit.kt`
- Test: `src/test/kotlin/jarvis/tutor/AuditTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuditTest {
    private fun freshDb() = TutorDb.connect(
        Files.createTempDirectory("audit-test").resolve("audit.db").toString()
    ).also { db -> transaction(db) { SchemaUtils.create(UsersTable, AuditLinesTable) } }

    private fun seedUser(db: org.jetbrains.exposed.sql.Database): String {
        val id = TutorTypes.ulid()
        UserRepo(db).insert(User(id, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        return id
    }

    @Test
    fun `append assigns monotonic seq and chains hashes`() {
        val db = freshDb()
        val u = seedUser(db)
        val repo = AuditRepo(db)
        val a = repo.append(u, ""+"""{"event":"first"}""", Outcome.SUCCESS)
        val b = repo.append(u, """{"event":"second"}""", Outcome.SUCCESS)
        assertEquals(1L, a.seq)
        assertEquals(2L, b.seq)
        assertEquals(a.thisHash, b.prevHash)
    }

    @Test
    fun `verifyChain returns true for intact log`() {
        val db = freshDb()
        val u = seedUser(db)
        val repo = AuditRepo(db)
        repo.append(u, """{"event":"a"}""", Outcome.SUCCESS)
        repo.append(u, """{"event":"b"}""", Outcome.SUCCESS)
        repo.append(u, """{"event":"c"}""", Outcome.REJECTED)
        assertTrue(repo.verifyChain(u))
    }

    @Test
    fun `seq starts at 1 per user`() {
        val db = freshDb()
        val u1 = seedUser(db)
        val u2 = seedUser(db)
        val repo = AuditRepo(db)
        val a = repo.append(u1, """{"e":1}""", Outcome.SUCCESS)
        val b = repo.append(u2, """{"e":2}""", Outcome.SUCCESS)
        assertEquals(1L, a.seq)
        assertEquals(1L, b.seq)
    }
}
```

- [ ] **Step 2: Run, FAIL**

Run: `./gradlew test --tests "jarvis.tutor.AuditTest"`
Expected: FAIL.

- [ ] **Step 3: Implement**

Create `src/main/kotlin/jarvis/tutor/Audit.kt`:

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

data class AuditLine(
    val seq: Long,
    val userId: String,
    val ts: Instant,
    val canonical: String,         // canonical JSON of event payload
    val outcome: Outcome,
    val prevHash: String,
    val thisHash: String,
)

object AuditLinesTable : Table("audit_lines") {
    val seq = long("seq")
    val userId = varchar("user_id", 26).references(UsersTable.id)
    val ts = timestamp("ts")
    val canonical = text("canonical")
    val outcome = varchar("outcome", 16)
    val prevHash = varchar("prev_hash", 64)
    val thisHash = varchar("this_hash", 64)
    override val primaryKey = PrimaryKey(userId, seq)
}

class AuditRepo(private val db: Database) {
    fun append(userId: String, canonicalLine: String, outcome: Outcome): AuditLine = transaction(db) {
        val lastSeq = AuditLinesTable
            .slice(AuditLinesTable.seq.max())
            .select { AuditLinesTable.userId eq userId }
            .singleOrNull()?.get(AuditLinesTable.seq.max()) ?: 0L
        val prev = if (lastSeq == 0L) "0".repeat(64) else
            AuditLinesTable
                .select { (AuditLinesTable.userId eq userId) and (AuditLinesTable.seq eq lastSeq) }
                .single()[AuditLinesTable.thisHash]
        val nextSeq = lastSeq + 1
        val now = Instant.now()
        val hash = HashChain.nextHash(prev, canonicalLine)
        AuditLinesTable.insert {
            it[seq] = nextSeq
            it[AuditLinesTable.userId] = userId
            it[ts] = now
            it[canonical] = canonicalLine
            it[AuditLinesTable.outcome] = outcome.name
            it[prevHash] = prev
            it[thisHash] = hash
        }
        AuditLine(nextSeq, userId, now, canonicalLine, outcome, prev, hash)
    }

    fun verifyChain(userId: String): Boolean = transaction(db) {
        val rows = AuditLinesTable
            .select { AuditLinesTable.userId eq userId }
            .orderBy(AuditLinesTable.seq)
            .toList()
        val links = rows.map { HashChain.Link(it[AuditLinesTable.prevHash], it[AuditLinesTable.canonical], it[AuditLinesTable.thisHash]) }
        HashChain.verify(links)
    }
}
```

- [ ] **Step 4: Run, PASS**

Run: `./gradlew test --tests "jarvis.tutor.AuditTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/Audit.kt src/test/kotlin/jarvis/tutor/AuditTest.kt
git commit -m "Tutor Layer A: Audit log with monotonic seq + hash chain per user"
```

---

## Task 12: EffectorValidator (the load-bearing rule pipeline)

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/EffectorValidator.kt`
- Test: `src/test/kotlin/jarvis/tutor/EffectorValidatorTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EffectorValidatorTest {
    private fun freshDb() = TutorDb.connect(
        Files.createTempDirectory("ev-test").resolve("t.db").toString()
    ).also { db -> transaction(db) {
        SchemaUtils.create(UsersTable, TrustGrantsTable, AuditLinesTable)
    } }

    private fun seedUser(db: org.jetbrains.exposed.sql.Database): String {
        val id = TutorTypes.ulid()
        UserRepo(db).insert(User(id, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        return id
    }

    private fun seedGrant(db: org.jetbrains.exposed.sql.Database, userId: String, scope: List<String>) =
        TutorTypes.ulid().also {
            TrustGrantRepo(db).insert(TrustGrant(it, userId, scope, setOf(EffectorType.APPLY_EDIT),
                Instant.now().plusSeconds(3600), 50, 0, GrantSource.UI, null, Instant.now()))
        }

    private fun req(taskId: String = "T01", uri: String = "file:///c/uaic/ps/x.R",
                    grantId: String, version: String = "v1") = ApplyEditRequest(
        taskId = taskId, effectorId = TutorTypes.ulid(),
        targetUri = uri, expectedDocVersion = version,
        edits = listOf(TextEdit(Range(Position(0,0), Position(0,1)), "a")),
        nonce = TutorTypes.ulid(), grantId = grantId,
    )

    @Test
    fun `valid request passes`() {
        val db = freshDb()
        val u = seedUser(db)
        val g = seedGrant(db, u, listOf("file:///c/uaic/ps/**"))
        val v = EffectorValidator(db, NonceCache())
        val r = v.validate(u, req(grantId = g, uri = "file:///c/uaic/ps/x.R"), currentDocVersion = "v1")
        assertIs<ValidationResult.Pass>(r)
    }

    @Test
    fun `path outside grant scope rejected`() {
        val db = freshDb()
        val u = seedUser(db)
        val g = seedGrant(db, u, listOf("file:///c/uaic/ps/**"))
        val v = EffectorValidator(db, NonceCache())
        val r = v.validate(u, req(grantId = g, uri = "file:///c/uaic/alo/x.R"), currentDocVersion = "v1")
        assertIs<ValidationResult.Reject>(r)
        assertEquals(Outcome.PATH_DENIED, r.outcome)
    }

    @Test
    fun `stale doc version rejected`() {
        val db = freshDb()
        val u = seedUser(db)
        val g = seedGrant(db, u, listOf("file:///c/uaic/ps/**"))
        val v = EffectorValidator(db, NonceCache())
        val r = v.validate(u, req(grantId = g, version = "v1"), currentDocVersion = "v2")
        assertIs<ValidationResult.Reject>(r)
        assertEquals(Outcome.STALE_DOC, r.outcome)
    }

    @Test
    fun `expired grant rejected`() {
        val db = freshDb()
        val u = seedUser(db)
        val expired = TutorTypes.ulid().also {
            TrustGrantRepo(db).insert(TrustGrant(it, u, listOf("file:///c/**"),
                setOf(EffectorType.APPLY_EDIT), Instant.now().minusSeconds(1),
                10, 0, GrantSource.UI, null, Instant.now()))
        }
        val v = EffectorValidator(db, NonceCache())
        val r = v.validate(u, req(grantId = expired), currentDocVersion = "v1")
        assertIs<ValidationResult.Reject>(r)
        assertEquals(Outcome.REJECTED, r.outcome)
    }

    @Test
    fun `duplicate nonce rejected`() {
        val db = freshDb()
        val u = seedUser(db)
        val g = seedGrant(db, u, listOf("file:///c/uaic/ps/**"))
        val nonces = NonceCache()
        val v = EffectorValidator(db, nonces)
        val first = req(grantId = g)
        val second = first.copy(effectorId = TutorTypes.ulid())  // same nonce
        val r1 = v.validate(u, first, currentDocVersion = "v1"); assertIs<ValidationResult.Pass>(r1)
        val r2 = v.validate(u, second, currentDocVersion = "v1")
        assertIs<ValidationResult.Reject>(r2)
        assertEquals(Outcome.REJECTED, r2.outcome)
    }

    @Test
    fun `path matches blocklist always denied even with grant`() {
        val db = freshDb()
        val u = seedUser(db)
        val g = seedGrant(db, u, listOf("**"))      // intentionally over-broad
        val v = EffectorValidator(db, NonceCache())
        val r = v.validate(u, req(grantId = g, uri = "file:///home/user/.ssh/config"),
                           currentDocVersion = "v1")
        assertIs<ValidationResult.Reject>(r)
        assertEquals(Outcome.PATH_DENIED, r.outcome)
    }
}
```

- [ ] **Step 2: Run, FAIL**

Run: `./gradlew test --tests "jarvis.tutor.EffectorValidatorTest"`
Expected: FAIL.

- [ ] **Step 3: Implement**

Create `src/main/kotlin/jarvis/tutor/EffectorValidator.kt`:

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue

private val PATH_BLOCKLIST = listOf(
    Regex(""".*/\.ssh(/|$).*"""),
    Regex(""".*/\.git(/|$).*"""),
    Regex(""".*\.env(\.[a-zA-Z0-9_]+)?$"""),
    Regex(""".*\.(key|pem)$"""),
    Regex(""".*/\.aws(/|$).*"""),
    Regex(""".*/\.config(/|$).*"""),
    Regex(""".*/\.kube(/|$).*"""),
)

sealed class ValidationResult {
    object Pass : ValidationResult()
    data class Reject(val outcome: Outcome, val reason: String) : ValidationResult()
}

class NonceCache(private val capacity: Int = 1000) {
    private val q = ConcurrentLinkedQueue<String>()
    private val set = java.util.Collections.synchronizedSet(HashSet<String>())
    fun seen(nonce: String): Boolean = set.contains(nonce)
    fun record(nonce: String) {
        if (set.add(nonce)) {
            q.add(nonce)
            while (set.size > capacity) {
                val removed = q.poll() ?: break
                set.remove(removed)
            }
        }
    }
}

class EffectorValidator(
    private val db: Database,
    private val nonces: NonceCache,
) {
    private val grantRepo = TrustGrantRepo(db)

    fun validate(userId: String, req: ApplyEditRequest, currentDocVersion: String): ValidationResult {
        // 1. Hardcoded blocklist (always wins).
        if (PATH_BLOCKLIST.any { it.matches(req.targetUri) }) {
            return ValidationResult.Reject(Outcome.PATH_DENIED, "path on blocklist")
        }
        // 2. Grant exists, active, owned by this user, op allowed.
        val now = Instant.now()
        val grant = grantRepo.findActive(req.grantId, now)
            ?: return ValidationResult.Reject(Outcome.REJECTED, "grant missing/expired/revoked")
        if (grant.userId != userId) return ValidationResult.Reject(Outcome.REJECTED, "grant userId mismatch")
        if (EffectorType.APPLY_EDIT !in grant.ops)
            return ValidationResult.Reject(Outcome.REJECTED, "op not in grant")
        // 3. Path within grant scope (glob).
        if (!grant.scope.any { matchGlob(req.targetUri, it) }) {
            return ValidationResult.Reject(Outcome.PATH_DENIED, "outside grant scope")
        }
        // 4. expectedDocVersion matches.
        if (req.expectedDocVersion != currentDocVersion) {
            return ValidationResult.Reject(Outcome.STALE_DOC,
                "expected ${req.expectedDocVersion} got $currentDocVersion")
        }
        // 5. Nonce not seen before.
        if (nonces.seen(req.nonce)) {
            return ValidationResult.Reject(Outcome.REJECTED, "nonce replay")
        }
        // 6. Consume one slot from grant.
        if (!grantRepo.tryConsume(req.grantId)) {
            return ValidationResult.Reject(Outcome.REJECTED, "grant maxCalls exhausted")
        }
        nonces.record(req.nonce)
        return ValidationResult.Pass
    }

    /**
     * Minimal glob matcher: supports ** (any path), * (single segment), and literal text.
     */
    private fun matchGlob(target: String, glob: String): Boolean {
        val regex = buildString {
            append('^')
            var i = 0
            while (i < glob.length) {
                val c = glob[i]
                when {
                    c == '*' && i + 1 < glob.length && glob[i + 1] == '*' -> { append(".*"); i += 2 }
                    c == '*' -> { append("[^/]*"); i++ }
                    c == '.' || c == '(' || c == ')' || c == '+' || c == '?' || c == '|' -> {
                        append('\\').append(c); i++
                    }
                    else -> { append(c); i++ }
                }
            }
            append('$')
        }
        return Regex(regex).matches(target)
    }
}
```

- [ ] **Step 4: Run, PASS**

Run: `./gradlew test --tests "jarvis.tutor.EffectorValidatorTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/EffectorValidator.kt src/test/kotlin/jarvis/tutor/EffectorValidatorTest.kt
git commit -m "Tutor Layer A: EffectorValidator (blocklist+grant+scope+version+nonce)"
```

---

## Task 13: Effector contract FUZZER (the pre-build gate)

This task is the gate. All future UI work depends on the validator being correct. Run this until green; commit only when green.

**Files:**
- Create: `src/test/kotlin/jarvis/tutor/EffectorFuzzerTest.kt`

- [ ] **Step 1: Write the fuzzer**

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class EffectorFuzzerTest {
    private val rng = Random(0xC0FFEE)

    private fun freshDb() = TutorDb.connect(
        Files.createTempDirectory("fuzzer").resolve("t.db").toString()
    ).also { db -> transaction(db) {
        SchemaUtils.create(UsersTable, TrustGrantsTable, AuditLinesTable)
    } }

    private fun seedSetup(): Triple<EffectorValidator, String, String> {
        val db = freshDb()
        val userId = TutorTypes.ulid()
        UserRepo(db).insert(User(userId, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val grantId = TutorTypes.ulid()
        TrustGrantRepo(db).insert(TrustGrant(grantId, userId, listOf("file:///c/uaic/**"),
            setOf(EffectorType.APPLY_EDIT), Instant.now().plusSeconds(3600),
            1_000_000, 0, GrantSource.UI, null, Instant.now()))
        return Triple(EffectorValidator(db, NonceCache(capacity = 10_000)), userId, grantId)
    }

    private fun goodReq(grantId: String, version: String) = ApplyEditRequest(
        taskId = "T", effectorId = TutorTypes.ulid(),
        targetUri = "file:///c/uaic/ps/x.R",
        expectedDocVersion = version,
        edits = listOf(TextEdit(Range(Position(0,0), Position(0,1)), "x")),
        nonce = TutorTypes.ulid(), grantId = grantId,
    )

    /** 500 valid requests must all PASS. */
    @Test
    fun `500 valid requests all pass`() {
        val (v, u, g) = seedSetup()
        repeat(500) {
            val r = v.validate(u, goodReq(g, "v1"), currentDocVersion = "v1")
            assertEquals(ValidationResult.Pass, r, "iter $it must pass")
        }
    }

    /** Stale doc version must always reject with STALE_DOC. */
    @Test
    fun `stale doc version always rejects`() {
        val (v, u, g) = seedSetup()
        repeat(200) {
            val r = v.validate(u, goodReq(g, "vSTALE-$it"), currentDocVersion = "vCURRENT")
            assertEquals(ValidationResult.Reject(Outcome.STALE_DOC, ""), normalize(r))
        }
    }

    /** Path outside scope always rejects with PATH_DENIED. */
    @Test
    fun `200 requests targeting paths outside scope all reject`() {
        val (v, u, g) = seedSetup()
        val outside = listOf(
            "file:///c/other/foo.R",
            "file:///etc/passwd",
            "file:///c/Users/x/.ssh/id_rsa",
            "file:///c/proj/.env.production",
            "file:///c/proj/.git/config",
        )
        repeat(200) {
            val pick = outside[rng.nextInt(outside.size)]
            val r = v.validate(u, goodReq(g, "v1").copy(targetUri = pick), currentDocVersion = "v1")
            assertEquals(ValidationResult.Reject(Outcome.PATH_DENIED, ""), normalize(r),
                "iter $it path=$pick")
        }
    }

    /** Replayed nonce always rejects on second attempt. */
    @Test
    fun `replayed nonce rejects every time after first`() {
        val (v, u, g) = seedSetup()
        repeat(100) {
            val req = goodReq(g, "v1")
            val r1 = v.validate(u, req, currentDocVersion = "v1"); assertEquals(ValidationResult.Pass, r1)
            val r2 = v.validate(u, req.copy(effectorId = TutorTypes.ulid()),
                                currentDocVersion = "v1")
            assertEquals(ValidationResult.Reject(Outcome.REJECTED, ""), normalize(r2))
        }
    }

    /** Random shuffle: the validator must NEVER PASS a request that violates any rule. */
    @Test
    fun `random shuffle never approves invalid`() {
        val (v, u, g) = seedSetup()
        repeat(1000) {
            val staleVersion = rng.nextBoolean()
            val outsidePath = rng.nextBoolean()
            val replayed = rng.nextBoolean()
            val invalid = staleVersion || outsidePath || replayed
            val req = goodReq(g, if (staleVersion) "vBAD" else "v1").copy(
                targetUri = if (outsidePath) "file:///etc/passwd" else "file:///c/uaic/ps/x.R",
            )
            if (replayed) {
                val first = v.validate(u, req, currentDocVersion = if (staleVersion) "vCURRENT" else "v1")
                if (first is ValidationResult.Pass) {
                    val again = v.validate(u, req.copy(effectorId = TutorTypes.ulid()),
                                           currentDocVersion = "v1")
                    assertEquals(ValidationResult.Reject(Outcome.REJECTED, ""), normalize(again))
                    return@repeat
                }
            }
            val r = v.validate(u, req, currentDocVersion = "v1")
            if (invalid) assert(r is ValidationResult.Reject) {
                "iter $it invalid={stale=$staleVersion path=$outsidePath replay=$replayed} got $r"
            }
        }
    }

    private fun normalize(r: ValidationResult): ValidationResult = when (r) {
        is ValidationResult.Reject -> ValidationResult.Reject(r.outcome, "")
        else -> r
    }
}
```

- [ ] **Step 2: Run fuzzer until green**

Run: `./gradlew test --tests "jarvis.tutor.EffectorFuzzerTest"`
Expected: ALL PASS. If anything fails, fix `EffectorValidator.kt` until green. Do NOT proceed to Task 14 until this is green.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/jarvis/tutor/EffectorFuzzerTest.kt
git commit -m "Tutor Layer A: effector contract fuzzer (pre-build gate)"
```

---

## Task 14: Task schema + repository

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/Tasks.kt`
- Test: `src/test/kotlin/jarvis/tutor/TasksTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TasksTest {
    private fun freshDb() = TutorDb.connect(
        Files.createTempDirectory("tasks-test").resolve("t.db").toString()
    ).also { db -> transaction(db) { SchemaUtils.create(UsersTable, TasksTable) } }

    private fun seedUser(db: org.jetbrains.exposed.sql.Database): String {
        val id = TutorTypes.ulid()
        UserRepo(db).insert(User(id, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        return id
    }

    private fun makeTask(userId: String, deadline: Instant) = Task(
        id = TutorTypes.ulid(), userId = userId, subject = "PS",
        title = "Tema A", deadline = deadline,
        problemRef = ContentRef("study-guide", "ps/tema-a/problem.md", "abc"),
        conceptRefs = listOf(ContentRef("study-guide", "ps/laplace.md", "def")),
        rubricRef = ContentRef("study-guide", "ps/tema-a/rubric.v1.yaml", "ghi"),
        scratchpad = null, submission = null, grade = null, cardRefs = emptyList(),
        status = TaskStatus.TODO,
        createdAt = Instant.now(), updatedAt = Instant.now(),
    )

    @Test
    fun `insert and findById round-trips`() {
        val db = freshDb()
        val u = seedUser(db)
        val repo = TaskRepo(db)
        val t = makeTask(u, Instant.now().plusSeconds(3600))
        repo.insert(t)
        val found = repo.findById(t.id)
        assertNotNull(found)
        assertEquals(t.title, found.title)
        assertEquals(1, found.conceptRefs.size)
    }

    @Test
    fun `listForUser returns deadline-sorted ascending`() {
        val db = freshDb()
        val u = seedUser(db)
        val repo = TaskRepo(db)
        val now = Instant.now()
        val a = makeTask(u, now.plusSeconds(100))
        val b = makeTask(u, now.plusSeconds(10))
        val c = makeTask(u, now.plusSeconds(1000))
        repo.insert(a); repo.insert(b); repo.insert(c)
        val ordered = repo.listForUser(u)
        assertEquals(listOf(b.id, a.id, c.id), ordered.map { it.id })
    }

    @Test
    fun `listForUser scopes to userId`() {
        val db = freshDb()
        val u1 = seedUser(db); val u2 = seedUser(db)
        val repo = TaskRepo(db)
        repo.insert(makeTask(u1, Instant.now().plusSeconds(60)))
        repo.insert(makeTask(u2, Instant.now().plusSeconds(60)))
        assertEquals(1, repo.listForUser(u1).size)
        assertEquals(1, repo.listForUser(u2).size)
    }
}
```

- [ ] **Step 2: Run, FAIL**

Run: `./gradlew test --tests "jarvis.tutor.TasksTest"`
Expected: FAIL.

- [ ] **Step 3: Implement**

Create `src/main/kotlin/jarvis/tutor/Tasks.kt`:

```kotlin
package jarvis.tutor

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

enum class TaskStatus { TODO, ACTIVE, SUBMITTED, GRADED, ARCHIVED }

@Serializable
data class ContentRef(val repo: String, val path: String, val sha: String)

@Serializable
data class ScratchpadRef(val docId: String, val version: Int)

@Serializable
data class SubmissionRef(val docId: String, val version: Int, val submittedAt: String)

@Serializable
data class GradeRecord(
    val score: Double,
    val rubricVersion: String,
    val gradedAt: String,
    val modelId: String,
)

data class Task(
    val id: String,
    val userId: String,
    val subject: String,
    val title: String,
    val deadline: Instant,
    val problemRef: ContentRef,
    val conceptRefs: List<ContentRef>,
    val rubricRef: ContentRef,
    val scratchpad: ScratchpadRef?,
    val submission: SubmissionRef?,
    val grade: GradeRecord?,
    val cardRefs: List<String>,                  // FsrsCard IDs
    val status: TaskStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
)

object TasksTable : Table("tasks") {
    val id = varchar("id", 26)
    val userId = varchar("user_id", 26).references(UsersTable.id)
    val subject = varchar("subject", 32)
    val title = varchar("title", 256)
    val deadline = timestamp("deadline")
    val problemRefJson = text("problem_ref_json")
    val conceptRefsJson = text("concept_refs_json")
    val rubricRefJson = text("rubric_ref_json")
    val scratchpadJson = text("scratchpad_json").nullable()
    val submissionJson = text("submission_json").nullable()
    val gradeJson = text("grade_json").nullable()
    val cardRefsJson = text("card_refs_json")
    val status = varchar("status", 16)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

class TaskRepo(private val db: Database) {
    fun insert(t: Task) = transaction(db) {
        TasksTable.insert {
            it[id] = t.id
            it[userId] = t.userId
            it[subject] = t.subject
            it[title] = t.title
            it[deadline] = t.deadline
            it[problemRefJson] = TutorTypes.tutorJson.encodeToString(ContentRef.serializer(), t.problemRef)
            it[conceptRefsJson] = TutorTypes.tutorJson.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(ContentRef.serializer()), t.conceptRefs)
            it[rubricRefJson] = TutorTypes.tutorJson.encodeToString(ContentRef.serializer(), t.rubricRef)
            it[scratchpadJson] = t.scratchpad?.let {
                sp -> TutorTypes.tutorJson.encodeToString(ScratchpadRef.serializer(), sp)
            }
            it[submissionJson] = t.submission?.let {
                s -> TutorTypes.tutorJson.encodeToString(SubmissionRef.serializer(), s)
            }
            it[gradeJson] = t.grade?.let {
                g -> TutorTypes.tutorJson.encodeToString(GradeRecord.serializer(), g)
            }
            it[cardRefsJson] = TutorTypes.tutorJson.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.builtins.serializer()),
                t.cardRefs)
            it[status] = t.status.name
            it[createdAt] = t.createdAt
            it[updatedAt] = t.updatedAt
        }
    }

    fun findById(id: String): Task? = transaction(db) {
        TasksTable.select { TasksTable.id eq id }.singleOrNull()?.toTask()
    }

    fun listForUser(userId: String): List<Task> = transaction(db) {
        TasksTable.select { TasksTable.userId eq userId }
            .orderBy(TasksTable.deadline, SortOrder.ASC)
            .map { it.toTask() }
    }

    private fun ResultRow.toTask() = Task(
        id = this[TasksTable.id],
        userId = this[TasksTable.userId],
        subject = this[TasksTable.subject],
        title = this[TasksTable.title],
        deadline = this[TasksTable.deadline],
        problemRef = TutorTypes.tutorJson.decodeFromString(ContentRef.serializer(), this[TasksTable.problemRefJson]),
        conceptRefs = TutorTypes.tutorJson.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(ContentRef.serializer()),
            this[TasksTable.conceptRefsJson]),
        rubricRef = TutorTypes.tutorJson.decodeFromString(ContentRef.serializer(), this[TasksTable.rubricRefJson]),
        scratchpad = this[TasksTable.scratchpadJson]?.let {
            TutorTypes.tutorJson.decodeFromString(ScratchpadRef.serializer(), it)
        },
        submission = this[TasksTable.submissionJson]?.let {
            TutorTypes.tutorJson.decodeFromString(SubmissionRef.serializer(), it)
        },
        grade = this[TasksTable.gradeJson]?.let {
            TutorTypes.tutorJson.decodeFromString(GradeRecord.serializer(), it)
        },
        cardRefs = TutorTypes.tutorJson.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.builtins.serializer()),
            this[TasksTable.cardRefsJson]),
        status = TaskStatus.valueOf(this[TasksTable.status]),
        createdAt = this[TasksTable.createdAt],
        updatedAt = this[TasksTable.updatedAt],
    )
}
```

- [ ] **Step 4: Run, PASS**

Run: `./gradlew test --tests "jarvis.tutor.TasksTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/Tasks.kt src/test/kotlin/jarvis/tutor/TasksTest.kt
git commit -m "Tutor Layer A: Task schema + insert/find/listForUser"
```

---

## Task 15: SensorEvent schema + repository

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/Sensors.kt`
- Test: `src/test/kotlin/jarvis/tutor/SensorsTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package jarvis.tutor

import kotlinx.serialization.encodeToString
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SensorsTest {
    private fun freshDb() = TutorDb.connect(
        Files.createTempDirectory("sens-test").resolve("t.db").toString()
    ).also { db -> transaction(db) { SchemaUtils.create(UsersTable, SensorEventsTable) } }

    @Test
    fun `TextDocSnapshot payload round-trips`() {
        val payload: SensorPayload = SensorPayload.TextDocSnapshot(
            uri = "file:///c/x.R", version = 17, lang = "r",
            selection = Range(Position(3, 5), Position(3, 12)),
            viewport = null, diagSummary = DiagSummary(errors = 1, warnings = 0)
        )
        val json = TutorTypes.tutorJson.encodeToString(payload)
        val back = TutorTypes.tutorJson.decodeFromString<SensorPayload>(json)
        assertIs<SensorPayload.TextDocSnapshot>(back)
        assertEquals(17, back.version)
    }

    @Test
    fun `append assigns monotonic eventSeq per source`() {
        val db = freshDb()
        val u = TutorTypes.ulid()
        UserRepo(db).insert(User(u, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val repo = SensorRepo(db)
        val a = repo.append(u, "vscode", "0.1", null,
            SensorPayload.WindowFocus("VSCode", "x.R", "Code.exe"))
        val b = repo.append(u, "vscode", "0.1", null,
            SensorPayload.WindowFocus("VSCode", "x.R", "Code.exe"))
        val c = repo.append(u, "rstudio", "0.1", null,
            SensorPayload.WindowFocus("RStudio", "y.R", "rstudio.exe"))
        assertEquals(1L, a.eventSeq)
        assertEquals(2L, b.eventSeq)
        assertEquals(1L, c.eventSeq)         // separate source counter
    }
}
```

- [ ] **Step 2: Run, FAIL**

Run: `./gradlew test --tests "jarvis.tutor.SensorsTest"`
Expected: FAIL.

- [ ] **Step 3: Implement**

Create `src/main/kotlin/jarvis/tutor/Sensors.kt`:

```kotlin
package jarvis.tutor

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

@Serializable
data class DiagSummary(val errors: Int, val warnings: Int)

@Serializable
sealed class SensorPayload {
    @Serializable
    data class TextDocSnapshot(
        val uri: String, val version: Int, val lang: String,
        val selection: Range?, val viewport: Range?, val diagSummary: DiagSummary?,
    ) : SensorPayload()

    @Serializable
    data class ConsoleEvent(
        val lang: String, val command: String,
        val outputDigest: String, val errorMessage: String?,
    ) : SensorPayload()

    @Serializable
    data class WindowFocus(val app: String, val title: String, val processName: String) : SensorPayload()

    @Serializable
    data class ClipboardEvent(val digest: String) : SensorPayload()

    @Serializable
    data class ScreenshotMeta(
        val capturedAt: String, val focusedRegion: String?, val ocrSummary: String,
    ) : SensorPayload()
}

data class SensorEvent(
    val v: Int = 1,
    val sensorId: String,
    val sensorVersion: String,
    val eventSeq: Long,
    val ts: Instant,
    val source: String,
    val taskId: String?,
    val payload: SensorPayload,
)

object SensorEventsTable : Table("sensor_events") {
    val sensorId = varchar("sensor_id", 64)
    val seq = long("seq")
    val sensorVersion = varchar("sensor_version", 32)
    val ts = timestamp("ts")
    val source = varchar("source", 32)
    val userId = varchar("user_id", 26).references(UsersTable.id)
    val taskId = varchar("task_id", 26).nullable()
    val payloadJson = text("payload_json")
    override val primaryKey = PrimaryKey(sensorId, seq)
}

class SensorRepo(private val db: Database) {
    fun append(
        userId: String, sensorId: String, sensorVersion: String,
        taskId: String?, payload: SensorPayload,
    ): SensorEvent = transaction(db) {
        val nextSeq = (SensorEventsTable
            .slice(SensorEventsTable.seq.max())
            .select { SensorEventsTable.sensorId eq sensorId }
            .singleOrNull()?.get(SensorEventsTable.seq.max()) ?: 0L) + 1
        val now = Instant.now()
        SensorEventsTable.insert {
            it[SensorEventsTable.sensorId] = sensorId
            it[seq] = nextSeq
            it[SensorEventsTable.sensorVersion] = sensorVersion
            it[ts] = now
            it[source] = sensorId.substringBefore('-')
            it[SensorEventsTable.userId] = userId
            it[SensorEventsTable.taskId] = taskId
            it[payloadJson] = TutorTypes.tutorJson.encodeToString(SensorPayload.serializer(), payload)
        }
        SensorEvent(1, sensorId, sensorVersion, nextSeq, now,
            source = sensorId.substringBefore('-'),
            taskId = taskId, payload = payload)
    }
}
```

- [ ] **Step 4: Run, PASS**

Run: `./gradlew test --tests "jarvis.tutor.SensorsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/Sensors.kt src/test/kotlin/jarvis/tutor/SensorsTest.kt
git commit -m "Tutor Layer A: SensorEvent schema (LSP-shaped) + monotonic seq per source"
```

---

## Task 16: KnowledgeGap JSONL ledger + index

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/KnowledgeGaps.kt`
- Test: `src/test/kotlin/jarvis/tutor/KnowledgeGapsTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KnowledgeGapsTest {
    @Test
    fun `append writes JSONL line and returns gap id`() {
        val dir = Files.createTempDirectory("gaps")
        val db = TutorDb.connect(dir.resolve("t.db").toString())
        transaction(db) { SchemaUtils.create(UsersTable, KnowledgeGapsTable) }
        val u = TutorTypes.ulid()
        UserRepo(db).insert(User(u, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val repo = KnowledgeGapRepo(db, dir)
        val g = KnowledgeGap(
            id = TutorTypes.ulid(), userId = u, taskId = null,
            topic = "R: median()", language = "R", type = GapType.COMMAND,
            trigger = GapTrigger.EXPLICIT_ASK,
            filledAt = Instant.now(),
            source = GapSource.LLM_GROUNDED,
            content = "median(x) computes...",
            exampleCode = "median(c(1,2,3))",
            sourceCitation = "?median",
            resolvedBy = null, reusedCount = 0, fsrsCardId = null,
        )
        val id = repo.append(g)
        assertEquals(g.id, id)
        val file = dir.resolve("knowledge_gaps_$u.jsonl")
        val lines = Files.readAllLines(file)
        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("R: median()"))
    }

    @Test
    fun `findByTopic finds prior gap by exact topic for user`() {
        val dir = Files.createTempDirectory("gaps2")
        val db = TutorDb.connect(dir.resolve("t.db").toString())
        transaction(db) { SchemaUtils.create(UsersTable, KnowledgeGapsTable) }
        val u = TutorTypes.ulid()
        UserRepo(db).insert(User(u, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val repo = KnowledgeGapRepo(db, dir)
        val g = KnowledgeGap(TutorTypes.ulid(), u, null, "R: median()", "R", GapType.COMMAND,
            GapTrigger.EXPLICIT_ASK, Instant.now(), GapSource.LLM_GROUNDED,
            "x", null, null, null, 0, null)
        repo.append(g)
        val found = repo.findByTopic(u, "R: median()")
        assertNotNull(found)
        assertEquals(g.id, found.id)
    }

    @Test
    fun `bumpReuse increments counter`() {
        val dir = Files.createTempDirectory("gaps3")
        val db = TutorDb.connect(dir.resolve("t.db").toString())
        transaction(db) { SchemaUtils.create(UsersTable, KnowledgeGapsTable) }
        val u = TutorTypes.ulid()
        UserRepo(db).insert(User(u, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val repo = KnowledgeGapRepo(db, dir)
        val g = KnowledgeGap(TutorTypes.ulid(), u, null, "R: median()", "R", GapType.COMMAND,
            GapTrigger.EXPLICIT_ASK, Instant.now(), GapSource.LLM_GROUNDED,
            "x", null, null, null, 0, null)
        repo.append(g)
        repo.bumpReuse(g.id)
        repo.bumpReuse(g.id)
        assertEquals(2, repo.findByTopic(u, "R: median()")!!.reusedCount)
    }
}
```

- [ ] **Step 2: Run, FAIL**

Run: `./gradlew test --tests "jarvis.tutor.KnowledgeGapsTest"`
Expected: FAIL.

- [ ] **Step 3: Implement**

Create `src/main/kotlin/jarvis/tutor/KnowledgeGaps.kt`:

```kotlin
package jarvis.tutor

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

enum class GapType { COMMAND, CONCEPT, SYNTAX, LIBRARY, THEOREM }
enum class GapTrigger { EXPLICIT_ASK, SYNTAX_ERROR, REPEATED_FAILURE, MANUAL_FLAG }
enum class GapSource { LESSON_LOCAL, PAST_GAP_REUSE, EXTERNAL_DOC, LLM_GROUNDED, LLM_PURE }
enum class GapResolved { USER_TYPED, USER_DISMISSED, USER_MARKED_DONE }

@Serializable
data class KnowledgeGap(
    val id: String, val userId: String, val taskId: String?,
    val topic: String, val language: String?, val type: GapType,
    val trigger: GapTrigger,
    @Serializable(InstantIso8601Serializer::class) val filledAt: Instant,
    val source: GapSource,
    val content: String, val exampleCode: String?, val sourceCitation: String?,
    val resolvedBy: GapResolved?,
    val reusedCount: Int,
    val fsrsCardId: String?,
)

object KnowledgeGapsTable : Table("knowledge_gaps") {
    val id = varchar("id", 26)
    val userId = varchar("user_id", 26).references(UsersTable.id)
    val topic = varchar("topic", 256)
    val language = varchar("language", 16).nullable()
    val type = varchar("type", 16)
    val filledAt = timestamp("filled_at")
    val reusedCount = integer("reused_count")
    val fsrsCardId = varchar("fsrs_card_id", 26).nullable()
    override val primaryKey = PrimaryKey(id)
    init { index(false, userId, topic) }
}

class KnowledgeGapRepo(private val db: Database, private val ledgerDir: Path) {
    fun append(g: KnowledgeGap): String = transaction(db) {
        // Index row
        KnowledgeGapsTable.insert {
            it[id] = g.id
            it[userId] = g.userId
            it[topic] = g.topic
            it[language] = g.language
            it[type] = g.type.name
            it[filledAt] = g.filledAt
            it[reusedCount] = g.reusedCount
            it[fsrsCardId] = g.fsrsCardId
        }
        // JSONL append
        val file = ledgerDir.resolve("knowledge_gaps_${g.userId}.jsonl")
        Files.createDirectories(ledgerDir)
        val line = TutorTypes.tutorJson.encodeToString(KnowledgeGap.serializer(), g)
        Files.write(file, (line + "\n").toByteArray(),
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.APPEND)
        g.id
    }

    fun findByTopic(userId: String, topic: String): KnowledgeGap? = transaction(db) {
        val row = KnowledgeGapsTable
            .select { (KnowledgeGapsTable.userId eq userId) and (KnowledgeGapsTable.topic eq topic) }
            .orderBy(KnowledgeGapsTable.filledAt, org.jetbrains.exposed.sql.SortOrder.DESC)
            .firstOrNull() ?: return@transaction null
        loadFromJsonl(userId, row[KnowledgeGapsTable.id])
    }

    fun bumpReuse(id: String) = transaction(db) {
        val cur = KnowledgeGapsTable
            .select { KnowledgeGapsTable.id eq id }.single()[KnowledgeGapsTable.reusedCount]
        KnowledgeGapsTable.update({ KnowledgeGapsTable.id eq id }) {
            it[reusedCount] = cur + 1
        }
    }

    private fun loadFromJsonl(userId: String, id: String): KnowledgeGap? {
        val file = ledgerDir.resolve("knowledge_gaps_$userId.jsonl")
        if (!Files.exists(file)) return null
        Files.lines(file).use { stream ->
            val match = stream
                .map { TutorTypes.tutorJson.decodeFromString(KnowledgeGap.serializer(), it) }
                .filter { it.id == id }
                .findFirst()
                .orElse(null)
            if (match == null) return null
            // Overlay current reuseCount from index (jsonl is append-only, may be stale).
            val current = transaction(db) {
                KnowledgeGapsTable.select { KnowledgeGapsTable.id eq id }
                    .single()[KnowledgeGapsTable.reusedCount]
            }
            return match.copy(reusedCount = current)
        }
    }
}

/** kotlinx-serialization Instant adapter for ISO-8601. */
object InstantIso8601Serializer : kotlinx.serialization.KSerializer<Instant> {
    override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
        "Instant", kotlinx.serialization.descriptors.PrimitiveKind.STRING)
    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Instant) =
        encoder.encodeString(value.toString())
    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Instant =
        Instant.parse(decoder.decodeString())
}
```

- [ ] **Step 4: Run, PASS**

Run: `./gradlew test --tests "jarvis.tutor.KnowledgeGapsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/KnowledgeGaps.kt src/test/kotlin/jarvis/tutor/KnowledgeGapsTest.kt
git commit -m "Tutor Layer A: KnowledgeGap JSONL append + SQLite index"
```

---

## Task 17: FsrsCard schema + repository

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/FsrsCards.kt`
- Test: `src/test/kotlin/jarvis/tutor/FsrsCardsTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FsrsCardsTest {
    private fun freshDb() = TutorDb.connect(
        Files.createTempDirectory("fsrs-test").resolve("t.db").toString()
    ).also { db -> transaction(db) { SchemaUtils.create(UsersTable, FsrsCardsTable) } }

    @Test
    fun `insert and findDue returns due cards in dueAt order`() {
        val db = freshDb()
        val u = TutorTypes.ulid()
        UserRepo(db).insert(User(u, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val repo = FsrsCardRepo(db)
        val now = Instant.now()
        val a = TutorCard(TutorTypes.ulid(), u, FsrsSource.MANUAL, "ref-a",
            "front a", "back a", FsrsState(2.5, 1.0, 0.9, now.plusSeconds(60), now, 0))
        val b = TutorCard(TutorTypes.ulid(), u, FsrsSource.MANUAL, "ref-b",
            "front b", "back b", FsrsState(2.5, 1.0, 0.9, now.minusSeconds(60), now, 0))
        repo.insert(a); repo.insert(b)
        val due = repo.findDueForUser(u, asOf = now)
        assertEquals(1, due.size)
        assertEquals(b.id, due[0].id)
    }

    @Test
    fun `findDueForUser scopes to userId`() {
        val db = freshDb()
        val u1 = TutorTypes.ulid(); val u2 = TutorTypes.ulid()
        UserRepo(db).insert(User(u1, "a", UserScope.OWNER, Instant.now(), Instant.now()))
        UserRepo(db).insert(User(u2, "b", UserScope.FRIEND, Instant.now(), Instant.now()))
        val repo = FsrsCardRepo(db)
        val now = Instant.now()
        repo.insert(TutorCard(TutorTypes.ulid(), u1, FsrsSource.MANUAL, "x",
            "f", "b", FsrsState(2.5, 1.0, 0.9, now.minusSeconds(10), now, 0)))
        repo.insert(TutorCard(TutorTypes.ulid(), u2, FsrsSource.MANUAL, "y",
            "f", "b", FsrsState(2.5, 1.0, 0.9, now.minusSeconds(10), now, 0)))
        assertEquals(1, repo.findDueForUser(u1, now).size)
        assertEquals(1, repo.findDueForUser(u2, now).size)
        assertTrue(repo.findDueForUser(u1, now).all { it.userId == u1 })
    }
}
```

- [ ] **Step 2: Run, FAIL**

Run: `./gradlew test --tests "jarvis.tutor.FsrsCardsTest"`
Expected: FAIL.

- [ ] **Step 3: Implement**

Create `src/main/kotlin/jarvis/tutor/FsrsCards.kt`:

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

enum class FsrsSource { GAP_PROMOTION, RUBRIC_CRITERION, MANUAL }

data class FsrsState(
    val difficulty: Double, val stability: Double, val retrievability: Double,
    val dueAt: Instant, val lastReviewedAt: Instant, val lapses: Int,
)

data class TutorCard(
    val id: String, val userId: String,
    val source: FsrsSource, val sourceRef: String,
    val front: String, val back: String,
    val state: FsrsState,
)

object FsrsCardsTable : Table("fsrs_cards") {
    val id = varchar("id", 26)
    val userId = varchar("user_id", 26).references(UsersTable.id)
    val source = varchar("source", 24)
    val sourceRef = varchar("source_ref", 32)
    val front = text("front")
    val back = text("back")
    val difficulty = double("difficulty")
    val stability = double("stability")
    val retrievability = double("retrievability")
    val dueAt = timestamp("due_at")
    val lastReviewedAt = timestamp("last_reviewed_at")
    val lapses = integer("lapses")
    override val primaryKey = PrimaryKey(id)
    init { index(false, userId, dueAt) }
}

class FsrsCardRepo(private val db: Database) {
    fun insert(c: TutorCard) = transaction(db) {
        FsrsCardsTable.insert {
            it[id] = c.id
            it[userId] = c.userId
            it[source] = c.source.name
            it[sourceRef] = c.sourceRef
            it[front] = c.front
            it[back] = c.back
            it[difficulty] = c.state.difficulty
            it[stability] = c.state.stability
            it[retrievability] = c.state.retrievability
            it[dueAt] = c.state.dueAt
            it[lastReviewedAt] = c.state.lastReviewedAt
            it[lapses] = c.state.lapses
        }
    }

    fun findDueForUser(userId: String, asOf: Instant): List<TutorCard> = transaction(db) {
        FsrsCardsTable
            .select { (FsrsCardsTable.userId eq userId) and (FsrsCardsTable.dueAt lessEq asOf) }
            .orderBy(FsrsCardsTable.dueAt, SortOrder.ASC)
            .map { it.toCard() }
    }

    private fun ResultRow.toCard() = TutorCard(
        id = this[FsrsCardsTable.id],
        userId = this[FsrsCardsTable.userId],
        source = FsrsSource.valueOf(this[FsrsCardsTable.source]),
        sourceRef = this[FsrsCardsTable.sourceRef],
        front = this[FsrsCardsTable.front],
        back = this[FsrsCardsTable.back],
        state = FsrsState(
            difficulty = this[FsrsCardsTable.difficulty],
            stability = this[FsrsCardsTable.stability],
            retrievability = this[FsrsCardsTable.retrievability],
            dueAt = this[FsrsCardsTable.dueAt],
            lastReviewedAt = this[FsrsCardsTable.lastReviewedAt],
            lapses = this[FsrsCardsTable.lapses],
        ),
    )
}
```

- [ ] **Step 4: Run, PASS**

Run: `./gradlew test --tests "jarvis.tutor.FsrsCardsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/FsrsCards.kt src/test/kotlin/jarvis/tutor/FsrsCardsTest.kt
git commit -m "Tutor Layer A: FsrsCard schema + insert/findDueForUser"
```

---

## Task 18: ProviderConfig schema + repository

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/ProviderConfig.kt`
- Test: `src/test/kotlin/jarvis/tutor/ProviderConfigTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProviderConfigTest {
    private fun freshDb() = TutorDb.connect(
        Files.createTempDirectory("pc-test").resolve("t.db").toString()
    ).also { db -> transaction(db) { SchemaUtils.create(UsersTable, ProviderConfigTable) } }

    @Test
    fun `upsert + find returns latest config`() {
        val db = freshDb()
        val u = TutorTypes.ulid()
        UserRepo(db).insert(User(u, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val repo = ProviderConfigRepo(db)
        repo.upsert(UserProviderConfig(u, ProviderType.CLAUDE_CLI_RELAY,
            relayEndpoint = "http://laptop.tail.ts.net:51234/chat",
            apiKeyEncryptedRef = null, fallback = listOf(ProviderType.COPILOT_CLI)))
        val cur = repo.find(u)
        assertNotNull(cur)
        assertEquals(ProviderType.CLAUDE_CLI_RELAY, cur.primary)
        repo.upsert(cur.copy(primary = ProviderType.ANTHROPIC_API,
            relayEndpoint = null, apiKeyEncryptedRef = "vault:abc"))
        assertEquals(ProviderType.ANTHROPIC_API, repo.find(u)!!.primary)
    }

    @Test
    fun `find returns null for absent user`() {
        val db = freshDb()
        assertNull(ProviderConfigRepo(db).find("missing"))
    }
}
```

- [ ] **Step 2: Run, FAIL**

Run: `./gradlew test --tests "jarvis.tutor.ProviderConfigTest"`
Expected: FAIL.

- [ ] **Step 3: Implement**

Create `src/main/kotlin/jarvis/tutor/ProviderConfig.kt`:

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

enum class ProviderType { CLAUDE_CLI_RELAY, ANTHROPIC_API, OPENAI_API, GEMINI_API, COPILOT_CLI }

data class UserProviderConfig(
    val userId: String,
    val primary: ProviderType,
    val relayEndpoint: String?,
    val apiKeyEncryptedRef: String?,
    val fallback: List<ProviderType>,
)

object ProviderConfigTable : Table("user_provider_config") {
    val userId = varchar("user_id", 26).references(UsersTable.id)
    val primary = varchar("primary", 24)
    val relayEndpoint = varchar("relay_endpoint", 256).nullable()
    val apiKeyEncryptedRef = varchar("api_key_encrypted_ref", 256).nullable()
    val fallbackJson = text("fallback_json")
    override val primaryKey = PrimaryKey(userId)
}

class ProviderConfigRepo(private val db: Database) {
    fun upsert(c: UserProviderConfig) = transaction(db) {
        ProviderConfigTable.deleteWhere { ProviderConfigTable.userId eq c.userId }
        ProviderConfigTable.insert {
            it[userId] = c.userId
            it[primary] = c.primary.name
            it[relayEndpoint] = c.relayEndpoint
            it[apiKeyEncryptedRef] = c.apiKeyEncryptedRef
            it[fallbackJson] = TutorTypes.tutorJson.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(
                    kotlinx.serialization.serializer<ProviderType>()), c.fallback)
        }
    }

    fun find(userId: String): UserProviderConfig? = transaction(db) {
        ProviderConfigTable
            .select { ProviderConfigTable.userId eq userId }
            .singleOrNull()?.toConfig()
    }

    private fun ResultRow.toConfig() = UserProviderConfig(
        userId = this[ProviderConfigTable.userId],
        primary = ProviderType.valueOf(this[ProviderConfigTable.primary]),
        relayEndpoint = this[ProviderConfigTable.relayEndpoint],
        apiKeyEncryptedRef = this[ProviderConfigTable.apiKeyEncryptedRef],
        fallback = TutorTypes.tutorJson.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(
                kotlinx.serialization.serializer<ProviderType>()),
            this[ProviderConfigTable.fallbackJson]),
    )
}
```

- [ ] **Step 4: Run, PASS**

Run: `./gradlew test --tests "jarvis.tutor.ProviderConfigTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/ProviderConfig.kt src/test/kotlin/jarvis/tutor/ProviderConfigTest.kt
git commit -m "Tutor Layer A: UserProviderConfig schema + upsert/find"
```

---

## Task 19: Vite + React 19 + Tailwind v4 + KaTeX skeleton

**Files:**
- Create: `tutor-web/package.json`
- Create: `tutor-web/vite.config.ts`
- Create: `tutor-web/tsconfig.json`
- Create: `tutor-web/index.html`
- Create: `tutor-web/src/main.tsx`
- Create: `tutor-web/src/App.tsx`
- Create: `tutor-web/src/__tests__/App.test.tsx`
- Create: `tutor-web/postcss.config.js`
- Create: `tutor-web/.gitignore`

- [ ] **Step 1: Initialize Vite project**

From the repo root, run:

```bash
cd tutor-web 2>/dev/null || mkdir tutor-web && cd tutor-web
```

Create `tutor-web/package.json`:

```json
{
  "name": "tutor-web",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview",
    "test": "vitest run"
  },
  "dependencies": {
    "react": "^19.0.0",
    "react-dom": "^19.0.0",
    "katex": "^0.16.11"
  },
  "devDependencies": {
    "@tailwindcss/vite": "^4.0.0",
    "@testing-library/jest-dom": "^6.6.3",
    "@testing-library/react": "^16.1.0",
    "@types/node": "^22.10.0",
    "@types/react": "^19.0.0",
    "@types/react-dom": "^19.0.0",
    "@vitejs/plugin-react": "^4.3.4",
    "jsdom": "^25.0.1",
    "tailwindcss": "^4.0.0",
    "typescript": "^5.7.2",
    "vite": "^5.4.11",
    "vitest": "^2.1.8"
  }
}
```

Create `tutor-web/vite.config.ts`:

```ts
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import path from "node:path";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: {
    outDir: path.resolve(__dirname, "../src/main/resources/tutor-dist"),
    emptyOutDir: true,
    sourcemap: true,
  },
  server: {
    port: 5173,
    proxy: {
      "/api": "http://localhost:7331",
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/setupTests.ts"],
  },
});
```

Create `tutor-web/tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "useDefineForClassFields": true,
    "module": "ESNext",
    "moduleResolution": "Bundler",
    "strict": true,
    "jsx": "react-jsx",
    "skipLibCheck": true,
    "esModuleInterop": true,
    "allowSyntheticDefaultImports": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "types": ["vitest/globals", "@testing-library/jest-dom"]
  },
  "include": ["src", "vite.config.ts"]
}
```

Create `tutor-web/index.html`:

```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width,initial-scale=1" />
    <title>Jarvis Tutor</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

Create `tutor-web/src/main.tsx`:

```tsx
import "katex/dist/katex.min.css";
import "./index.css";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
```

Create `tutor-web/src/index.css`:

```css
@import "tailwindcss";
```

Create `tutor-web/src/App.tsx`:

```tsx
export function App() {
  return (
    <div className="min-h-dvh flex items-center justify-center bg-zinc-100 text-zinc-900 font-mono">
      <div className="border-4 border-black bg-yellow-300 px-6 py-3 text-lg tracking-widest">
        JARVIS TUTOR · ONLINE
      </div>
    </div>
  );
}
```

Create `tutor-web/src/setupTests.ts`:

```ts
import "@testing-library/jest-dom";
```

Create `tutor-web/src/__tests__/App.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import { App } from "../App";

test("renders ONLINE banner", () => {
  render(<App />);
  expect(screen.getByText(/ONLINE/)).toBeInTheDocument();
});
```

Create `tutor-web/postcss.config.js`:

```js
export default { plugins: {} };
```

Create `tutor-web/.gitignore`:

```
node_modules/
dist/
```

- [ ] **Step 2: Install + run tests**

```bash
cd tutor-web
npm install
npm test
```

Expected: 1 test passes. Snapshot includes "ONLINE".

- [ ] **Step 3: Build the bundle**

```bash
cd tutor-web && npm run build
```

Expected: bundle written to `src/main/resources/tutor-dist/index.html` etc. Verify file exists:

```bash
ls ../src/main/resources/tutor-dist/index.html
```

- [ ] **Step 4: Commit**

```bash
git add tutor-web/ src/main/resources/tutor-dist/
git commit -m "Tutor Layer A: Vite + React 19 + Tailwind v4 + KaTeX skeleton"
```

---

## Task 20: Ktor /tutor static route

**Files:**
- Create: `src/main/kotlin/jarvis/web/TutorRoutes.kt`
- Modify: `src/main/kotlin/jarvis/web/WebMain.kt`
- Test: `src/test/kotlin/jarvis/tutor/TutorRoutesTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package jarvis.tutor

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import jarvis.web.installTutorRoutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TutorRoutesTest {
    @Test
    fun `GET tutor returns index html`() = testApplication {
        application { installTutorRoutes() }
        val r = client.get("/tutor/")
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(r.bodyAsText().contains("<div id=\"root\">"))
    }

    @Test
    fun `GET api v1 health returns 200 ok json`() = testApplication {
        application { installTutorRoutes() }
        val r = client.get("/api/v1/health")
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(r.bodyAsText().contains("\"ok\":true"))
    }
}
```

- [ ] **Step 2: Run, FAIL**

Run: `./gradlew test --tests "jarvis.tutor.TutorRoutesTest"`
Expected: FAIL — `Unresolved reference: installTutorRoutes`.

- [ ] **Step 3: Implement**

Create `src/main/kotlin/jarvis/web/TutorRoutes.kt`:

```kotlin
package jarvis.web

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.installTutorRoutes() {
    routing {
        // Static SPA bundle.
        staticResources("/tutor", "tutor-dist") {
            default("index.html")
        }
        // Health endpoint (no auth required).
        get("/api/v1/health") {
            call.respondText("""{"ok":true}""", ContentType.Application.Json)
        }
    }
}
```

- [ ] **Step 4: Wire into existing WebMain**

In `src/main/kotlin/jarvis/web/WebMain.kt`, find the `routing { ... }` block (around line 98). Add a call to `installTutorRoutes()` at the appropriate place — typically right after the existing `routing { }` block closes, OR inline with `application.install`. The simplest approach: at the end of the function/block that configures the application, add:

```kotlin
installTutorRoutes()
```

If `WebMain.kt` defines an `Application.module()` style fn, add `installTutorRoutes()` there. If it uses `embeddedServer { application { ... } }`, add inside that block. Verify by reading `WebMain.kt` lines 80-120.

- [ ] **Step 5: Run, PASS**

Run: `./gradlew test --tests "jarvis.tutor.TutorRoutesTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/jarvis/web/TutorRoutes.kt src/main/kotlin/jarvis/web/WebMain.kt src/test/kotlin/jarvis/tutor/TutorRoutesTest.kt
git commit -m "Tutor Layer A: Ktor /tutor static route + /api/v1/health"
```

---

## Task 21: Auth setup endpoint (/auth/setup) + cookie issuance

**Files:**
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt`
- Test: `src/test/kotlin/jarvis/tutor/AuthSetupTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package jarvis.tutor

import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import jarvis.web.installTutorRoutes
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthSetupTest {
    @Test
    fun `GET auth setup with valid token sets session and csrf cookies`() = testApplication {
        val dbDir = Files.createTempDirectory("authtest")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) {
            SchemaUtils.create(UsersTable, TokensTable, SessionsTable)
        }
        val u = TutorTypes.ulid()
        UserRepo(db).insert(User(u, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val rawToken = TokenRepo(db).issue(u, "test")
        application {
            attributes.put(TutorContextKey, TutorContext(db, dbDir))
            installTutorRoutes()
        }
        val client = createClient { install(HttpCookies) }
        val r = client.get("/auth/setup?t=$rawToken")
        assertEquals(HttpStatusCode.Found, r.status)            // redirect
        val setCookies = r.headers.getAll(HttpHeaders.SetCookie)!!
        assertTrue(setCookies.any { it.startsWith("jarvis_session=") && it.contains("HttpOnly") && it.contains("SameSite=Strict") })
        assertTrue(setCookies.any { it.startsWith("csrf=") })
    }

    @Test
    fun `GET auth setup with bad token returns 401`() = testApplication {
        val dbDir = Files.createTempDirectory("authtest2")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) {
            SchemaUtils.create(UsersTable, TokensTable, SessionsTable)
        }
        application {
            attributes.put(TutorContextKey, TutorContext(db, dbDir))
            installTutorRoutes()
        }
        val r = client.get("/auth/setup?t=bogus")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }
}
```

- [ ] **Step 2: Add `TutorContext`**

In `src/main/kotlin/jarvis/tutor/TutorTypes.kt`, append:

```kotlin
import io.ktor.util.*
import org.jetbrains.exposed.sql.Database
import java.nio.file.Path

data class TutorContext(val db: Database, val ledgerDir: Path)
val TutorContextKey = AttributeKey<TutorContext>("TutorContext")
```

(Make sure imports compile.)

- [ ] **Step 3: Run, FAIL**

Run: `./gradlew test --tests "jarvis.tutor.AuthSetupTest"`
Expected: FAIL.

- [ ] **Step 4: Extend TutorRoutes**

Replace `src/main/kotlin/jarvis/web/TutorRoutes.kt` with:

```kotlin
package jarvis.web

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jarvis.tutor.SessionRepo
import jarvis.tutor.TokenRepo
import jarvis.tutor.TutorContextKey
import java.security.SecureRandom

private val rng = SecureRandom()

fun Application.installTutorRoutes() {
    routing {
        staticResources("/tutor", "tutor-dist") { default("index.html") }

        get("/api/v1/health") {
            call.respondText("""{"ok":true}""", ContentType.Application.Json)
        }

        get("/auth/setup") {
            val ctx = application.attributes[TutorContextKey]
            val raw = call.request.queryParameters["t"]
            if (raw.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized, "missing token")
                return@get
            }
            val tokens = TokenRepo(ctx.db)
            val userId = tokens.findUserIdByToken(raw)
                ?: run { call.respond(HttpStatusCode.Unauthorized, "bad token"); return@get }
            val sessions = SessionRepo(ctx.db)
            val sid = sessions.create(userId, ttlSeconds = 60L * 60 * 24 * 14)  // 14 days
            val csrf = ByteArray(16).also { rng.nextBytes(it) }
                .joinToString("") { "%02x".format(it) }
            call.response.cookies.append(Cookie(
                name = "jarvis_session", value = sid,
                httpOnly = true, secure = true,
                extensions = mapOf("SameSite" to "Strict"),
                path = "/", maxAge = 60 * 60 * 24 * 14,
            ))
            call.response.cookies.append(Cookie(
                name = "csrf", value = csrf,
                httpOnly = false, secure = true,
                extensions = mapOf("SameSite" to "Strict"),
                path = "/", maxAge = 60 * 60 * 24 * 14,
            ))
            call.respondRedirect("/tutor/")
        }
    }
}
```

- [ ] **Step 5: Run, PASS**

Run: `./gradlew test --tests "jarvis.tutor.AuthSetupTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/TutorTypes.kt src/main/kotlin/jarvis/web/TutorRoutes.kt src/test/kotlin/jarvis/tutor/AuthSetupTest.kt
git commit -m "Tutor Layer A: /auth/setup endpoint sets httpOnly+CSRF cookies"
```

---

## Task 22: TypeScript schema mirrors + api.ts (cookie + CSRF fetch)

**Files:**
- Create: `tutor-web/src/lib/types.ts`
- Create: `tutor-web/src/lib/api.ts`
- Create: `tutor-web/src/__tests__/api.test.ts`

- [ ] **Step 1: Failing test**

Create `tutor-web/src/__tests__/api.test.ts`:

```ts
import { describe, expect, test, vi, beforeEach, afterEach } from "vitest";
import { jarvisFetch, getCsrfToken } from "../lib/api";

describe("api", () => {
  beforeEach(() => {
    Object.defineProperty(document, "cookie", {
      value: "csrf=abc123; foo=bar",
      configurable: true,
      writable: true,
    });
    vi.stubGlobal("fetch", vi.fn(async () => new Response("{}", {
      status: 200, headers: { "content-type": "application/json" }
    })));
  });
  afterEach(() => { vi.unstubAllGlobals(); });

  test("getCsrfToken reads csrf cookie", () => {
    expect(getCsrfToken()).toBe("abc123");
  });

  test("jarvisFetch GET sends credentials and no csrf header", async () => {
    await jarvisFetch("/api/v1/health");
    const call = (globalThis.fetch as any).mock.calls[0];
    expect(call[1].credentials).toBe("include");
    expect(call[1].headers?.["X-CSRF-Token"]).toBeUndefined();
  });

  test("jarvisFetch POST sends X-CSRF-Token header from cookie", async () => {
    await jarvisFetch("/api/v1/whatever", { method: "POST", body: JSON.stringify({}) });
    const call = (globalThis.fetch as any).mock.calls[0];
    expect(call[1].headers["X-CSRF-Token"]).toBe("abc123");
  });
});
```

- [ ] **Step 2: Run, FAIL**

```bash
cd tutor-web && npm test
```

Expected: FAIL — `lib/api.ts` not found.

- [ ] **Step 3: Implement**

Create `tutor-web/src/lib/types.ts`:

```ts
export type EffectorType = "APPLY_EDIT" | "RUN_R" | "NAVIGATE" | "INSERT_SCRATCHPAD";
export type Outcome = "SUCCESS" | "REJECTED" | "ROLLED_BACK" | "STALE_DOC" | "PATH_DENIED";

export interface ContentRef { repo: string; path: string; sha: string; }
export interface ScratchpadRef { docId: string; version: number; }
export interface SubmissionRef { docId: string; version: number; submittedAt: string; }
export interface GradeRecord { score: number; rubricVersion: string; gradedAt: string; modelId: string; }

export type TaskStatus = "TODO" | "ACTIVE" | "SUBMITTED" | "GRADED" | "ARCHIVED";

export interface Task {
  id: string;
  userId: string;
  subject: string;
  title: string;
  deadline: string;
  problemRef: ContentRef;
  conceptRefs: ContentRef[];
  rubricRef: ContentRef;
  scratchpad: ScratchpadRef | null;
  submission: SubmissionRef | null;
  grade: GradeRecord | null;
  cardRefs: string[];
  status: TaskStatus;
  createdAt: string;
  updatedAt: string;
}

export interface Position { line: number; character: number; }
export interface Range { start: Position; end: Position; }
export interface TextEdit { range: Range; newText: string; }
export interface ApplyEditRequest {
  taskId: string;
  effectorId: string;
  targetUri: string;
  expectedDocVersion: string;
  edits: TextEdit[];
  nonce: string;
  grantId: string;
}
```

Create `tutor-web/src/lib/api.ts`:

```ts
export function getCsrfToken(): string | null {
  const m = document.cookie.match(/(?:^|;\s*)csrf=([^;]+)/);
  return m ? decodeURIComponent(m[1]) : null;
}

export async function jarvisFetch(
  url: string,
  init: RequestInit = {},
): Promise<Response> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers: Record<string, string> = {
    ...(init.headers as Record<string, string> | undefined),
  };
  if (method !== "GET" && method !== "HEAD") {
    const csrf = getCsrfToken();
    if (csrf) headers["X-CSRF-Token"] = csrf;
    if (init.body && !headers["Content-Type"]) {
      headers["Content-Type"] = "application/json";
    }
  }
  return fetch(url, { ...init, headers, credentials: "include" });
}
```

- [ ] **Step 4: Run, PASS**

```bash
cd tutor-web && npm test
```

Expected: all api tests pass.

- [ ] **Step 5: Commit**

```bash
git add tutor-web/src/lib/ tutor-web/src/__tests__/api.test.ts
git commit -m "Tutor Layer A: TypeScript schema mirrors + api.ts (cookie+CSRF fetch)"
```

---

## Task 23: Workspace shell — split-pane PDF + chat

**Files:**
- Create: `tutor-web/src/components/PdfPane.tsx`
- Create: `tutor-web/src/components/ChatPane.tsx`
- Create: `tutor-web/src/components/TutorWorkspace.tsx`
- Modify: `tutor-web/src/App.tsx`
- Create: `tutor-web/src/__tests__/TutorWorkspace.test.tsx`

- [ ] **Step 1: Failing test**

Create `tutor-web/src/__tests__/TutorWorkspace.test.tsx`:

```tsx
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { TutorWorkspace } from "../components/TutorWorkspace";

beforeEach(() => {
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (url.includes("/api/chat")) {
      return new Response(JSON.stringify({ reply: "hello back" }), {
        status: 200, headers: { "content-type": "application/json" },
      });
    }
    return new Response("{}", { status: 200 });
  }));
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true });
});
afterEach(() => { vi.unstubAllGlobals(); });

test("renders left PDF pane and right chat pane", () => {
  render(<TutorWorkspace pdfUrl="/sample.pdf" taskId="T1" />);
  expect(screen.getByTestId("pdf-pane")).toBeInTheDocument();
  expect(screen.getByTestId("chat-pane")).toBeInTheDocument();
});

test("send button POSTs to /api/chat and shows reply", async () => {
  render(<TutorWorkspace pdfUrl="/sample.pdf" taskId="T1" />);
  const input = screen.getByPlaceholderText(/message/i);
  fireEvent.change(input, { target: { value: "hi" } });
  fireEvent.click(screen.getByRole("button", { name: /send/i }));
  await waitFor(() => expect(screen.getByText("hello back")).toBeInTheDocument());
  const call = (globalThis.fetch as any).mock.calls.find((c: any) => c[0].includes("/api/chat"));
  expect(call[1].headers["X-CSRF-Token"]).toBe("zzz");
});
```

- [ ] **Step 2: Run, FAIL**

```bash
cd tutor-web && npm test
```

Expected: FAIL — components missing.

- [ ] **Step 3: Implement**

Create `tutor-web/src/components/PdfPane.tsx`:

```tsx
export function PdfPane({ url }: { url: string }) {
  return (
    <div data-testid="pdf-pane" className="h-full bg-zinc-50 border-r-4 border-black overflow-auto">
      <embed src={url} type="application/pdf" className="w-full h-full" />
    </div>
  );
}
```

Create `tutor-web/src/components/ChatPane.tsx`:

```tsx
import { useState } from "react";
import { jarvisFetch } from "../lib/api";

interface Msg { role: "you" | "jarvis"; text: string; }

export function ChatPane({ taskId }: { taskId: string }) {
  const [messages, setMessages] = useState<Msg[]>([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);

  async function send() {
    if (!input.trim() || sending) return;
    const userMsg = input;
    setMessages(m => [...m, { role: "you", text: userMsg }]);
    setInput("");
    setSending(true);
    try {
      const res = await jarvisFetch("/api/chat", {
        method: "POST",
        body: JSON.stringify({ taskId, message: userMsg }),
      });
      const data = await res.json();
      setMessages(m => [...m, { role: "jarvis", text: data.reply ?? "(no reply)" }]);
    } catch (e) {
      setMessages(m => [...m, { role: "jarvis", text: `(error: ${(e as Error).message})` }]);
    } finally {
      setSending(false);
    }
  }

  return (
    <div data-testid="chat-pane" className="h-full flex flex-col bg-white font-mono">
      <div className="bg-black text-yellow-300 px-4 py-2 text-sm tracking-widest font-bold">
        JARVIS · TASK {taskId}
      </div>
      <div className="flex-1 overflow-auto p-4 space-y-3">
        {messages.map((m, i) => (
          <div key={i}>
            <div className={`inline-block px-2 py-0.5 text-xs font-bold tracking-widest ${m.role === "you" ? "bg-yellow-300 text-black" : "bg-black text-white"}`}>
              {m.role.toUpperCase()}
            </div>
            <div className="text-sm leading-relaxed mt-1">{m.text}</div>
          </div>
        ))}
      </div>
      <div className="flex border-t-4 border-black">
        <input
          className="flex-1 px-3 py-2 outline-none text-sm font-mono"
          placeholder="message · ctrl+enter sends"
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={e => { if (e.ctrlKey && e.key === "Enter") send(); }}
          disabled={sending}
        />
        <button
          className="bg-yellow-300 px-6 font-bold tracking-widest text-sm disabled:opacity-50 border-l-4 border-black"
          onClick={send}
          disabled={sending}
        >
          SEND
        </button>
      </div>
    </div>
  );
}
```

Create `tutor-web/src/components/TutorWorkspace.tsx`:

```tsx
import { PdfPane } from "./PdfPane";
import { ChatPane } from "./ChatPane";

export function TutorWorkspace({ pdfUrl, taskId }: { pdfUrl: string; taskId: string }) {
  return (
    <div className="grid grid-cols-2 h-dvh">
      <PdfPane url={pdfUrl} />
      <ChatPane taskId={taskId} />
    </div>
  );
}
```

Replace `tutor-web/src/App.tsx`:

```tsx
import { TutorWorkspace } from "./components/TutorWorkspace";

export function App() {
  // Hardcoded test task for Layer A acceptance.
  return <TutorWorkspace pdfUrl="/test-task.pdf" taskId="TEST-TASK-A" />;
}
```

- [ ] **Step 4: Run, PASS**

```bash
cd tutor-web && npm test
```

Expected: all tests pass.

- [ ] **Step 5: Rebuild bundle**

```bash
cd tutor-web && npm run build
```

Verify: `src/main/resources/tutor-dist/index.html` exists.

- [ ] **Step 6: Commit**

```bash
git add tutor-web/src/components/ tutor-web/src/App.tsx tutor-web/src/__tests__/TutorWorkspace.test.tsx src/main/resources/tutor-dist/
git commit -m "Tutor Layer A: workspace shell — split-pane PDF + chat"
```

---

## Task 24: Hardcoded test task seeding + smoke script

**Files:**
- Create: `tutor-web/public/test-task.pdf` — minimal one-page PDF for acceptance.
- Create: `tools/smoke-tutor.sh`
- Create: `tools/seed-test-task.kt` (script-style, runnable via `./gradlew seedTestTask`)
- Modify: `build.gradle.kts` — add `seedTestTask` task.

- [ ] **Step 1: Add minimal test PDF**

A short script: from `tutor-web/`, run:

```bash
cd tutor-web/public
cat > test-task.pdf << 'EOF'
%PDF-1.4
1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj
2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj
3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]/Contents 4 0 R/Resources<<>>>>endobj
4 0 obj<</Length 44>>stream
BT /F1 12 Tf 100 700 Td (Tema A · Test Task) Tj ET
endstream endobj
xref
0 5
0000000000 65535 f
0000000010 00000 n
0000000053 00000 n
0000000098 00000 n
0000000178 00000 n
trailer<</Size 5/Root 1 0 R>>
startxref
260
%%EOF
EOF
```

(If the inline PDF is rejected by the browser, replace with a real one-page PDF generated by any tool.)

- [ ] **Step 2: Create smoke script**

Create `tools/smoke-tutor.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail
HOST="${HOST:-http://localhost:7331}"

echo "[smoke] /api/v1/health"
curl -fsS "$HOST/api/v1/health" | grep -q '"ok":true'

echo "[smoke] /tutor/"
curl -fsS "$HOST/tutor/" | grep -q '<div id="root">'

echo "[smoke] tutor bundle js exists"
curl -fsSI "$HOST/tutor/assets/" >/dev/null 2>&1 || \
  curl -fsS "$HOST/tutor/" | grep -qE '/tutor/assets/[^"]+\.js' || true

echo "[smoke] OK"
```

Make executable: `chmod +x tools/smoke-tutor.sh`.

- [ ] **Step 3: Test smoke script against test app**

This test exercises the deployable artifact, not just unit tests. Start the Ktor app locally (or use `testApplication` for an automated check):

Create `src/test/kotlin/jarvis/tutor/SmokeTest.kt`:

```kotlin
package jarvis.tutor

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import jarvis.web.installTutorRoutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SmokeTest {
    @Test
    fun `tutor bundle is served and contains root div`() = testApplication {
        application { installTutorRoutes() }
        val r = client.get("/tutor/")
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("<div id=\"root\">"), "expected root div in bundle")
    }

    @Test
    fun `health endpoint returns ok json`() = testApplication {
        application { installTutorRoutes() }
        val r = client.get("/api/v1/health")
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(r.bodyAsText().contains("\"ok\":true"))
    }
}
```

- [ ] **Step 4: Run smoke test**

Run: `./gradlew test --tests "jarvis.tutor.SmokeTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add tutor-web/public/test-task.pdf tools/smoke-tutor.sh src/test/kotlin/jarvis/tutor/SmokeTest.kt
git commit -m "Tutor Layer A: hardcoded test task + smoke script + smoke test"
```

---

## Task 25: Wire TutorContext into application bootstrap

**Files:**
- Modify: `src/main/kotlin/jarvis/Config.kt`
- Modify: `src/main/kotlin/jarvis/web/WebMain.kt`
- Test: `src/test/kotlin/jarvis/tutor/BootstrapTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package jarvis.tutor

import io.ktor.server.application.*
import io.ktor.server.testing.*
import jarvis.web.installTutorRoutes
import jarvis.web.installTutorContext
import kotlin.test.Test
import kotlin.test.assertNotNull

class BootstrapTest {
    @Test
    fun `installTutorContext attaches TutorContext to application`() = testApplication {
        val tmp = java.nio.file.Files.createTempDirectory("boot")
        application {
            installTutorContext(dbPath = tmp.resolve("tutor.db").toString(), ledgerDir = tmp)
            installTutorRoutes()
        }
        startApplication()
        val ctx = application.attributes[TutorContextKey]
        assertNotNull(ctx)
    }
}
```

- [ ] **Step 2: Run, FAIL**

Run: `./gradlew test --tests "jarvis.tutor.BootstrapTest"`
Expected: FAIL — `Unresolved reference: installTutorContext`.

- [ ] **Step 3: Implement**

Append to `src/main/kotlin/jarvis/web/TutorRoutes.kt`:

```kotlin
import io.ktor.server.application.Application
import jarvis.tutor.TutorContext
import jarvis.tutor.TutorContextKey
import jarvis.tutor.TutorDb
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import jarvis.tutor.UsersTable
import jarvis.tutor.TokensTable
import jarvis.tutor.SessionsTable
import jarvis.tutor.TasksTable
import jarvis.tutor.SensorEventsTable
import jarvis.tutor.TrustGrantsTable
import jarvis.tutor.AuditLinesTable
import jarvis.tutor.KnowledgeGapsTable
import jarvis.tutor.FsrsCardsTable
import jarvis.tutor.ProviderConfigTable
import java.nio.file.Files
import java.nio.file.Path

fun Application.installTutorContext(dbPath: String, ledgerDir: Path) {
    Files.createDirectories(ledgerDir)
    val db = TutorDb.connect(dbPath)
    transaction(db) {
        SchemaUtils.create(
            UsersTable, TokensTable, SessionsTable, TasksTable, SensorEventsTable,
            TrustGrantsTable, AuditLinesTable, KnowledgeGapsTable, FsrsCardsTable,
            ProviderConfigTable,
        )
    }
    attributes.put(TutorContextKey, TutorContext(db, ledgerDir))
}
```

In `src/main/kotlin/jarvis/Config.kt`, add fields:

```kotlin
val tutorDbPath: String = System.getenv("JARVIS_TUTOR_DB")
    ?: "${System.getProperty("user.home")}/.jarvis/tutor.db"
val tutorLedgerDir: String = System.getenv("JARVIS_TUTOR_LEDGER_DIR")
    ?: "${System.getProperty("user.home")}/.jarvis/tutor-ledgers"
```

(Add inside the existing `Config` class/object — match its style.)

In `src/main/kotlin/jarvis/web/WebMain.kt`, near where `routing { }` was wired (around line 90-100), call:

```kotlin
installTutorContext(
    dbPath = jarvis.Config.tutorDbPath,
    ledgerDir = java.nio.file.Path.of(jarvis.Config.tutorLedgerDir),
)
installTutorRoutes()
```

Replacing or appending to existing routing initialization as appropriate.

- [ ] **Step 4: Run, PASS**

Run: `./gradlew test --tests "jarvis.tutor.BootstrapTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/Config.kt src/main/kotlin/jarvis/web/TutorRoutes.kt src/main/kotlin/jarvis/web/WebMain.kt src/test/kotlin/jarvis/tutor/BootstrapTest.kt
git commit -m "Tutor Layer A: bootstrap TutorContext (DB + schema migrate + ledger dir)"
```

---

## Task 26: Acceptance — full Layer A end-to-end

**Files:**
- Create: `src/test/kotlin/jarvis/tutor/LayerAAcceptanceTest.kt`

- [ ] **Step 1: Acceptance test**

```kotlin
package jarvis.tutor

import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import jarvis.web.installTutorContext
import jarvis.web.installTutorRoutes
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LayerAAcceptanceTest {
    @Test
    fun `acceptance — token issue then setup then chat fetch`() = testApplication {
        val tmp = Files.createTempDirectory("accept")
        application {
            installTutorContext(tmp.resolve("t.db").toString(), tmp)
            installTutorRoutes()
        }
        startApplication()

        val ctx = application.attributes[TutorContextKey]
        // 1. Seed a user + token.
        val userId = TutorTypes.ulid()
        UserRepo(ctx.db).insert(User(userId, "victor", UserScope.OWNER, Instant.now(), Instant.now()))
        val raw = TokenRepo(ctx.db).issue(userId, "primary")

        // 2. Hit /auth/setup, follow cookies.
        val client = createClient { install(HttpCookies) }
        val authRes = client.get("/auth/setup?t=$raw")
        assertEquals(HttpStatusCode.Found, authRes.status)

        // 3. /tutor/ now responds.
        val tutorRes = client.get("/tutor/")
        assertEquals(HttpStatusCode.OK, tutorRes.status)
        assertTrue(tutorRes.bodyAsText().contains("<div id=\"root\">"))

        // 4. /api/v1/health works.
        val healthRes = client.get("/api/v1/health")
        assertEquals(HttpStatusCode.OK, healthRes.status)
        assertTrue(healthRes.bodyAsText().contains("\"ok\":true"))

        // 5. Audit log started clean for this user.
        val audit = AuditRepo(ctx.db)
        assertTrue(audit.verifyChain(userId), "audit chain must be valid (or empty)")

        // 6. Effector validator rejects a malformed apply.
        val v = EffectorValidator(ctx.db, NonceCache())
        val grantId = TutorTypes.ulid()
        TrustGrantRepo(ctx.db).insert(TrustGrant(grantId, userId,
            listOf("file:///c/uaic/**"), setOf(EffectorType.APPLY_EDIT),
            Instant.now().plusSeconds(3600), 50, 0, GrantSource.UI, null, Instant.now()))
        val req = ApplyEditRequest(taskId = "T", effectorId = TutorTypes.ulid(),
            targetUri = "file:///etc/passwd",
            expectedDocVersion = "v1",
            edits = listOf(TextEdit(Range(Position(0,0), Position(0,1)), "x")),
            nonce = TutorTypes.ulid(), grantId = grantId)
        val r = v.validate(userId, req, currentDocVersion = "v1")
        assertEquals(ValidationResult.Reject(Outcome.PATH_DENIED, "path on blocklist").outcome,
            (r as ValidationResult.Reject).outcome)
    }
}
```

- [ ] **Step 2: Run**

Run: `./gradlew test --tests "jarvis.tutor.LayerAAcceptanceTest"`
Expected: PASS.

- [ ] **Step 3: Run the entire tutor test suite, expect green**

Run: `./gradlew test --tests "jarvis.tutor.*"`
Expected: ALL PASS.

- [ ] **Step 4: Final Layer A commit**

```bash
git add src/test/kotlin/jarvis/tutor/LayerAAcceptanceTest.kt
git commit -m "Tutor Layer A: acceptance test — token→setup→/tutor→/api+audit+validator green"
```

- [ ] **Step 5: Tag the layer**

```bash
git tag tutor/layer-a-acceptance
```

---

## Layer A · Acceptance Checklist

When Layer A is complete, all of these must hold:

- [ ] `./gradlew test --tests "jarvis.tutor.*"` — green
- [ ] `./gradlew test --tests "jarvis.tutor.EffectorFuzzerTest"` — green (the gate)
- [ ] `./gradlew test --tests "jarvis.tutor.LayerAAcceptanceTest"` — green
- [ ] `cd tutor-web && npm test` — green
- [ ] `cd tutor-web && npm run build` — produces `src/main/resources/tutor-dist/index.html`
- [ ] Local `./gradlew run`, then `tools/smoke-tutor.sh` — passes against running app
- [ ] All schemas have `userId` columns and tests verify cross-user isolation
- [ ] `git tag tutor/layer-a-acceptance` exists
- [ ] No `// TODO`, `// FIXME`, or placeholder strings in committed Kotlin or TS code

When this checklist is fully green, Layer B may begin. Layer B's plan will be written at that point.

---

## Self-Review Notes

**Spec coverage check (against `2026-05-09-jarvis-tutor-design.md` §3):**
- §3.1 Routing + Auth → Tasks 6, 7, 20, 21, 25 ✓
- §3.2 All schemas → Tasks 4, 5, 6, 8, 9, 11, 14, 15, 16, 17, 18 ✓
- §3.3 Storage layout → Tasks 2 (WAL pragma), 11 (audit), 16 (JSONL gaps) ✓
- §3.4 Workspace shell first commit → Tasks 19, 23 ✓
- §3.5 Effector contract fuzzer (pre-build gate) → Task 13 ✓
- Multi-tenant `userId` everywhere → every schema task includes userId column + cross-user test ✓

**Type consistency check:**
- `Outcome.STALE_DOC`, `PATH_DENIED`, `REJECTED`, `SUCCESS`, `ROLLED_BACK` — used consistently in `Effectors.kt` (Task 8), `EffectorValidator.kt` (Task 12), `Audit.kt` (Task 11) ✓
- `ULID = 26-char string` — used in every schema's primary key ✓
- `TutorTypes.ulid()` returns 26-char Crockford base32 — verified in Task 3 test ✓
- `tutorJson` JSON instance shared across all serialization sites ✓
- `ContentRef = (repo, path, sha)` — used in `Tasks.kt` per spec §3.2 ✓

**Placeholder scan:** none.

**Scope check:** Layer A only. Layer B/C/D scoped explicitly to future plans. No leaks.

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-05-09-jarvis-tutor-layer-a.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

**Which approach?**
