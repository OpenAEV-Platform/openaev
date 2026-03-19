package io.openaev.helper;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.openaev.database.model.Base;
import java.io.IOException;

/**
 * Custom JSON serializer that serializes a {@link Base} entity to just its ID string.
 *
 * <p>This serializer is useful for relationship fields where only the entity ID needs to be
 * included in the JSON output, reducing payload size and avoiding circular references.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @ManyToOne(fetch = FetchType.LAZY)
 * @JsonSerialize(using = MonoIdSerializer.class)
 * @JsonProperty("inject_exercise")
 * private Exercise exercise;
 * }</pre>
 *
 * @see MultiIdListSerializer
 * @see MultiIdSetSerializer
 */
public class MonoIdSerializer extends StdSerializer<Base> {

  /** Default constructor required by Jackson. */
  public MonoIdSerializer() {
    this(null);
  }

  /**
   * Constructor with type specification.
   *
   * @param t the type class being serialized
   */
  public MonoIdSerializer(Class<Base> t) {
    super(t);
  }

  /**
   * Serializes the entity to its ID string.
   *
   * @param base the entity to serialize
   * @param jsonGenerator the JSON generator
   * @param serializerProvider the serializer provider
   * @throws IOException if an I/O error occurs during serialization
   */
  @Override
  public void serialize(
      Base base, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
      throws IOException {
    jsonGenerator.writeString(base.getId());
  }
}
