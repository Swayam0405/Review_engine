package review_engine.project.service.auth;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import review_engine.project.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Sends verification codes through the SMTP server configured for Spring Mail.
 */
@Service
public class SmtpEmailService implements EmailService {
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String fromAddress;

    public SmtpEmailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                            @Value("${app.email.from}") String fromAddress) {
        this.mailSenderProvider = mailSenderProvider;
        this.fromAddress = fromAddress;
    }

    /**
     * Delivers an expiring verification code; no code is written to application logs.
     *
     * @param email normalized destination address
     * @param code short-lived verification code
     * @throws ApiException if no SMTP sender is configured
     */
    @Override
    public void sendVerificationCode(String email, String code) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Email delivery is not configured.");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("Verify your Review Engine email");
        message.setText("Your verification code is " + code + ". It expires soon and can only be used once.");
        sender.send(message);
    }
}
