package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
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

  @JsonProperty("external_refs")
  private List<String> externalRefs;

  /**
   * StixRefToExternalRef object to use when only external reference is necessary
   *
   * @param stixRef id
   * @param externalRefs stix external references
   */
  public StixRefToExternalRef(String stixRef, List<String> externalRefs) {
    this.stixRef = stixRef;
    this.externalRefs = externalRefs;
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
        && Objects.equals(this.externalRefs, that.externalRefs);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.stixRef, this.externalRefs);
  }
}
