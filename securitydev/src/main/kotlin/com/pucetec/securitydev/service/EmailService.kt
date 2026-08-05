package com.pucetec.securitydev.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service

// Envío de correos vía SMTP (Gmail, configurado en application.yaml).
@Service
class EmailService(private val mailSender: JavaMailSender) {

    @Value("\${app.frontend.url:http://localhost:8100}")
    private lateinit var frontendUrl: String

    // Único método: notifica por correo que alguien está compartiendo su
    // ubicación, con un link directo al frontend (/share/{shareId}).
    fun sendLocationShareEmail(toEmail: String, username: String, shareId: String) {
        val shareLink = "$frontendUrl/share/$shareId"
        val message = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, true, "UTF-8")

        helper.setTo(toEmail)
        helper.setSubject("📍 $username está compartiendo su ubicación")
        helper.setText("""
            <!DOCTYPE html>
            <html>
            <body style="font-family: sans-serif; color: #333;">
                <h2>¡Hola!</h2>
                <p><b>$username</b> está compartiendo su ubicación en tiempo real contigo.</p>
                <a href="$shareLink" style="background-color: #007bff; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px;">
                    Ver ubicación en el mapa
                </a>
            </body>
            </html>
        """.trimIndent(), true)

        mailSender.send(message)
    }
}