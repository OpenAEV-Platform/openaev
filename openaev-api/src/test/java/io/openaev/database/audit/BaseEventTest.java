package io.openaev.database.audit;

import static io.openaev.database.audit.ModelBaseListener.DATA_DELETE;
import static io.openaev.database.audit.ModelBaseListener.DATA_UPDATE;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetCategory;
import io.openaev.database.model.Base;
import io.openaev.database.model.Command;
import io.openaev.database.model.PreventionInjectExpectation;
import io.openaev.helper.ObjectMapperHelper;
import io.openaev.utils.fixtures.AssetFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.SecurityPlatformFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// The frontend stores each streamed entity under the event's attribute_schema, and reads assets
// from one bucket per subtype (actions/assets/asset-schema.ts): an event sent under any other
// schema lands in a bucket no screen reads.
class BaseEventTest {

  private final ObjectMapper mapper = ObjectMapperHelper.openAEVJsonMapper();

  private BaseEvent eventFor(String eventType, Base entity) {
    entity.setId("entity-id");
    return new BaseEvent(eventType, entity, mapper);
  }

  @Nested
  @DisplayName("Asset events use the frontend bucket of their subtype")
  class AssetSubtypes {

    @ParameterizedTest
    @ValueSource(strings = {DATA_UPDATE, DATA_DELETE})
    @DisplayName("An endpoint is streamed under endpoints")
    void given_endpoint_should_streamUnderEndpoints(String eventType) {
      // Arrange
      Base endpoint = EndpointFixture.createEndpoint();

      // Act
      BaseEvent event = eventFor(eventType, endpoint);

      // Assert
      assertThat(event.getSchema()).isEqualTo("endpoints");
      assertThat(event.getAttributeId()).isEqualTo("asset_id");
    }

    @ParameterizedTest
    @ValueSource(strings = {DATA_UPDATE, DATA_DELETE})
    @DisplayName("A security platform is streamed under securityplatforms")
    void given_securityPlatform_should_streamUnderSecurityPlatforms(String eventType) {
      // Arrange
      Base securityPlatform = SecurityPlatformFixture.createDefault("EDR", "EDR");

      // Act
      BaseEvent event = eventFor(eventType, securityPlatform);

      // Assert
      assertThat(event.getSchema()).isEqualTo("securityplatforms");
      assertThat(event.getAttributeId()).isEqualTo("asset_id");
    }

    @ParameterizedTest
    @ValueSource(strings = {DATA_UPDATE, DATA_DELETE})
    @DisplayName("An AI target is streamed under aitargets")
    void given_aiTarget_should_streamUnderAiTargets(String eventType) {
      // Arrange
      Asset aiTarget = AssetFixture.createDefaultAsset("LLM");
      aiTarget.setCategory(AssetCategory.AI_TARGET);

      // Act
      BaseEvent event = eventFor(eventType, aiTarget);

      // Assert
      assertThat(event.getSchema()).isEqualTo("aitargets");
      assertThat(event.getAttributeId()).isEqualTo("asset_id");
    }

    @ParameterizedTest
    @ValueSource(strings = {DATA_UPDATE, DATA_DELETE})
    @DisplayName("Any other asset keeps the assets schema")
    void given_otherAsset_should_streamUnderAssets(String eventType) {
      // Arrange
      Asset cloudResource = AssetFixture.createDefaultAsset("Bucket");
      cloudResource.setCategory(AssetCategory.CLOUD_RESOURCE);

      // Act
      BaseEvent event = eventFor(eventType, cloudResource);

      // Assert
      assertThat(event.getSchema()).isEqualTo("assets");
    }

    @ParameterizedTest
    @ValueSource(strings = {DATA_UPDATE, DATA_DELETE})
    @DisplayName("The stream schema is not serialized in the payload")
    void given_endpoint_should_notSerializeTheStreamSchema(String eventType) {
      // Arrange
      Base endpoint = EndpointFixture.createEndpoint();

      // Act
      BaseEvent event = eventFor(eventType, endpoint);

      // Assert
      assertThat(event.getInstanceData().toString()).doesNotContain("endpoints");
    }
  }

  @Nested
  @DisplayName("Other hierarchies keep one bucket for every subtype")
  class OtherHierarchies {

    @ParameterizedTest
    @ValueSource(strings = {DATA_UPDATE, DATA_DELETE})
    @DisplayName("An expectation subtype is streamed under injectexpectations")
    void given_expectationSubtype_should_streamUnderInjectExpectations(String eventType) {
      // Act
      BaseEvent event = eventFor(eventType, new PreventionInjectExpectation());

      // Assert
      assertThat(event.getSchema()).isEqualTo("injectexpectations");
    }

    @ParameterizedTest
    @ValueSource(strings = {DATA_UPDATE, DATA_DELETE})
    @DisplayName("A payload subtype is streamed under payloads")
    void given_payloadSubtype_should_streamUnderPayloads(String eventType) {
      // Act
      BaseEvent event = eventFor(eventType, new Command());

      // Assert
      assertThat(event.getSchema()).isEqualTo("payloads");
    }
  }
}
