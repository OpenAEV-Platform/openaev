package io.openaev.helper;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.openaev.database.model.Base;
import java.io.IOException;

/**
 * Custom JSON serializer that serializes a Base entity to just its ID string. This is useful for
 * relationships where only the ID needs to be included in the JSON output.
 */
public class MonoIdSerializer extends StdSerializer<Base> {

  public MonoIdSerializer() {
    this(null);
  }

  public MonoIdSerializer(Class<Base> t) {
    super(t);
  }

  @Override
  public void serialize(
      Base base, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
      throws IOException {
    jsonGenerator.writeString(base.getId());
  }
}
