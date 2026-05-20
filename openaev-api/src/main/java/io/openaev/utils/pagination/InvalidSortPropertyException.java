package io.openaev.utils.pagination;

/** Thrown when a search request contains an invalid or non-sortable property name. */
public class InvalidSortPropertyException extends IllegalArgumentException {

  public InvalidSortPropertyException(String property) {
    super("Unknown sort property '%s', please try an other one".formatted(property));
  }
}
