package io.openaev.utils.sanitisation;

public interface Sanitiser<T> {
  T sanitise(T bad);
}
