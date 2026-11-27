package io.openaev.utils;

import io.openaev.database.model.User;
import io.openaev.database.repository.UserRepository;
import io.openaev.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Opens a security session as the platform administrator, for a test that seeds or acts on its
 * behalf outside any request. No production code opens this session, so the read of the
 * administrator runs from this helper and not from a production frame: the fail-closed gate treats
 * the empty scope of that read as the test's own choice.
 */
@Component
public class AdminSessionTestHelper {

  @Value("${openbas.admin.email:${openaev.admin.email:#{null}}}")
  private String adminEmail;

  @Autowired private UserRepository userRepository;
  @Autowired private UserService userService;

  /** Sets the security context of the current thread to the platform administrator. */
  public void open() {
    User admin = userRepository.findByEmailIgnoreCase(adminEmail).orElseThrow();
    userService.createUserSession(admin);
  }
}
