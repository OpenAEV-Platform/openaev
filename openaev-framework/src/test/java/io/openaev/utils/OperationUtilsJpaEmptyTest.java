package io.openaev.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("OperationUtilsJpa empty / not_empty")
class OperationUtilsJpaEmptyTest {

  private enum Level {
    LOW
  }

  static Stream<Class<?>> nullCheckOnlyTypes() {
    return Stream.of(
        Boolean.class,
        boolean.class,
        Instant.class,
        LocalDate.class,
        Date.class,
        UUID.class,
        Level.class);
  }

  @SuppressWarnings("unchecked")
  private static Expression<String> path() {
    return mock(Expression.class);
  }

  @ParameterizedTest
  @MethodSource("nullCheckOnlyTypes")
  @DisplayName("given_nonTextType_should_emptyBeAPureNullCheck")
  void given_nonTextType_should_emptyBeAPureNullCheck(Class<?> type) {
    CriteriaBuilder cb = mock(CriteriaBuilder.class);
    Expression<String> path = path();
    Predicate isNull = mock(Predicate.class);
    when(cb.isNull(path)).thenReturn(isNull);

    Predicate result = OperationUtilsJpa.empty(path, cb, type);

    assertThat(result).isSameAs(isNull);
    verify(cb, never()).equal(any(Expression.class), anyString());
  }

  @ParameterizedTest
  @MethodSource("nullCheckOnlyTypes")
  @DisplayName("given_nonTextType_should_notEmptyBeAPureNotNullCheck")
  void given_nonTextType_should_notEmptyBeAPureNotNullCheck(Class<?> type) {
    CriteriaBuilder cb = mock(CriteriaBuilder.class);
    Expression<String> path = path();
    Predicate isNotNull = mock(Predicate.class);
    when(cb.isNotNull(path)).thenReturn(isNotNull);

    Predicate result = OperationUtilsJpa.notEmpty(path, cb, type);

    assertThat(result).isSameAs(isNotNull);
    verify(cb, never()).notEqual(any(Expression.class), anyString());
  }

  @Test
  @DisplayName("given_textType_should_emptyStillMatchBlankValues")
  void given_textType_should_emptyStillMatchBlankValues() {
    CriteriaBuilder cb = mock(CriteriaBuilder.class);
    Expression<String> path = path();
    Predicate isNull = mock(Predicate.class);
    Predicate isBlank = mock(Predicate.class);
    Predicate isSpace = mock(Predicate.class);
    Predicate combined = mock(Predicate.class);
    when(cb.isNull(path)).thenReturn(isNull);
    when(cb.equal(path, "")).thenReturn(isBlank);
    when(cb.equal(path, " ")).thenReturn(isSpace);
    when(cb.or(isNull, isBlank, isSpace)).thenReturn(combined);

    Predicate result = OperationUtilsJpa.empty(path, cb, String.class);

    assertThat(result).isSameAs(combined);
  }

  @Test
  @DisplayName("given_textType_should_notEmptyStillExcludeBlankValues")
  void given_textType_should_notEmptyStillExcludeBlankValues() {
    CriteriaBuilder cb = mock(CriteriaBuilder.class);
    Expression<String> path = path();
    Predicate isNotNull = mock(Predicate.class);
    Predicate notBlank = mock(Predicate.class);
    Predicate combined = mock(Predicate.class);
    when(cb.isNotNull(path)).thenReturn(isNotNull);
    when(cb.notEqual(path, "")).thenReturn(notBlank);
    when(cb.and(isNotNull, notBlank)).thenReturn(combined);

    Predicate result = OperationUtilsJpa.notEmpty(path, cb, String.class);

    assertThat(result).isSameAs(combined);
  }

  @Test
  @DisplayName("given_textOrArrayType_should_keepTheBlankChecks")
  void given_textOrArrayType_should_keepTheBlankChecks() {
    assertThat(OperationUtilsJpa.isNullCheckOnly(String.class)).isFalse();
    assertThat(OperationUtilsJpa.isNullCheckOnly(String[].class)).isFalse();
    assertThat(OperationUtilsJpa.isNullCheckOnly(Boolean.class)).isTrue();
  }
}
