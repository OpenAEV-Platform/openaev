package io.openaev.api.chaining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.chaining.dto.StepOutput;
import io.openaev.database.model.Step;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("StepMapper - assets the reader cannot see")
class StepMapperTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private static Step templateWithAssets(String... assetIds) throws Exception {
    Step step = new Step();
    step.setId("step-1");
    step.setData(
        JSON.createObjectNode()
            .put("inject_title", "nmap")
            .set("inject_assets", JSON.valueToTree(List.of(assetIds)))
            .toString());
    return step;
  }

  @Test
  @DisplayName("should take the ids of the hidden assets out of inject_assets")
  void given_hiddenAssets_should_cutThemFromTheTemplateData() throws Exception {
    // Arrange
    Step step = templateWithAssets("green-asset", "red-asset");

    // Act
    StepOutput output = StepMapper.toOutput(step, Set.of("red-asset"));

    // Assert
    JsonNode assets = output.getData().get("inject_assets");
    assertEquals(1, assets.size());
    assertEquals("green-asset", assets.get(0).asText());
    assertEquals("nmap", output.getData().get("inject_title").asText(), "the rest is untouched");
    assertTrue(
        step.getData().contains("red-asset"), "the stored template keeps the hidden asset id");
  }

  @Test
  @DisplayName("should return the data as it is when nothing is hidden")
  void given_nothingHidden_should_notChangeTheData() throws Exception {
    // Arrange
    Step step = templateWithAssets("green-asset", "red-asset");

    // Act
    StepOutput output = StepMapper.toOutput(step);

    // Assert
    assertEquals(2, output.getData().get("inject_assets").size());
    assertEquals(2, StepMapper.toOutput(step, Set.of()).getData().get("inject_assets").size());
  }

  @Test
  @DisplayName("should list the asset ids a template carries, none when it carries no list")
  void given_template_should_listItsAssetIds() throws Exception {
    // Arrange
    Step withAssets = templateWithAssets("a", "b");
    Step without = new Step();
    without.setData("{\"inject_title\":\"x\"}");
    Step noData = new Step();

    // Act / Assert
    assertEquals(List.of("a", "b"), StepMapper.assetIdsOf(withAssets));
    assertEquals(List.of(), StepMapper.assetIdsOf(without));
    assertEquals(List.of(), StepMapper.assetIdsOf(noData));
  }
}
