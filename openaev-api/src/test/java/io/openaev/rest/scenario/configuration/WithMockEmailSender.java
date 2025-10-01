package io.openaev.rest.scenario.configuration;

import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;

@Profile("test")
@Configuration
public class WithMockEmailSender {
  @Bean
  @Primary
  public JavaMailSender mailSender() {
    return Mockito.mock(JavaMailSender.class, Mockito.CALLS_REAL_METHODS);
  }
}
