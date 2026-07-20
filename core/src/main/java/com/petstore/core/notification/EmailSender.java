package com.petstore.core.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Real SMTP transport for customer notifications, ready but deliberately not
 * wired in: Notifier logs instead, matching the legacy default (its SendMail
 * flags shipped as false, so out of the box it never mailed either). To send
 * real email, call send(...) from Notifier in place of its log line and point
 * the spring.mail.* properties at a real SMTP server.
 */
@Component
public class EmailSender {

	private final JavaMailSender mailSender;
	private final String from;

	public EmailSender(JavaMailSender mailSender, @Value("${petstore.mail.from}") String from) {
		this.mailSender = mailSender;
		this.from = from;
	}

	public void send(String to, String subject, String body) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(to);
		message.setSubject(subject);
		message.setText(body);
		mailSender.send(message);
	}
}
