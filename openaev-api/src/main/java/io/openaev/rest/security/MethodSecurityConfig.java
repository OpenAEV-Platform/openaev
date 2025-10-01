package io.openaev.rest.security;

import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Configuration
@EnableMethodSecurity(securedEnabled = true)
@RequiredArgsConstructor
public class MethodSecurityConfig {

  private final UserRepository userRepository;
  private final ExerciseRepository exerciseRepository;

  @Bean
  MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
    return new SecurityExpressionHandler(this.userRepository, this.exerciseRepository);
  }
}
