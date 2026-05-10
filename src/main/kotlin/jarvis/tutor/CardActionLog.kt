package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * Phase 3 audit table for KnowledgeGap + SuggestedEdit card status
 * updates. Phase 4 splits gap state into its own tutor_gaps table; this
 * stays as the action log even after that lands. Append-only.
 */
object CardActionLogTable : Table("tutor_card_action_log") {
    val id = varchar("id", 26)              // ulid
    val userId = varchar("user_id", 26).references(UsersTable.id)
    val cardKind = varchar("card_kind", 16) // "GAP" | "EDIT"
    val cardId = varchar("card_id", 64)     // client-generated id
    val status = varchar("status", 32)      // verbatim from request
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

class CardActionLogRepo(private val db: Database) {
    fun insert(userId: String, cardKind: String, cardId: String, status: String): String {
        val id = TutorTypes.ulid()
        transaction(db) {
            CardActionLogTable.insert {
                it[CardActionLogTable.id] = id
                it[CardActionLogTable.userId] = userId
                it[CardActionLogTable.cardKind] = cardKind
                it[CardActionLogTable.cardId] = cardId
                it[CardActionLogTable.status] = status
                it[CardActionLogTable.createdAt] = Instant.now()
            }
        }
        return id
    }
}
