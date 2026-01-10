package io.openaev.helper;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.openaev.database.model.Collector;
import java.io.IOException;

/** Custom JSON serializer that serializes a Collector entity to just its type string. */
public class CollectorTypeSerializer extends JsonSerializer<Collector> {

  @Override
  public void serialize(
      Collector value, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
      throws IOException {
    jsonGenerator.writeString(value.getType());
  }
}
