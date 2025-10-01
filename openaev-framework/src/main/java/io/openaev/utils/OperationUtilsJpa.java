package io.openaev.utils;

import static org.springframework.util.StringUtils.hasText;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class OperationUtilsJpa {

  private OperationUtilsJpa() {}

  // -- NOT CONTAINS --

  public static Predicate notContainsTexts(
      Expression<String> paths, CriteriaBuilder cb, List<String> texts, Class<?> type) {
    if (isEmpty(texts)) {
      return cb.conjunction();
    }

    Predicate[] predicates =
        texts.stream().map(text -> containsText(paths, cb, text, type)).toArray(Predicate[]::new);

    return cb.or(predicates).not();
  }

  // -- CONTAINS --

  public static Predicate containsTexts(
      Expression<String> paths, CriteriaBuilder cb, List<String> texts, Class<?> type) {
    if (isEmpty(texts)) {
      return cb.conjunction();
    }

    Predicate[] predicates =
        texts.stream().map(text -> containsText(paths, cb, text, type)).toArray(Predicate[]::new);

    return cb.or(predicates);
  }

  public static Predicate containsText(
      Expression<String> paths, CriteriaBuilder cb, String text, Class<?> type) {
    if (isEmpty(text)) {
      return cb.conjunction();
    }

    if (type.isAssignableFrom(Map.class) || type.getName().contains("ImmutableCollections")) {
      Expression<String> values = lower(arrayToString(avals(paths, cb), cb), cb);
      return cb.like(values, "%" + text.toLowerCase() + "%");
    }
    if (type.isArray() || type.isAssignableFrom(List.class)) {
      return cb.like(lower(arrayToString(paths, cb), cb), "%" + text.toLowerCase() + "%");
    }
    return cb.and(cb.like(cb.lower(paths), "%" + text.toLowerCase() + "%"), cb.isNotNull(paths));
  }

  // -- NOT EQUALS --

  public static Predicate notEqualsTexts(
      Expression<String> paths, CriteriaBuilder cb, List<String> texts, Class<?> type) {
    if (isEmpty(texts)) {
      return cb.conjunction();
    }

    Predicate[] predicates =
        texts.stream().map(text -> notEqualsText(paths, cb, text, type)).toArray(Predicate[]::new);

    return cb.or(predicates);
  }

  private static Predicate notEqualsText(
      Expression<String> paths, CriteriaBuilder cb, String text, Class<?> type) {
    if (isEmpty(text)) {
      return cb.conjunction();
    }

    return equalsText(paths, cb, text, type).not();
  }

  // -- EQUALS --

  public static Predicate equalsTexts(
      Expression<String> paths, CriteriaBuilder cb, List<String> texts, Class<?> type) {
    if (isEmpty(texts)) {
      return cb.conjunction();
    }

    Predicate[] predicates =
        texts.stream().map(text -> equalsText(paths, cb, text, type)).toArray(Predicate[]::new);

    return cb.or(predicates);
  }

  private static Predicate equalsText(
      Expression<String> paths, CriteriaBuilder cb, String text, Class<?> type) {
    if (isEmpty(text)) {
      return cb.conjunction();
    }

    if (type.isAssignableFrom(Map.class) || type.getName().contains("ImmutableCollections")) {
      Expression<String[]> values = lowerArray(avals(paths, cb), cb);
      return cb.isNotNull(arrayPosition(values, cb, cb.literal(text.toLowerCase())));
    }
    if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false")) {
      return cb.equal(paths, Boolean.valueOf(text));
    } else {
      return cb.equal(cb.lower(paths), text.toLowerCase());
    }
  }

  // -- NOT START WITH --

  public static Predicate notStartWithTexts(
      Expression<String> paths, CriteriaBuilder cb, List<String> texts, Class<?> type) {
    if (isEmpty(texts)) {
      return cb.conjunction();
    }

    Predicate[] predicates =
        texts.stream()
            .map(text -> notStartWithText(paths, cb, text, type))
            .toArray(Predicate[]::new);

    return cb.or(predicates);
  }

  public static Predicate notStartWithText(
      Expression<String> paths, CriteriaBuilder cb, String text, Class<?> type) {
    if (isEmpty(text)) {
      return cb.conjunction();
    }

    return startWithText(paths, cb, text, type).not();
  }

  // -- START WITH --

  public static Predicate startWithTexts(
      Expression<String> paths, CriteriaBuilder cb, List<String> texts, Class<?> type) {
    if (isEmpty(texts)) {
      return cb.conjunction();
    }

    Predicate[] predicates =
        texts.stream().map(text -> startWithText(paths, cb, text, type)).toArray(Predicate[]::new);

    return cb.or(predicates);
  }

  public static Predicate startWithText(
      Expression<String> paths, CriteriaBuilder cb, String text, Class<?> type) {
    if (isEmpty(text)) {
      return cb.conjunction();
    }

    if (type.isAssignableFrom(Map.class) || type.getName().contains("ImmutableCollections")) {
      Expression<String> values = lower(arrayToString(avals(paths, cb), cb), cb);
      return cb.like(cb.lower(values), text.toLowerCase() + "%");
    }

    return cb.like(cb.lower(paths), text.toLowerCase() + "%");
  }

  // -- NOT EMPTY --

  public static Predicate notEmpty(Expression<String> paths, CriteriaBuilder cb, Class<?> type) {
    return empty(paths, cb, type).not();
  }

  // -- EMPTY --

  public static Predicate empty(Expression<String> paths, CriteriaBuilder cb, Class<?> type) {
    Expression<String> finalPaths;
    if (type.isArray() || type.isAssignableFrom(List.class)) {
      finalPaths = arrayToString(paths, cb);
    } else {
      finalPaths = paths;
    }
    if (type.equals(Instant.class) || type.isEnum()) {
      return cb.isNull(finalPaths);
    }
    return cb.or(cb.isNull(finalPaths), cb.equal(finalPaths, ""), cb.equal(finalPaths, " "));
  }

  // -- DATE --

  public static Predicate greaterThanTexts(
      Expression<Instant> paths, CriteriaBuilder cb, List<String> texts) {
    if (isEmpty(texts)) {
      return cb.conjunction();
    }

    Predicate[] predicates =
        texts.stream().map(value -> greaterThanText(paths, cb, value)).toArray(Predicate[]::new);

    return cb.or(predicates);
  }

  public static Predicate greaterThanText(
      Expression<Instant> paths, CriteriaBuilder cb, String text) {
    if (isEmpty(text)) {
      return cb.conjunction();
    }

    return cb.greaterThan(paths, Instant.parse(text));
  }

  public static Predicate greaterThanOrEqualTexts(
      Expression<Instant> paths, CriteriaBuilder cb, List<String> texts) {
    if (isEmpty(texts)) {
      return cb.conjunction();
    }

    Predicate[] predicates =
        texts.stream()
            .map(value -> greaterThanOrEqualText(paths, cb, value))
            .toArray(Predicate[]::new);

    return cb.or(predicates);
  }

  public static Predicate greaterThanOrEqualText(
      Expression<Instant> paths, CriteriaBuilder cb, String text) {
    if (isEmpty(text)) {
      return cb.conjunction();
    }

    return cb.greaterThanOrEqualTo(paths, Instant.parse(text));
  }

  public static Predicate lessThanTexts(
      Expression<Instant> paths, CriteriaBuilder cb, List<String> texts) {
    if (isEmpty(texts)) {
      return cb.conjunction();
    }

    Predicate[] predicates =
        texts.stream().map(value -> lessThanText(paths, cb, value)).toArray(Predicate[]::new);

    return cb.or(predicates);
  }

  public static Predicate lessThanText(Expression<Instant> paths, CriteriaBuilder cb, String text) {
    if (isEmpty(text)) {
      return cb.conjunction();
    }

    return cb.lessThan(paths, Instant.parse(text));
  }

  public static Predicate lessThanOrEqualTexts(
      Expression<Instant> paths, CriteriaBuilder cb, List<String> texts) {
    if (isEmpty(texts)) {
      return cb.conjunction();
    }

    Predicate[] predicates =
        texts.stream()
            .map(value -> lessThanOrEqualText(paths, cb, value))
            .toArray(Predicate[]::new);

    return cb.or(predicates);
  }

  public static Predicate lessThanOrEqualText(
      Expression<Instant> paths, CriteriaBuilder cb, String text) {
    if (isEmpty(text)) {
      return cb.conjunction();
    }

    return cb.lessThanOrEqualTo(paths, Instant.parse(text));
  }

  // -- CUSTOM FUNCTION --

  private static Expression<String[]> lowerArray(Expression<?> paths, CriteriaBuilder cb) {
    return stringToArray(lower(arrayToString(paths, cb), cb), cb);
  }

  // -- BASE FUNCTION --

  private static Expression<Boolean> arrayPosition(
      Expression<String[]> paths, CriteriaBuilder cb, Expression<String> text) {
    return cb.function("array_position_wrapper", Boolean.class, paths, text);
  }

  private static Expression<String> lower(Expression<String> paths, CriteriaBuilder cb) {
    return cb.function("lower", String.class, paths);
  }

  private static Expression<String[]> stringToArray(Expression<String> paths, CriteriaBuilder cb) {
    return cb.function("string_to_array", String[].class, paths, cb.literal(" && "));
  }

  private static Expression<String> arrayToString(Expression<?> paths, CriteriaBuilder cb) {
    return cb.function("array_to_string_wrapper", String.class, paths, cb.literal(" && "));
  }

  private static Expression<String[]> avals(Expression<String> paths, CriteriaBuilder cb) {
    return cb.function("avals", String[].class, paths);
  }

  private static boolean isEmpty(List<String> texts) {
    return texts == null || texts.isEmpty() || texts.stream().anyMatch(s -> !hasText(s));
  }

  private static boolean isEmpty(String text) {
    return !hasText(text);
  }
}
