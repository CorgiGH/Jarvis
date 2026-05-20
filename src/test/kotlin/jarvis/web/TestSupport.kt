package jarvis.web

import jarvis.tutor.LoggingMailer
import jarvis.tutor.MagicLinkMailer
import jarvis.tutor.MailResult
import jarvis.tutor.TutorContext
import org.jetbrains.exposed.sql.Database
import java.nio.file.Path

class FakeMailer : MagicLinkMailer {
    val sent = mutableListOf<Pair<String, String>>()  // (email, link)
    override suspend fun send(toEmail: String, link: String, lang: String): MailResult {
        sent += toEmail to link
        return MailResult.SENT
    }
}

/** Builds a TutorContext with the given mailer for use in route tests. */
fun testTutorContext(
    db: Database,
    dbDir: Path,
    mailer: MagicLinkMailer = LoggingMailer(),
): TutorContext = TutorContext(db = db, ledgerDir = dbDir, mailer = mailer)
