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
