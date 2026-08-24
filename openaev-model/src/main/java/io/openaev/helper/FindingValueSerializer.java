package io.openaev.helper;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.openaev.database.model.Finding;
import java.io.IOException;

/**
 * Serializes {@code finding_value} through {@link FindingValueRedactor}. The sensitivity flag lives
 * on the enclosing {@link Finding}, which Jackson exposes as the generator current value while the
 * bean fields are written, so the property keeps its {@code @JsonProperty}/{@code @Queryable}
 * declaration (filters and sorts still resolve on {@code finding_value}) while the emitted content
 * is redacted.
 */
public class FindingValueSerializer extends JsonSerializer<String> {

  @Override
  public void serialize(String value, JsonGenerator gen, SerializerProvider serializers)
      throws IOException {
    boolean sensitive = gen.getCurrentValue() instanceof Finding finding && finding.isSensitive();
    gen.writeString(FindingValueRedactor.redact(value, sensitive));
  }
}
