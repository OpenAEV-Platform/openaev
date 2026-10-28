package io.openaev.utils.fixtures;

import io.openaev.database.model.Asset;
import io.openaev.rest.asset.ai_targets.form.AiTargetInput;
import java.util.UUID;

public class AiTargetFixture {

  /** A valid AI target input, with a unique name built on {@code name}. */
  public static AiTargetInput createDefaultAiTargetInput(String name) {
    AiTargetInput input = new AiTargetInput();
    input.setName(name + "-" + UUID.randomUUID());
    input.setAiTargetProvider(Asset.AI_TARGET_PROVIDER.OPENAI_COMPATIBLE);
    input.setAiTargetModality(Asset.AI_TARGET_MODALITY.TEXT);
    return input;
  }
}
