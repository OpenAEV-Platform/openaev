package io.openaev.service;

import io.openaev.database.model.User;
import io.openaev.database.model.author.Authored;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthorService {

  private final UserService userService;

  /**
   * Makes the user acting now the author, in place of any organization. Returns false, and leaves
   * the author unchanged, when no user is acting (startup, schedulers, collectors).
   */
  public boolean authorByCurrentUser(Authored authored) {
    User user = userService.currentUserOrNull();
    if (user == null) {
      return false;
    }
    authored.setAuthorUser(user);
    authored.setAuthorOrganization(null);
    return true;
  }
}
