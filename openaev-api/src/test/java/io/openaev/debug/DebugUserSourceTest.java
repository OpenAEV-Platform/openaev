package io.openaev.debug;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.config.OpenAEVPrincipal;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@DisplayName("DebugUserSource")
class DebugUserSourceTest {

  private final DebugUserSource source = new DebugUserSource();

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("returns 'anonymous' when there is no authenticated user")
  void anonymousWhenNoAuthentication() {
    SecurityContextHolder.clearContext();
    assertThat(source.currentUser()).isEqualTo("anonymous");
  }

  @Test
  @DisplayName("returns the id of the authenticated user")
  void returnsAuthenticatedUserId() {
    // The three-argument constructor produces an authenticated token.
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(principal("user-123"), null, List.of()));
    assertThat(source.currentUser()).isEqualTo("user-123");
  }

  @Test
  @DisplayName("returns 'anonymous' for an unauthenticated token (isAuthenticated() false)")
  void anonymousWhenTokenNotAuthenticated() {
    // The two-argument constructor produces an explicitly unauthenticated token: its principal
    // must not be attributed as the caller.
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal("user-123"), null));
    assertThat(source.currentUser()).isEqualTo("anonymous");
  }

  private static OpenAEVPrincipal principal(String id) {
    return new OpenAEVPrincipal() {
      @Override
      public String getId() {
        return id;
      }

      @Override
      public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
      }

      @Override
      public boolean isAdmin() {
        return false;
      }

      @Override
      public String getLang() {
        return "auto";
      }
    };
  }
}
