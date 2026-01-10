package io.openaev.helper;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.openaev.database.model.Collector;
import java.io.IOException;

/**
 * Custom JSON serializer that serializes a {@link Collector} entity to just its type string.
 *
 * <p>This serializer is useful when only the collector type identifier is needed in the JSON
 * output, rather than the full collector entity.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @ManyToOne(fetch = FetchType.LAZY)
 * @JsonSerialize(using = CollectorTypeSerializer.class)
 * @JsonProperty("collector_type")
 * private Collector collector;
 * }</pre>
 *
 * @see Collector
 */
public class CollectorTypeSerializer extends JsonSerializer<Collector> {

  /**
   * Serializes the collector to its type string.
   *
   * @param value the collector to serialize
   * @param jsonGenerator the JSON generator
   * @param serializerProvider the serializer provider
   * @throws IOException if an I/O error occurs during serialization
   */
  @Override
  public void serialize(
      Collector value, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
      throws IOException {
    jsonGenerator.writeString(value.getType());
  }
}
