package io.openaev.injector_contract;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Defines the cardinality (multiplicity) of a contract field.
 *
 * <p>Cardinality determines whether a field accepts a single value or multiple values:
 *
 * <ul>
 *   <li>{@link #One} - Field accepts exactly one value (single-select, single input)
 *   <li>{@link #Multiple} - Field accepts zero or more values (multi-select, list input)
 * </ul>
 */
@SuppressWarnings("PackageAccessibility")
public enum ContractCardinality {
  /** Single value cardinality - field accepts exactly one value. */
  @JsonProperty("1")
  One,

  /** Multiple value cardinality - field accepts zero or more values. */
  @JsonProperty("n")
  Multiple
}
