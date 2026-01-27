package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class StixRefToExternalRef {

  @JsonProperty("stix_ref")
  private String stixRef;

  @JsonProperty("external_ref")
  private String externalRef;

  /**
   * StixRefToExternalRef object to use when only external reference is necessary
   *
   * @param stixRef id
   * @param externalRef stix external reference
   */
  public StixRefToExternalRef(String stixRef, String externalRef) {
    this.stixRef = stixRef;
    this.externalRef = externalRef;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (null == o || this.getClass() != o.getClass()) {
      return false;
    }
    final StixRefToExternalRef that = (StixRefToExternalRef) o;
    return Objects.equals(this.stixRef, that.stixRef)
        && Objects.equals(this.externalRef, that.externalRef);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.stixRef, this.externalRef);
  }
}
