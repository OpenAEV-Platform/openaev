package io.openaev.helper;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.openaev.database.model.Base;
import java.io.IOException;
import java.util.List;

/**
 * Custom JSON serializer that serializes a {@link List} of {@link Base} entities to a JSON array of
 * their ID strings.
 *
 * <p>This serializer is useful for collection relationship fields where only entity IDs need to be
 * included in the JSON output, reducing payload size and avoiding circular references.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @OneToMany(mappedBy = "exercise")
 * @JsonSerialize(using = MultiIdListSerializer.class)
 * @JsonProperty("exercise_teams")
 * private List<Team> teams;
 * }</pre>
 *
 * <p>Output: {@code ["id1", "id2", "id3"]}
 *
 * @see MonoIdSerializer
 * @see MultiIdSetSerializer
 */
public class MultiIdListSerializer extends StdSerializer<List<Base>> {

  /** Default constructor required by Jackson. */
  public MultiIdListSerializer() {
    this(null);
  }

  /**
   * Constructor with type specification.
   *
   * @param t the type class being serialized
   */
  public MultiIdListSerializer(Class<List<Base>> t) {
    super(t);
  }

  /**
   * Serializes the entity list to a JSON array of ID strings.
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
    List<String> ids = base.stream().map(Base::getId).toList();
    String[] arrayIds = ids.toArray(new String[0]);
    jsonGenerator.writeArray(arrayIds, 0, arrayIds.length);
  }
}
