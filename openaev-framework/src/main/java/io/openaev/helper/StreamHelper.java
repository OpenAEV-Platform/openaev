package io.openaev.helper;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Utility class for working with Java Streams, Iterables, and Iterators.
 *
 * <p>This helper provides convenient methods for converting between collection types and creating
 * streams from iterators. These utilities are particularly useful when working with Spring Data
 * repositories which often return {@code Iterable} types.
 *
 * <p>All methods in this class are null-safe and will return empty collections when given null
 * input.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Convert repository results to a List
 * List<User> users = StreamHelper.fromIterable(userRepository.findAll());
 *
 * // Convert to a Set for deduplication
 * Set<String> uniqueNames = StreamHelper.iterableToSet(nameRepository.findAll());
 *
 * // Stream from an iterator
 * Stream<Item> itemStream = StreamHelper.asStream(iterator, true); // parallel
 * }</pre>
 */
public final class StreamHelper {

  private StreamHelper() {
    // Utility class - prevent instantiation
  }

  /**
   * Converts an Iterable to a List.
   *
   * <p>This is useful when working with Spring Data repositories that return Iterable.
   *
   * @param results the iterable to convert
   * @param <T> the element type
   * @return a new List containing all elements from the iterable
   */
  public static <T> List<T> fromIterable(Iterable<T> results) {
    if (results == null) {
      return List.of();
    }
    return StreamSupport.stream(results.spliterator(), false).collect(Collectors.toList());
  }

  /**
   * Converts an Iterable to a Set.
   *
   * <p>Duplicates are removed based on element equality.
   *
   * @param results the iterable to convert
   * @param <T> the element type
   * @return a new Set containing unique elements from the iterable
   */
  public static <T> Set<T> iterableToSet(Iterable<T> results) {
    if (results == null) {
      return Set.of();
    }
    return StreamSupport.stream(results.spliterator(), false).collect(Collectors.toSet());
  }

  /**
   * Creates a sequential Stream from an Iterator.
   *
   * @param sourceIterator the iterator to convert
   * @param <T> the element type
   * @return a sequential Stream over the iterator's elements
   */
  public static <T> Stream<T> asStream(Iterator<T> sourceIterator) {
    return asStream(sourceIterator, false);
  }

  /**
   * Creates a Stream from an Iterator with optional parallelism.
   *
   * @param sourceIterator the iterator to convert
   * @param parallel whether the stream should be parallel
   * @param <T> the element type
   * @return a Stream over the iterator's elements
   */
  public static <T> Stream<T> asStream(Iterator<T> sourceIterator, boolean parallel) {
    if (sourceIterator == null) {
      return Stream.empty();
    }
    Iterable<T> iterable = () -> sourceIterator;
    return StreamSupport.stream(iterable.spliterator(), parallel);
  }
}
