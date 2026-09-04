package io.openaev.ocsf.parser;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.ocsf.parser.generator.emission.ClassMetadata;
import io.openaev.ocsf.parser.generator.emission.DatatypeClassGenerator;
import io.openaev.ocsf.parser.schema.SchemaDimension;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

public class DatatypeClassGeneratorTest {
  private final ObjectMapper mapper = new ObjectMapper();

  private JsonNode readResourceFile(String path) throws IOException {
    String rawJson =
        new String(
            Objects.requireNonNull(getClass().getResourceAsStream(path)).readAllBytes(),
            StandardCharsets.UTF_8);
    return mapper.readTree(rawJson);
  }

  @Test
  void emitsCorrectly() throws IOException {
    ClassMetadata md =
        new ClassMetadata(
            "datetime_t",
            null,
            SchemaDimension.DATATYPES,
            null,
            "OcsfDatatypeTestDatatypeT",
            "io.openaev.test",
            "io.openaev.schema",
            readResourceFile("/ocsf/datetime_t.json"));

    DatatypeClassGenerator gen = new DatatypeClassGenerator();
    String classContents = gen.emit(md, null);

    assertThat(classContents)
        .isEqualTo(
            """
                package io.openaev.test;

                import io.openaev.ocsf.schema.OcsfDatatype;


                public class OcsfDatatypeDatetimeT extends OcsfDatatype<java.lang.String> {

                  public OcsfDatatypeDatetimeT (java.lang.String value) {
                    super(value);
                  }

                  @java.lang.Override
                  protected boolean validate() {
                    return getValue().matches("^\\\\d{4}-\\\\d{2}-\\\\d{2}[Tt]\\\\d{2}:\\\\d{2}:\\\\d{2}(?:\\\\.\\\\d+)?([Zz]|[\\\\+-]\\\\d{2}:\\\\d{2})?$");
                  }
                }
                """);
  }
}
