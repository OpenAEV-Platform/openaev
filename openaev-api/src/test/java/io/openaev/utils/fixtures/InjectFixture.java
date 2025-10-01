package io.openaev.utils.fixtures;

import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import io.openaev.injectors.challenge.model.ChallengeContent;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class InjectFixture {

  public static final String INJECT_EMAIL_NAME = "Test email inject";
  public static final String INJECT_CHALLENGE_NAME = "Test challenge inject";

  private static Inject createInject(InjectorContract injectorContract, String title) {
    Inject inject = createInjectWithTitle(title);
    inject.setInjectorContract(injectorContract);
    inject.setEnabled(true);
    inject.setDependsDuration(0L);

    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode injectContent = objectMapper.createObjectNode();
    injectContent.set(
        CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS,
        objectMapper.convertValue(
            List.of(
                ExpectationFixture.createExpectation(InjectExpectation.EXPECTATION_TYPE.MANUAL)),
            ArrayNode.class));
    inject.setContent(injectContent);

    return inject;
  }

  public static Inject createTechnicalInject(
      InjectorContract injectorContract, String title, Asset asset) {
    Inject inject = new Inject();
    inject.setTitle(title);
    inject.setInjectorContract(injectorContract);
    inject.setAssets(List.of(asset));
    inject.setEnabled(true);
    inject.setDependsDuration(0L);
    return inject;
  }

  public static Inject createTechnicalInjectWithAssetGroup(
      InjectorContract injectorContract, String title, AssetGroup assetGroup) {
    Inject inject = new Inject();
    inject.setTitle(title);
    inject.setInjectorContract(injectorContract);
    inject.setAssetGroups(List.of(assetGroup));
    inject.setEnabled(true);
    inject.setDependsDuration(0L);
    return inject;
  }

  public static Inject getInjectWithoutContract() {
    Inject inject = createInjectWithTitle(INJECT_EMAIL_NAME);
    inject.setEnabled(true);
    inject.setDependsDuration(0L);
    return inject;
  }

  public static Inject getInjectWithAllTeams() {
    Inject inject = createInjectWithTitle(INJECT_EMAIL_NAME);
    inject.setEnabled(true);
    inject.setDependsDuration(0L);
    inject.setAllTeams(true);
    return inject;
  }

  public static Inject getDefaultInject() {
    Inject inject = createInjectWithDefaultTitle();
    inject.setEnabled(true);
    inject.setDependsDuration(0L);
    return inject;
  }

  public static Inject getDefaultInjectWithDuration(long duration) {
    Inject inject = createInjectWithDefaultTitle();
    inject.setEnabled(true);
    inject.setDependsDuration(duration);
    return inject;
  }

  public static Inject getInjectForEmailContract(InjectorContract injectorContract) {
    return createInject(injectorContract, INJECT_EMAIL_NAME);
  }

  public static Inject createDefaultInjectChallenge(
      InjectorContract injectorContract, ObjectMapper objectMapper, List<String> challengeIds) {
    Inject inject = createInject(injectorContract, INJECT_CHALLENGE_NAME);

    ChallengeContent content = new ChallengeContent();
    content.setChallenges(challengeIds);
    inject.setContent(objectMapper.valueToTree(content));
    return inject;
  }

  public static Inject createInjectCommandPayload(
      InjectorContract injectorContract, Map<String, Object> payloadArguments) {

    Inject inject = createInject(injectorContract, "Inject title");
    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode injectContent = objectMapper.createObjectNode();
    payloadArguments.forEach(
        (key, value) -> injectContent.set(key, objectMapper.convertValue(value, JsonNode.class)));

    payloadArguments.forEach(
        (key, value) -> {
          if (value instanceof String) {
            injectContent.set(key, objectMapper.convertValue(value, JsonNode.class));
          } else if (value instanceof List) {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            (((List<?>) value).stream().toList()).forEach(item -> arrayNode.add(item.toString()));
            injectContent.set(key, arrayNode);
          } else {
            throw new IllegalArgumentException("Unsupported type for key: " + key);
          }
        });

    inject.setContent(injectContent);

    return inject;
  }

  private static Inject createInjectWithDefaultTitle() {
    return createInjectWithTitle(null);
  }

  private static Inject createInjectWithTitle(String title) {
    String new_title = title == null ? "inject-%s".formatted(UUID.randomUUID()) : title;
    Inject inject = new Inject();
    inject.setTitle(new_title);
    return inject;
  }
}
