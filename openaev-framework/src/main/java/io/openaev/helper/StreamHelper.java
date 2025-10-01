package io.openaev.helper;

import static java.util.stream.StreamSupport.stream;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class StreamHelper {

  private StreamHelper() {}

  public static <T> List<T> fromIterable(Iterable<T> results) {
    return stream(results.spliterator(), false).collect(Collectors.toList());
  }

  public static <T> Set<T> iterableToSet(Iterable<T> results) {
    return StreamSupport.stream(results.spliterator(), false).collect(Collectors.toSet());
  }

  public static <T> Stream<T> asStream(Iterator<T> sourceIterator) {
    return asStream(sourceIterator, false);
  }

  public static <T> Stream<T> asStream(Iterator<T> sourceIterator, boolean parallel) {
    Iterable<T> iterable = () -> sourceIterator;
    return StreamSupport.stream(iterable.spliterator(), parallel);
  }
}
