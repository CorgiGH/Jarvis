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
    fun upsertByEmail(email: String, lang: String): User = transaction(db) {
        UsersTable.selectAll().where { UsersTable.email eq email }
            .singleOrNull()?.toUser()
            ?: run {
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
                UsersTable.insert { row ->
                    row[id] = u.id
                    row[name] = u.name
                    row[scope] = u.scope.name
                    row[createdAt] = u.createdAt
                    row[lastSeenAt] = u.lastSeenAt
                    row[UsersTable.email] = u.email
                    row[UsersTable.lang] = u.lang
                }
                u
            }
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
