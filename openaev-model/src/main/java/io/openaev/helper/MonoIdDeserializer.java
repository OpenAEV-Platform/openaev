package io.openaev.helper;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.openaev.database.model.Base;
import java.io.IOException;

public class MonoIdDeserializer extends StdSerializer<Base> {

  public MonoIdDeserializer() {
    this(null);
  }

  public MonoIdDeserializer(Class<Base> t) {
    super(t);
  }

  @Override
  public void serialize(
      Base base, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
      throws IOException {
    jsonGenerator.writeString(base.getId());
  }
}
