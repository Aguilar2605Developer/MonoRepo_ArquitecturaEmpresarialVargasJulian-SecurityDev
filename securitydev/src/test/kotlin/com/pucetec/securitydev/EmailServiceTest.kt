package com.pucetec.securitydev

import com.pucetec.securitydev.service.EmailService
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.test.util.ReflectionTestUtils
import java.util.Properties

/**
 * Tests unitarios para EmailService.
 *
 * IMPORTANTE: MimeMessageHelper necesita un MimeMessage "real" (no un mock),
 * porque internamente llama a varios métodos concretos de la clase final
 * jakarta.mail.internet.MimeMessage. Por eso en vez de mockear MimeMessage,
 * se crea una instancia real con una Session en blanco y se le indica al mock
 * de JavaMailSender que la devuelva en createMimeMessage().
 *
 * `frontendUrl` se inyecta con @Value en runtime de Spring, así que en el test
 * se setea manualmente con ReflectionTestUtils.
 */
@ExtendWith(MockitoExtension::class)
class EmailServiceTest {

    @Mock
    lateinit var mailSender: JavaMailSender

    private lateinit var emailService: EmailService

    private val frontendUrl = "http://localhost:8100"

    @BeforeEach
    fun setUp() {
        emailService = EmailService(mailSender)
        ReflectionTestUtils.setField(emailService, "frontendUrl", frontendUrl)

        val session = Session.getDefaultInstance(Properties())
        val realMimeMessage = MimeMessage(session)
        whenever(mailSender.createMimeMessage()).doReturn(realMimeMessage)
    }

    @Test
    fun `sendLocationShareEmail deberia enviar el correo al destinatario correcto`() {
        emailService.sendLocationShareEmail(
            toEmail = "destinatario@example.com",
            username = "Juan Perez",
            shareId = "abc123"
        )

        val captor = argumentCaptor<MimeMessage>()
        verify(mailSender).send(captor.capture())

        val sentMessage = captor.firstValue
        val recipients = sentMessage.getRecipients(Message.RecipientType.TO)

        assertEquals(1, recipients.size)
        assertEquals("destinatario@example.com", recipients[0].toString())
    }

    @Test
    fun `sendLocationShareEmail deberia incluir el nombre de usuario en el asunto`() {
        emailService.sendLocationShareEmail(
            toEmail = "destinatario@example.com",
            username = "Maria Lopez",
            shareId = "xyz789"
        )

        val captor = argumentCaptor<MimeMessage>()
        verify(mailSender).send(captor.capture())

        val subject = captor.firstValue.subject
        assertNotNull(subject)
        assertTrue(subject!!.contains("Maria Lopez"))
    }

    @Test
    fun `sendLocationShareEmail deberia construir el link con frontendUrl y shareId`() {
        emailService.sendLocationShareEmail(
            toEmail = "destinatario@example.com",
            username = "Carlos Ruiz",
            shareId = "share-999"
        )

        val captor = argumentCaptor<MimeMessage>()
        verify(mailSender).send(captor.capture())

        val rawContent = extractRawContent(captor.firstValue)
        val expectedLink = "$frontendUrl/share/share-999"

        assertTrue(rawContent.contains(expectedLink)) {
            "El contenido del correo no incluye el link esperado: $expectedLink"
        }
        assertTrue(rawContent.contains("Carlos Ruiz"))
    }

    @Test
    fun `sendLocationShareEmail deberia llamar a mailSender send exactamente una vez`() {
        emailService.sendLocationShareEmail(
            toEmail = "destinatario@example.com",
            username = "Ana Torres",
            shareId = "share-001"
        )

        verify(mailSender, org.mockito.kotlin.times(1)).send(org.mockito.kotlin.any<MimeMessage>())
    }

    /**
     * Lee el mensaje MIME completo tal cual se enviaría por SMTP (headers +
     * cuerpo), en vez de depender de message.getContent(). Esto evita fallos
     * cuando el DataContentHandler de "text/html" no está registrado fuera
     * de un contexto Spring completo, que puede devolver un InputStream en
     * lugar de un String decodificado.
     */
    private fun extractRawContent(message: MimeMessage): String {
        val out = java.io.ByteArrayOutputStream()
        message.writeTo(out)
        return out.toString(Charsets.UTF_8.name())
    }
}