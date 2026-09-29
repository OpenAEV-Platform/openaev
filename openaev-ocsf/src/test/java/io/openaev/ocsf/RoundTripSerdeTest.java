package io.openaev.ocsf;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.openaev.ocsf.schema.v190.OcsfFilter;
import io.openaev.ocsf.schema.v190.classes.OcsfClassDetectionFinding;
import io.openaev.utils.fixtures.ProwlerOcsfOutputFixture;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class RoundTripSerdeTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  @DisplayName("Deserialised OCSF class object is serialised back correctly")
  void deserialisedOcsfClassObject_isSerialisedBackCorrectly() throws IOException {
    List<OcsfClassDetectionFinding> findings =
        new OcsfFilter()
            .filterOcsfClassDetectionFindings(ProwlerOcsfOutputFixture.getProwlerOcsfOutputTrace());
    ArrayNode serialised = mapper.createArrayNode();
    for (OcsfClassDetectionFinding df : findings) {
      serialised.add(df.toOcsf(mapper));
    }

    assertThatJson(serialised).isEqualTo(ProwlerOcsfOutputFixture.getProwlerOcsfOutputTrace());
  }
}
