package jarvis

import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.util.Properties

/**
 * R5 (deep-research recommendation #5 / Khoj automation parity) — minimal
 * SMTP relay so [runReflect] can deliver a daily summary email in addition
 * to the wiki write.
 *
 * Default-OFF: [sendIfEnabled] silently returns false unless
 *   JARVIS_DAILY_EMAIL=true AND SMTP_USER + SMTP_PASS are set.
 *
 * Defaults to Gmail SMTP (smtp.gmail.com:587 STARTTLS); override via
 * SMTP_HOST / SMTP_PORT for any other provider.
 */
object Smtp {

    /** Returns true if the email actually went out, false if disabled or
     *  creds missing. Throws on SMTP error so the caller can log. */
    fun sendIfEnabled(subject: String, body: String): Boolean {
        if (System.getenv("JARVIS_DAILY_EMAIL")?.lowercase() !in setOf("1", "true", "yes")) {
            return false
        }
        val user = System.getenv("SMTP_USER")?.trim().orEmpty()
        val pass = System.getenv("SMTP_PASS")?.trim().orEmpty()
        if (user.isEmpty() || pass.isEmpty()) return false
        val host = System.getenv("SMTP_HOST")?.trim()?.takeIf { it.isNotEmpty() }
            ?: "smtp.gmail.com"
        val port = System.getenv("SMTP_PORT")?.trim()?.toIntOrNull() ?: 587
        val to = System.getenv("SMTP_TO")?.trim()?.takeIf { it.isNotEmpty() } ?: user

        val props = Properties().apply {
            put("mail.smtp.host", host)
            put("mail.smtp.port", port.toString())
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.starttls.required", "true")
        }
        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(user, pass)
        })
        val msg = MimeMessage(session).apply {
            setFrom(InternetAddress(user))
            setRecipient(Message.RecipientType.TO, InternetAddress(to))
            setSubject(subject)
            setText(body, Charsets.UTF_8.name())
        }
        Transport.send(msg)
        return true
    }
}
