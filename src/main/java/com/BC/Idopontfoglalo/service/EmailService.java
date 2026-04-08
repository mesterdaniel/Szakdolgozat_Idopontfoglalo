package com.BC.Idopontfoglalo.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.email.from}")
    private String fromEmail;

    public void sendSimpleMessage(String to, String subject, String text) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            
            // HTML tartalom összeállítása egy alap sablonnal
            String htmlContent = buildHtmlTemplate(subject, text);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            System.out.println("SIKERES EMAIL KULDES IDE: " + to);
        } catch (Exception e) {
            System.err.println("HIBA AZ EMAIL KULDESEKOR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String buildHtmlTemplate(String title, String content) {
        // Alapértelmezett, elegáns HTML sablon
        return "<!DOCTYPE html>" +
                "<html lang='hu'>" +
                "<head>" +
                "    <meta charset='UTF-8'>" +
                "    <meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "    <style>" +
                "        body {" +
                "            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;" +
                "            background-color: #f5f7fa;" +
                "            color: #333;" +
                "            line-height: 1.6;" +
                "            margin: 0;" +
                "            padding: 0;" +
                "        }" +
                "        .email-container {" +
                "            max-width: 600px;" +
                "            margin: 0 auto;" +
                "            background-color: #ffffff;" +
                "            border-radius: 8px;" +
                "            overflow: hidden;" +
                "            box-shadow: 0 4px 10px rgba(0, 0, 0, 0.1);" +
                "            margin-top: 20px;" +
                "            margin-bottom: 20px;" +
                "        }" +
                "        .header {" +
                "            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);" +
                "            color: #ffffff;" +
                "            padding: 20px;" +
                "            text-align: center;" +
                "        }" +
                "        .header h1 {" +
                "            margin: 0;" +
                "            font-size: 24px;" +
                "        }" +
                "        .content {" +
                "            padding: 30px;" +
                "            font-size: 16px;" +
                "        }" +
                "        .content pre {" +
                "            white-space: pre-wrap;" +
                "            font-family: inherit;" +
                "            margin: 0;" +
                "        }" +
                "        .footer {" +
                "            background-color: #f8f9fa;" +
                "            padding: 20px;" +
                "            text-align: center;" +
                "            font-size: 14px;" +
                "            color: #6c757d;" +
                "            border-top: 1px solid #eeeeee;" +
                "        }" +
                "        .btn {" +
                "            display: inline-block;" +
                "            padding: 10px 20px;" +
                "            margin-top: 20px;" +
                "            background-color: #667eea;" +
                "            color: #ffffff;" +
                "            text-decoration: none;" +
                "            border-radius: 5px;" +
                "            font-weight: bold;" +
                "        }" +
                "    </style>" +
                "</head>" +
                "<body>" +
                "    <div class='email-container'>" +
                "        <div class='header'>" +
                "            <h1>Időpontfoglaló Rendszer</h1>" +
                "        </div>" +
                "        <div class='content'>" +
                "            <h2>" + title + "</h2>" +
                "            <pre>" + content + "</pre>" +
                "        </div>" +
                "        <div class='footer'>" +
                "            <p>Ez egy automatikusan generált üzenet. Kérjük, ne válaszoljon rá.</p>" +
                "            <p>&copy; 2026 Időpontfoglaló Rendszer.</p>" +
                "        </div>" +
                "    </div>" +
                "</body>" +
                "</html>";
    }
}
