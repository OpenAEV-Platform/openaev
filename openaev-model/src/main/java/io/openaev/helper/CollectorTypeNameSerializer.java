package io.openaev.helper;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.openaev.database.model.CollectorType;
import java.io.IOException;

/**
 * Custom JSON serializer that serializes a {@link CollectorType} entity to just its name string.
 *
 * <p>This serializer outputs the human-readable type name (e.g. "openaev_crowdstrike") rather than
 * the technical UUID, which is the expected API contract for payload and detection-remediation
 * endpoints.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @ManyToOne(fetch = FetchType.LAZY)
 * @JsonSerialize(using = CollectorTypeNameSerializer.class)
 * @JsonProperty("payload_collector_type")
 * private CollectorType collectorType;
 * }</pre>
 *
 * @see CollectorType
 */
public class CollectorTypeNameSerializer extends JsonSerializer<CollectorType> {

  @Override
  public void serialize(
      CollectorType value, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
      throws IOException {
    jsonGenerator.writeString(value.getName());
  }
}
