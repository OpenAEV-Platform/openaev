package io.openaev.config;

import static org.mockito.Mockito.*;
import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;

import io.openaev.service.LogService;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionManager - session expired audit")
class SessionManagerTest {

  @Mock private LogService logService;
  @Mock private HttpSession session;
  @Mock private HttpSessionEvent sessionEvent;

  private HttpSessionListener listener;

  @BeforeEach
  void setUp() {
    SessionManager sessionManager = new SessionManager(logService);
    listener = sessionManager.httpSessionListener();
    lenient().when(sessionEvent.getSession()).thenReturn(session);
    lenient().when(session.getId()).thenReturn("session-abc");
    lenient().when(session.getAttribute(SessionManager.AUTH_SESSION_CONTEXT)).thenReturn(null);
  }

  @Nested
  @DisplayName("emitSessionExpiredEvent")
  class EmitSessionExpiredEvent {

    @Test
    @DisplayName("given_authenticatedSessionWithoutExplicitLogout_should_emitSessionExpiredEvent")
    void given_authenticatedSessionWithoutExplicitLogout_should_emitSessionExpiredEvent() {
      // -- PREPARE --
      when(session.getAttribute(SessionManager.AUTH_SESSION_CONTEXT))
          .thenReturn(new SessionManager.AuthSessionContext("10.0.0.1", "ua/1.0"));
      when(session.getAttribute(SessionManager.EXPLICIT_LOGOUT)).thenReturn(null);

      SecurityContext secCtx = mock(SecurityContext.class);
      Authentication auth = mock(Authentication.class);
      OpenAEVPrincipal principal = mock(OpenAEVPrincipal.class);

      when(session.getAttribute(SPRING_SECURITY_CONTEXT_KEY)).thenReturn(secCtx);
      when(secCtx.getAuthentication()).thenReturn(auth);
      when(auth.getPrincipal()).thenReturn(principal);
      when(principal.getId()).thenReturn("user-456");

      when(session.getCreationTime()).thenReturn(1_000L);
      when(session.getLastAccessedTime()).thenReturn(61_000L);

      // -- EXECUTE --
      listener.sessionDestroyed(sessionEvent);

      // -- VERIFY --
      verify(logService)
          .logSessionExpiredEvent(
              eq("user-456"),
              eq("session-abc"),
              eq(60L),
              eq("inactivity_timeout"),
              eq("10.0.0.1"),
              eq("ua/1.0"));
    }

    @Test
    @DisplayName("given_explicitLogoutMarker_should_notEmitEvent")
    void given_explicitLogoutMarker_should_notEmitEvent() {
      // -- PREPARE --
      when(session.getAttribute(SessionManager.AUTH_SESSION_CONTEXT))
          .thenReturn(new SessionManager.AuthSessionContext("10.0.0.1", "ua/1.0"));
      when(session.getAttribute(SessionManager.EXPLICIT_LOGOUT)).thenReturn(Boolean.TRUE);

      // -- EXECUTE --
      listener.sessionDestroyed(sessionEvent);

      // -- VERIFY --
      verify(logService, never())
          .logSessionExpiredEvent(any(), any(), anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("given_unauthenticatedSession_should_notEmitEvent")
    void given_unauthenticatedSession_should_notEmitEvent() {
      // -- EXECUTE --
      listener.sessionDestroyed(sessionEvent);

      // -- VERIFY --
      verify(logService, never())
          .logSessionExpiredEvent(any(), any(), anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("given_anonymousUserId_should_notEmitEvent")
    void given_anonymousUserId_should_notEmitEvent() {
      // -- PREPARE --
      when(session.getAttribute(SessionManager.AUTH_SESSION_CONTEXT))
          .thenReturn(new SessionManager.AuthSessionContext("10.0.0.1", "ua/1.0"));
      when(session.getAttribute(SessionManager.EXPLICIT_LOGOUT)).thenReturn(null);

      SecurityContext secCtx = mock(SecurityContext.class);
      Authentication auth = mock(Authentication.class);
      OpenAEVPrincipal principal = mock(OpenAEVPrincipal.class);

      when(session.getAttribute(SPRING_SECURITY_CONTEXT_KEY)).thenReturn(secCtx);
      when(secCtx.getAuthentication()).thenReturn(auth);
      when(auth.getPrincipal()).thenReturn(principal);
      when(principal.getId()).thenReturn("anonymous");

      // -- EXECUTE --
      listener.sessionDestroyed(sessionEvent);

      // -- VERIFY --
      verify(logService, never())
          .logSessionExpiredEvent(any(), any(), anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("given_noSecurityContext_should_notEmitEvent")
    void given_noSecurityContext_should_notEmitEvent() {
      // -- PREPARE --
      when(session.getAttribute(SessionManager.AUTH_SESSION_CONTEXT))
          .thenReturn(new SessionManager.AuthSessionContext("10.0.0.1", "ua/1.0"));
      when(session.getAttribute(SessionManager.EXPLICIT_LOGOUT)).thenReturn(null);
      when(session.getAttribute(SPRING_SECURITY_CONTEXT_KEY)).thenReturn(null);

      // -- EXECUTE --
      listener.sessionDestroyed(sessionEvent);

      // -- VERIFY --
      verify(logService, never())
          .logSessionExpiredEvent(any(), any(), anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("given_principalNotOpenAEVPrincipal_should_notEmitEvent")
    void given_principalNotOpenAEVPrincipal_should_notEmitEvent() {
      // -- PREPARE --
      when(session.getAttribute(SessionManager.AUTH_SESSION_CONTEXT))
          .thenReturn(new SessionManager.AuthSessionContext("10.0.0.1", "ua/1.0"));
      when(session.getAttribute(SessionManager.EXPLICIT_LOGOUT)).thenReturn(null);

      SecurityContext secCtx = mock(SecurityContext.class);
      Authentication auth = mock(Authentication.class);

      when(session.getAttribute(SPRING_SECURITY_CONTEXT_KEY)).thenReturn(secCtx);
      when(secCtx.getAuthentication()).thenReturn(auth);
      when(auth.getPrincipal()).thenReturn("someStringPrincipal");

      // -- EXECUTE --
      listener.sessionDestroyed(sessionEvent);

      // -- VERIFY --
      verify(logService, never())
          .logSessionExpiredEvent(any(), any(), anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("given_sessionAlreadyInvalidated_should_notThrow")
    void given_sessionAlreadyInvalidated_should_notThrow() {
      // -- PREPARE --
      when(session.getAttribute(SessionManager.AUTH_SESSION_CONTEXT))
          .thenThrow(new IllegalStateException("session already invalidated"));

      // -- EXECUTE --
      listener.sessionDestroyed(sessionEvent);

      // -- VERIFY --
      verify(logService, never())
          .logSessionExpiredEvent(any(), any(), anyLong(), any(), any(), any());
    }
  }
}
