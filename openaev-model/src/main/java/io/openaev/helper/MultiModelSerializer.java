package io.openaev.helper;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.openaev.database.model.Base;
import java.io.IOException;
import java.util.List;

/**
 * Custom JSON serializer that serializes a {@link List} of {@link Base} entities as a full JSON
 * array with complete entity data.
 *
 * <p>Unlike {@link MultiIdListSerializer} which only outputs IDs, this serializer includes the
 * complete entity representation. Use this when the full entity data is needed in the response.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @OneToMany(mappedBy = "exercise")
 * @JsonSerialize(using = MultiModelSerializer.class)
 * @JsonProperty("exercise_injects")
 * private List<Inject> injects;
 * }</pre>
 *
 * @see MultiIdListSerializer
 */
public class MultiModelSerializer extends StdSerializer<List<Base>> {

  /** Default constructor required by Jackson. */
  public MultiModelSerializer() {
    this(null);
  }

  /**
   * Constructor with type specification.
   *
   * @param t the type class being serialized
   */
  public MultiModelSerializer(Class<List<Base>> t) {
    super(t);
  }

  /**
   * Serializes the entity list to a full JSON array representation.
   *
   * @param base the list of entities to serialize
   * @param jsonGenerator the JSON generator
   * @param serializerProvider the serializer provider
   * @throws IOException if an I/O error occurs during serialization
   */
  @Override
  public void serialize(
      List<Base> base, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
      throws IOException {
    jsonGenerator.writeObject(base);
  }
}
