package io.openaev.service.stix;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.openaev.database.model.IocValidation;
import io.openaev.database.model.IocValidationOutcome;
import io.openaev.database.model.IocValidationPair;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IOC validation result bundle")
class IocValidationResultBundleTest {

  private static final String REQUEST_ID = "5c7f0a2e-1111-4a2b-9c3d-123456789abc";
  private static final Instant EVALUATED_AT = Instant.parse("2026-10-03T10:00:00Z");
  // Same date handling as the Spring mapper that serializes the pushed bundle: ISO-8601 strings.
  private final ObjectMapper mapper =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private static IocValidationPair pair(String suffix, IocValidationOutcome outcome) {
    IocValidationPair pair = new IocValidationPair();
    pair.setIndicatorRef("indicator--00000000-0000-4000-8000-00000000000" + suffix);
    pair.setPlatformRef("identity--00000000-0000-4000-8000-00000000000" + suffix);
    pair.setDeployedOnRef("relationship--00000000-0000-4000-8000-00000000000" + suffix);
    pair.setOutcome(outcome);
    pair.setEvaluatedAt(EVALUATED_AT);
    return pair;
  }

  private IocValidation validation(IocValidationPair... pairs) {
    IocValidation validation = new IocValidation();
    validation.setExternalId(REQUEST_ID);
    validation.setPairs(new ArrayList<>(List.of(pairs)));
    return validation;
  }

  private List<JsonNode> objects(IocValidation validation) {
    JsonNode bundle = IocValidationResultBundle.build(validation, Instant.now()).toStix(mapper);
    assertThat(bundle.path("type").asText()).isEqualTo("bundle");
    return StreamSupport.stream(bundle.path("objects").spliterator(), false).toList();
  }

  private static List<JsonNode> ofType(List<JsonNode> objects, String type) {
    return objects.stream().filter(object -> type.equals(object.path("type").asText())).toList();
  }

  @Test
  @DisplayName("re-emits each deployed-on relationship under its OpenCTI id with the outcome")
  void given_pairs_should_upsertDeployedOnRelationships() {
    List<JsonNode> objects =
        objects(
            validation(
                pair("1", IocValidationOutcome.DETECTED), pair("2", IocValidationOutcome.MISSED)));

    List<JsonNode> relationships = ofType(objects, "relationship");
    assertThat(relationships).hasSize(2);
    JsonNode detected =
        relationships.stream()
            .filter(r -> r.path("id").asText().endsWith("1"))
            .findFirst()
            .orElseThrow();
    assertThat(detected.path("id").asText())
        .isEqualTo("relationship--00000000-0000-4000-8000-000000000001");
    assertThat(detected.path("relationship_type").asText()).isEqualTo("deployed-on");
    assertThat(detected.path("source_ref").asText())
        .isEqualTo("indicator--00000000-0000-4000-8000-000000000001");
    assertThat(detected.path("target_ref").asText())
        .isEqualTo("identity--00000000-0000-4000-8000-000000000001");
    assertThat(detected.path("validation_status").asText()).isEqualTo("detected");
    assertThat(detected.path("validation_run_id").asText()).isEqualTo(REQUEST_ID);
    assertThat(detected.path("last_validation_at").asText()).startsWith("2026-10-03T10:00:00");
  }

  @Test
  @DisplayName("records a sighting per evaluated pair, negative for misses, none for errors")
  void given_outcomes_should_emitSightingsOnlyForEvaluatedPairs() {
    List<JsonNode> objects =
        objects(
            validation(
                pair("1", IocValidationOutcome.PREVENTED),
                pair("2", IocValidationOutcome.MISSED),
                pair("3", IocValidationOutcome.ERROR)));

    List<JsonNode> sightings = ofType(objects, "sighting");
    assertThat(sightings).hasSize(2);
    JsonNode missed =
        sightings.stream()
            .filter(s -> s.path("sighting_of_ref").asText().endsWith("2"))
            .findFirst()
            .orElseThrow();
    assertThat(missed.path("x_opencti_negative").asBoolean()).isTrue();
    assertThat(missed.path("where_sighted_refs").get(0).asText())
        .isEqualTo("identity--00000000-0000-4000-8000-000000000002");
    assertThat(missed.path("count").asInt()).isEqualTo(1);
    JsonNode prevented =
        sightings.stream()
            .filter(s -> s.path("sighting_of_ref").asText().endsWith("1"))
            .findFirst()
            .orElseThrow();
    assertThat(prevented.path("x_opencti_negative").asBoolean()).isFalse();
    assertThat(ofType(objects, "relationship")).hasSize(3);
  }

  @Test
  @DisplayName("skips pairs without outcome and keeps sighting ids stable across pushes")
  void given_replayedPush_should_reuseSightingIds() {
    IocValidation validation =
        validation(pair("1", IocValidationOutcome.DETECTED), pair("2", null));

    List<JsonNode> first = objects(validation);
    List<JsonNode> second = objects(validation);

    assertThat(ofType(first, "relationship")).hasSize(1);
    assertThat(ofType(first, "sighting").getFirst().path("id").asText())
        .isEqualTo(ofType(second, "sighting").getFirst().path("id").asText())
        .startsWith("sighting--");
  }
}
