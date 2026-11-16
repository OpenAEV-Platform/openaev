package io.openaev.database.model.payload;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.database.model.CollectorType;
import io.openaev.database.model.Payload.PAYLOAD_SOURCE;
import io.openaev.helper.CollectorTypeNameSerializer;
import io.openaev.jsonapi.JsonApiExtraAttributes;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A payload version whose origin is stored on its {@link PayloadIdentity}. The API, imports and
 * exports still read and write the origin on the payload, as before identities.
 */
public interface HasPayloadIdentity extends JsonApiExtraAttributes {

  PayloadIdentity getIdentity();

  @JsonProperty("payload_external_id")
  default String getExternalId() {
    return getIdentity().getExternalId();
  }

  default void setExternalId(String externalId) {
    getIdentity().setExternalId(externalId);
  }

  @JsonProperty("payload_source")
  @NotNull
  default PAYLOAD_SOURCE getSource() {
    return getIdentity().getSource();
  }

  default void setSource(PAYLOAD_SOURCE source) {
    getIdentity().setSource(source);
  }

  /** A version released by a catalogue, which the platform never changes. */
  @JsonIgnore
  default boolean isManaged() {
    return getIdentity().isManaged();
  }

  @JsonProperty("payload_collector_type")
  @JsonSerialize(using = CollectorTypeNameSerializer.class)
  @Schema(implementation = String.class)
  default CollectorType getCollectorType() {
    return getIdentity().getCollectorType();
  }

  @JsonIgnore
  default void setCollectorType(CollectorType collectorType) {
    getIdentity().setCollectorType(collectorType);
  }

  @Override
  default Map<String, Object> extraAttributes() {
    Map<String, Object> attributes = new LinkedHashMap<>();
    attributes.put("payload_external_id", getExternalId());
    attributes.put("payload_source", getSource() == null ? null : getSource().name());
    return attributes;
  }

  @Override
  default void applyExtraAttributes(Map<String, Object> attributes) {
    if (attributes.get("payload_external_id") instanceof String externalId) {
      setExternalId(externalId);
    }
    if (attributes.get("payload_source") instanceof String source) {
      setSource(PAYLOAD_SOURCE.valueOf(source));
    }
  }
}
