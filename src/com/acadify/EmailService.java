package com.acadify;

import javax.mail.*;
import javax.mail.internet.*;
import java.util.Properties;


public class EmailService {

	private static final String EMAIL_USER = EnvConfig.get("EMAIL_USER");
	private static final String EMAIL_PASS = EnvConfig.get("EMAIL_PASS");
	private static final String CLIENT_URL = EnvConfig.get("CLIENT_URL");

    public static void sendVerificationEmail(String toEmail, String name, String token) throws Exception {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(EMAIL_USER, EMAIL_PASS);
            }
        });

        String verifyUrl = CLIENT_URL + "/verify-email?token=" + token;

        String htmlBody = "<div style='font-family:sans-serif;max-width:480px;margin:auto'>"
            + "<h2>Welcome to Acadify, " + name + "!</h2>"
            + "<p>Click the button below to verify your email. This link expires in <b>24 hours</b>.</p>"
            + "<a href='" + verifyUrl + "' style='display:inline-block;margin-top:16px;"
            + "padding:12px 24px;background:#3b82f6;color:#fff;border-radius:8px;"
            + "text-decoration:none;font-weight:600'>Verify Email</a>"
            + "<p style='margin-top:16px;color:#888;font-size:12px'>"
            + "If you didn't sign up, ignore this email.</p></div>";

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(EMAIL_USER, "Acadify"));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
        message.setSubject("Verify your Acadify account");
        message.setContent(htmlBody, "text/html; charset=utf-8");

        Transport.send(message);
    }
}