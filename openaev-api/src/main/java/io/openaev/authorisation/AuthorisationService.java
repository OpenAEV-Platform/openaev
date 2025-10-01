package io.openaev.authorisation;

import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.security.SecurityExpression;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Getter
@RequiredArgsConstructor
@Service
public class AuthorisationService {
  private final ExerciseRepository exerciseRepository;
  private final UserRepository userRepository;

  public SecurityExpression getSecurityExpression() {
    return new SecurityExpression(
        SecurityContextHolder.getContext().getAuthentication(), userRepository, exerciseRepository);
  }
}
