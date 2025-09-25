package io.openaev.injectors.email.service;

import io.openaev.database.model.Setting;
import io.openaev.database.repository.SettingRepository;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SmtpService {

    private static final String SMTP_SETTINGS_KEY = "smtp_service_available";

    private boolean isServiceAvailable;

    @Autowired private JavaMailSender mailSender;

    private final SettingRepository settingRepository;

    public SmtpService(@Autowired SettingRepository settingRepository) {
        this.settingRepository = settingRepository;
        this.saveServiceState(false);
        this.testConnection();
    }

    public MimeMessage createMimeMessage() {
        return this.mailSender.createMimeMessage();
    }

    public void send(MimeMessage mimeMessage) {
        this.mailSender.send(mimeMessage);
    }

    public boolean isServiceAvailable() {
        return this.isServiceAvailable;
    }

    // Check connection every 10 seconds
    @Scheduled(fixedDelay = 10000, initialDelay = 10000)
    public void connectionListener() {
        this.testConnection();
    }

    private void testConnection() {
        try {
            if (mailSender instanceof JavaMailSenderImpl javaMailSender) {
                javaMailSender.testConnection();
                this.saveServiceState(true);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            this.saveServiceState(false);
        }
    }

    private void saveServiceState(boolean state) {
        Setting imapSetting = this.settingRepository.findByKey(SMTP_SETTINGS_KEY)
                .orElse(new Setting(SMTP_SETTINGS_KEY, null));
        imapSetting.setValue(String.valueOf(state));
        this.settingRepository.save(imapSetting);
        this.isServiceAvailable = state;
    }

}
