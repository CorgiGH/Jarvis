package jarvis

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SmtpTest {

    @Test
    fun disabledByDefault() {
        // Test environment doesn't set JARVIS_DAILY_EMAIL; should silently no-op.
        val sent = Smtp.sendIfEnabled("test subject", "test body")
        assertEquals(false, sent, "default-disabled returns false (no SMTP)")
    }

    // Note: actual SMTP send is integration-tested manually after deploy with
    // real Gmail app-password creds in /opt/jarvis/.env. Unit-testing
    // jakarta.mail Transport.send requires either a fake server (Greenmail
    // dep) or a system-property override — skipped at v1 scope per Pragmatist.
}
