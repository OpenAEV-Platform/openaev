package io.openaev.structured_output_parsers;

/**
 * Defines the different processing contexts for contract output types. Each context represents a
 * different way to process the output data.
 */
public enum ProcessingContext {
  /** Create findings from structured output data */
  FINDING,

  /** Create or update assets from structured outputs */
  ASSET,

  /** Validate expectations against structured output data */
  EXPECTATION
}
