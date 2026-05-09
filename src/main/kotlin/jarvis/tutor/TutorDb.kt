package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import java.sql.DriverManager

object TutorDb {
    fun connect(sqlitePath: String): Database {
        val url = "jdbc:sqlite:$sqlitePath"

        // Set pragmas using direct JDBC connection before Exposed connects
        DriverManager.getConnection(url).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("PRAGMA journal_mode=WAL")
                stmt.execute("PRAGMA synchronous=NORMAL")
                stmt.execute("PRAGMA foreign_keys=ON")
            }
        }

        // Now connect via Exposed
        return Database.connect(url, driver = "org.sqlite.JDBC")
    }
}
