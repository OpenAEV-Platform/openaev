package io.openaev.utils;

@FunctionalInterface
public interface TriVoid<T, U, V> {
  void apply(T t, U u, V v);
}
