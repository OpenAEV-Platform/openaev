package io.openaev.helper;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.openaev.database.model.Base;
import java.io.IOException;
import java.util.List;

public class MultiModelDeserializer extends StdSerializer<List<Base>> {

  public MultiModelDeserializer() {
    this(null);
  }

  public MultiModelDeserializer(Class<List<Base>> t) {
    super(t);
  }

  @Override
  public void serialize(
      List<Base> base, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
      throws IOException {
    jsonGenerator.writeObject(base);
  }
}
