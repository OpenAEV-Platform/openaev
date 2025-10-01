package io.openaev.stix.types.inner;

import static io.openaev.stix.types.Hashes.parseHashes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.stix.types.Hashes;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class ExternalReference {
  @JsonProperty("source_name")
  private String sourceName;

  @JsonProperty("description")
  private String description;

  @JsonProperty("hashes")
  private Hashes hashes;

  @JsonProperty("url")
  private String url;

  @JsonProperty("external_id")
  private String externalId;

  public static ExternalReference parseExternalReference(JsonNode propertyNode) {
    ExternalReference externalReference = new ExternalReference();
    externalReference.setSourceName(propertyNode.get("source_name").asText());
    if (propertyNode.has("description")) {
      externalReference.setDescription(propertyNode.get("description").asText());
    }
    if (propertyNode.has("external_id")) {
      externalReference.setExternalId(propertyNode.get("external_id").asText());
    }
    if (propertyNode.has("hashes")) {
      externalReference.setHashes(parseHashes(propertyNode.get("hashes")));
    }
    if (propertyNode.has("url")) {
      externalReference.setUrl(propertyNode.get("url").asText());
    }
    return externalReference;
  }
}
