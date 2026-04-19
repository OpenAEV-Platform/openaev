package io.openaev.security.token;

import io.openaev.database.model.User;
import io.openaev.service.UserService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class PlainTokenExtractor implements ExtractorBase {

  private final UserService userService;

  @Override
  public Optional<User> authUser(String value) {
    return userService.findByToken(value);
  }
}
