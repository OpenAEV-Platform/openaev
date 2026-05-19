package io.openaev.rest.url_access_token.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesRegex;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import io.openaev.api.url_access_token.UrlAccessTokenService;
import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.UrlAccessToken;
import io.openaev.database.model.User;
import io.openaev.database.repository.UrlAccessTokenRepository;
import io.openaev.service.UserService;
import io.openaev.utils.RandomUtils;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("UrlAccessTokenService tests")
class UrlAccessTokenServiceTest {

  @Mock private UserService userService;
  @Mock private RandomUtils randomUtils;
  @Mock private UrlAccessTokenRepository urlAccessTokenRepository;
  @Mock private OpenAEVConfig openAEVConfig;

  @InjectMocks private UrlAccessTokenService urlAccessTokenService;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(urlAccessTokenService, "expiryMarginDays", 7);
    ReflectionTestUtils.setField(urlAccessTokenService, "retentionDays", 30);
    lenient().when(randomUtils.getRandomAlphanumeric(anyInt())).thenReturn("raw-token");
    lenient().when(openAEVConfig.getBaseUrl()).thenReturn("http://localhost:8080");
  }

  @Nested
  @DisplayName("generateToken")
  class GenerateTokenTests {

    @Test
    void given_validInput_should_generateAndPersistToken() {
      // Arrange
      Instant exerciseEnd = Instant.parse("2026-05-12T10:00:00Z");
      Exercise exercise = new Exercise();
      exercise.setEnd(exerciseEnd);
      User tokenUser = new User();
      User creatorUser = new User();
      String url = "/api/player/resource";

      when(userService.currentUser()).thenReturn(creatorUser);
      when(urlAccessTokenRepository.save(any(UrlAccessToken.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // Act
      String rawToken = urlAccessTokenService.generateTokenUrl(exercise, tokenUser, url);

      // Assert
      assertNotNull(rawToken);
      assertFalse(rawToken.isBlank());

      ArgumentCaptor<UrlAccessToken> tokenCaptor = ArgumentCaptor.forClass(UrlAccessToken.class);
      verify(urlAccessTokenRepository).save(tokenCaptor.capture());

      UrlAccessToken savedToken = tokenCaptor.getValue();
      assertEquals(url, savedToken.getUrl());
      assertEquals(tokenUser, savedToken.getUser());
      assertEquals(creatorUser, savedToken.getCreatorUser());
      assertEquals(exercise, savedToken.getExercise());
      assertEquals(exerciseEnd.plus(7, ChronoUnit.DAYS), savedToken.getExpiresAt());
      assertNotNull(savedToken.getTokenHash());
      assertNotEquals(rawToken, savedToken.getTokenHash());
      assertThat(savedToken.getTokenHash(), matchesRegex("^[a-f0-9]{64}$"));
    }

    @Test
    void given_noCurrentUser_should_generateTokenWithNullCreator() {
      // Arrange
      Exercise exercise = new Exercise();
      exercise.setEnd(Instant.now().plus(1, ChronoUnit.DAYS));
      User tokenUser = new User();

      when(userService.currentUser()).thenThrow(new RuntimeException("No security context"));

      // Act
      urlAccessTokenService.generateTokenUrl(exercise, tokenUser, "/api/resource");

      // Assert
      ArgumentCaptor<UrlAccessToken> tokenCaptor = ArgumentCaptor.forClass(UrlAccessToken.class);
      verify(urlAccessTokenRepository).save(tokenCaptor.capture());
      assertNull(tokenCaptor.getValue().getCreatorUser());
    }
  }

  @Nested
  @DisplayName("validateToken")
  class ValidateTokenTests {

    @Test
    void given_validTokenAndMatchingScope_should_returnToken() {
      // Arrange
      String rawToken = "raw-token";
      UrlAccessToken token = buildValidToken("exercise-1", "user-1");
      when(urlAccessTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

      // Act
      UrlAccessToken result = urlAccessTokenService.validateToken(rawToken, "exercise-1");

      // Assert
      assertEquals(token, result);
    }

    @Test
    void given_unknownToken_should_throwAccessDeniedException() {
      // Arrange
      when(urlAccessTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

      // Act + Assert
      assertThrows(
          AccessDeniedException.class,
          () -> urlAccessTokenService.validateToken("unknown-token", null));
    }

    @Test
    void given_expiredToken_should_throwAccessDeniedException() {
      // Arrange
      UrlAccessToken token = buildValidToken("exercise-1", "user-1");
      token.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
      when(urlAccessTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

      // Act + Assert
      assertThrows(
          AccessDeniedException.class,
          () -> urlAccessTokenService.validateToken("raw-token", "exercise-1"));
    }

    @Test
    void given_revokedToken_should_throwAccessDeniedException() {
      // Arrange
      UrlAccessToken token = buildValidToken("exercise-1", "user-1");
      token.setRevokedAt(Instant.now());
      when(urlAccessTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

      // Act + Assert
      assertThrows(
          AccessDeniedException.class,
          () -> urlAccessTokenService.validateToken("raw-token", "exercise-1"));
    }

    @Test
    void given_exerciseScopeMismatch_should_throwAccessDeniedException() {
      // Arrange
      UrlAccessToken token = buildValidToken("exercise-1", "user-1");
      when(urlAccessTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

      // Act + Assert
      assertThrows(
          AccessDeniedException.class,
          () -> urlAccessTokenService.validateToken("raw-token", "exercise-2"));
    }

    @Test
    void given_validTokenWithDifferentUser_should_stillReturnTokenBecauseUserScopeIsNotValidatedHere() {
      // Arrange
      UrlAccessToken token = buildValidToken("exercise-1", "user-1");
      when(urlAccessTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

      // Act
      UrlAccessToken result = urlAccessTokenService.validateToken("raw-token", "exercise-1");

      // Assert
      assertEquals(token, result);
    }
  }

  @Nested
  @DisplayName("validateTokenExpiration")
  class ValidateTokenExpirationTests {

    @Test
    void given_validToken_should_returnToken() {
      // Arrange
      UrlAccessToken token = buildValidToken("exercise-1", "user-1");
      when(urlAccessTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

      // Act
      UrlAccessToken result = urlAccessTokenService.validateTokenExpiration("raw-token");

      // Assert
      assertEquals(token, result);
      verify(urlAccessTokenRepository, never()).delete(any());
    }

    @Test
    void given_unknownToken_should_throwAccessDeniedException() {
      // Arrange
      when(urlAccessTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

      // Act + Assert
      assertThrows(
          AccessDeniedException.class,
          () -> urlAccessTokenService.validateTokenExpiration("raw-token"));
      verify(urlAccessTokenRepository, never()).delete(any());
    }

    @Test
    void given_expiredToken_should_deleteThenThrowAccessDeniedException() {
      // Arrange
      UrlAccessToken token = buildValidToken("exercise-1", "user-1");
      token.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
      when(urlAccessTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

      // Act + Assert
      assertThrows(
          AccessDeniedException.class,
          () -> urlAccessTokenService.validateTokenExpiration("raw-token"));
      verify(urlAccessTokenRepository).delete(token);
    }
  }

  @Nested
  @DisplayName("validateTokenAndFindUserId")
  class ValidateTokenAndFindUserIdTests {

    @Test
    void given_validToken_should_returnUserId() {
      // Arrange
      UrlAccessToken token = buildValidToken("exercise-1", "user-1");
      when(urlAccessTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

      // Act
      String result = urlAccessTokenService.validateTokenAndFindUserId("raw-token");

      // Assert
      assertEquals("user-1", result);
    }
  }

  @Nested
  @DisplayName("updateLastUsed")
  class UpdateLastUsedTests {

    @Test
    void given_token_should_updateLastUsedAndSave() {
      // Arrange
      UrlAccessToken token = buildValidToken("exercise-1", "user-1");
      Instant beforeUpdate = Instant.now().minus(1, ChronoUnit.SECONDS);

      // Act
      urlAccessTokenService.updateLastUsed(token);

      // Assert
      assertNotNull(token.getLastUsedAt());
      assertTrue(token.getLastUsedAt().isAfter(beforeUpdate));
      verify(urlAccessTokenRepository).save(token);
    }
  }

  @Nested
  @DisplayName("revokeToken")
  class RevokeTokenTests {

    @Test
    void given_existingToken_should_revokeAndSave() {
      // Arrange
      UrlAccessToken token = buildValidToken("exercise-1", "user-1");
      when(urlAccessTokenRepository.findById("token-id")).thenReturn(Optional.of(token));

      // Act
      urlAccessTokenService.revokeToken("token-id");

      // Assert
      assertNotNull(token.getRevokedAt());
      verify(urlAccessTokenRepository).save(token);
    }

    @Test
    void given_unknownToken_should_throwAccessDeniedException() {
      // Arrange
      when(urlAccessTokenRepository.findById("unknown")).thenReturn(Optional.empty());

      // Act + Assert
      assertThrows(AccessDeniedException.class, () -> urlAccessTokenService.revokeToken("unknown"));
      verify(urlAccessTokenRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("revokeAllForExercise")
  class RevokeAllForExerciseTests {

    @Test
    void given_exerciseId_should_delegateAndReturnUpdatedCount() {
      // Arrange
      when(urlAccessTokenRepository.revokeAllByExerciseId("exercise-1")).thenReturn(3);

      // Act
      int result = urlAccessTokenService.revokeAllForExercise("exercise-1");

      // Assert
      assertEquals(3, result);
      verify(urlAccessTokenRepository).revokeAllByExerciseId("exercise-1");
    }
  }

  @Nested
  @DisplayName("purgeExpiredAndRevokedTokens")
  class PurgeExpiredAndRevokedTokensTests {

    @Test
    void given_retentionWindow_should_deleteTokensOlderThanCutoff() {
      // Arrange
      when(urlAccessTokenRepository.deleteExpiredAndRevokedBefore(any(Instant.class)))
          .thenReturn(5);
      Instant beforeCall = Instant.now();

      // Act
      int deletedCount = urlAccessTokenService.purgeExpiredAndRevokedTokens();
      Instant afterCall = Instant.now();

      // Assert
      assertEquals(5, deletedCount);
      ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
      verify(urlAccessTokenRepository).deleteExpiredAndRevokedBefore(cutoffCaptor.capture());

      Instant expectedMin = beforeCall.minus(30, ChronoUnit.DAYS);
      Instant expectedMax = afterCall.minus(30, ChronoUnit.DAYS);
      assertFalse(cutoffCaptor.getValue().isBefore(expectedMin));
      assertFalse(cutoffCaptor.getValue().isAfter(expectedMax));
    }
  }

  private UrlAccessToken buildValidToken(String exerciseId, String userId) {
    Exercise exercise = new Exercise();
    exercise.setId(exerciseId);

    User user = new User();
    user.setId(userId);

    UrlAccessToken token = new UrlAccessToken();
    token.setExercise(exercise);
    token.setUser(user);
    token.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
    return token;
  }
}
