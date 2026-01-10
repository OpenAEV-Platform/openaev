package io.openaev.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A simple key-value pair model for generic data representation.
 *
 * <p>This model is commonly used for:
 *
 * <ul>
 *   <li>Configuration options
 *   <li>Dynamic form data
 *   <li>HTTP headers or parameters
 *   <li>Generic metadata storage
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PairModel {

  /** The key identifier of this pair. */
  @JsonProperty("key")
  private String key;

  /** The value associated with the key. */
  @JsonProperty("value")
  private String value;
}
