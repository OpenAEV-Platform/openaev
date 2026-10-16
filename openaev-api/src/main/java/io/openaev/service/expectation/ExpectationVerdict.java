package io.openaev.service.expectation;

import static io.openaev.service.InjectExpectationService.COLLECTOR;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.getSourceAssetId;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.getSourcePlatform;

import io.openaev.database.model.Collector;
import io.openaev.rest.exercise.form.InjectExpectationUpdateFromUIInput;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

/**
 * Result written on the leaves of an expectation by an {@link ExpectationBehavior}: who answered
 * (the source) and with which score. It is the single input of the behaviors, whatever the entry
 * point (UI, challenge, media pressure, expiration), so the API inputs stay free to evolve with
 * their own clients.
 *
 * @param sourceId the ID of the source, the upsert key of the result on a leaf
 * @param sourceType the kind of source (collector, challenge, media pressure...)
 * @param sourceName the display name of the source
 * @param sourcePlatform the security platform type of the source, if any
 * @param sourceAssetId the asset the source stands for, if any
 * @param score the score given by the source
 */
@Builder
public record ExpectationVerdict(
    @NotBlank String sourceId,
    @NotBlank String sourceType,
    @NotBlank String sourceName,
    @Nullable String sourcePlatform,
    @Nullable String sourceAssetId,
    @NotNull Double score) {

  /** Verdict given from the UI. */
  public static ExpectationVerdict fromUI(InjectExpectationUpdateFromUIInput input) {
    return ExpectationVerdict.builder()
        .sourceId(input.getSourceId())
        .sourceType(input.getSourceType())
        .sourceName(input.getSourceName())
        .sourcePlatform(input.getSourcePlatform())
        .sourceAssetId(input.getSourceId())
        .score(input.getScore())
        .build();
  }

  /** Verdict attributed to a collector (e.g. the expiration manager). */
  public static ExpectationVerdict fromCollector(Collector collector, Double score) {
    return ExpectationVerdict.builder()
        .sourceId(collector.getId())
        .sourceType(COLLECTOR)
        .sourceName(collector.getName())
        .sourcePlatform(getSourcePlatform(collector))
        .sourceAssetId(getSourceAssetId(collector))
        .score(score)
        .build();
  }
}
