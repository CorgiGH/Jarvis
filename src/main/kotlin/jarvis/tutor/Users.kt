package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.select
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
        UsersTable.selectAll().where { UsersTable.id eq id }.singleOrNull()?.toUser()
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
