package io.openaev.database.specification;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public class InjectExpectationSpecification {

  public static Specification<BaseInjectExpectation> type(@NotBlank final EXPECTATION_TYPE type) {
    return (root, query, cb) -> cb.equal(root.get("type"), type);
  }

  public static Specification<BaseInjectExpectation> assetGroupIsNull() {
    return (root, query, cb) -> cb.isNull(root.get("assetGroup"));
  }

  public static Specification<BaseInjectExpectation> fromAssetGroup(
      @Nullable final String assetGroupId) {
    return (root, query, cb) -> cb.equal(root.get("assetGroup").get("id"), assetGroupId);
  }

  public static Specification<BaseInjectExpectation> from(@NotBlank final Instant date) {
    return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), date);
  }

  public static Specification<BaseInjectExpectation> agentNotNull() {
    return (root, query, cb) -> cb.isNotNull(root.get("agent"));
  }

  public static Specification<BaseInjectExpectation> assetNotNull() {
    return (root, query, cb) -> cb.isNotNull(root.get("asset"));
  }

  public static Specification<BaseInjectExpectation> fromAgents(
      @NotBlank final String injectId, @NotEmpty final List<String> agentIds) {
    return (root, query, cb) ->
        cb.and(
            cb.equal(root.get("inject").get("id"), injectId),
            root.get("agent").get("id").in(agentIds));
  }

  public static Specification<BaseInjectExpectation> fromAssets(
      @NotBlank final String injectId, @NotEmpty final List<String> assetIds) {
    return (root, query, cb) ->
        cb.and(
            cb.equal(root.get("inject").get("id"), injectId),
            cb.isNull(root.get("agent")),
            root.get("asset").get("id").in(assetIds));
  }
}
