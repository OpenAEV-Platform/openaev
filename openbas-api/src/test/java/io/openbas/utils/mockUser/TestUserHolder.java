package io.openbas.utils.mockUser;

import static io.openbas.service.UserService.buildAuthenticationToken;

import io.openbas.database.model.User;
import io.openbas.database.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class TestUserHolder {

  @Autowired private UserRepository userRepository;
  private String currentUserId;

  public void set(User user) {
    this.currentUserId = user.getId();
  }

  public User get() {
    return userRepository
        .findById(currentUserId)
        .orElseThrow(() -> new IllegalStateException("User not found"));
  }

  /** Clear the holder and security context */
  public void clear() {
    this.currentUserId = null;
    SecurityContextHolder.clearContext();
  }

  /** Refresh Spring Security context with current user */
  public void refreshSecurityContext() {
    User user = get(); // reloads user from DB
    Authentication auth = buildAuthenticationToken(user);
    SecurityContextHolder.getContext().setAuthentication(auth);
  }
}
