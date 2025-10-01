package io.openaev.helper;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.openaev.database.model.Base;
import java.io.IOException;
import java.util.List;
import java.util.Set;

public class MultiIdSetDeserializer extends StdSerializer<Set<Base>> {

  public MultiIdSetDeserializer() {
    this(null);
  }

  public MultiIdSetDeserializer(Class<Set<Base>> t) {
    super(t);
  }

  @Override
  public void serialize(
      Set<Base> base, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
      throws IOException {
    List<String> ids = base.stream().map(Base::getId).toList();
    String[] arrayIds = ids.toArray(new String[0]);
    jsonGenerator.writeArray(arrayIds, 0, arrayIds.length);
  }
}
