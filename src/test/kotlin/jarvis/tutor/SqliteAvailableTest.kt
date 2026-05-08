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
}
