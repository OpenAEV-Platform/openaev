package io.openaev.database.specification;

import io.openaev.database.model.InjectExpectation;
import io.openaev.database.model.InjectExpectation.EXPECTATION_TYPE;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public class InjectExpectationSpecification {

  public static Specification<InjectExpectation> type(@NotBlank final EXPECTATION_TYPE type) {
    return (root, query, cb) -> cb.equal(root.get("type"), type);
  }

  public static Specification<InjectExpectation> assetGroupIsNull() {
    return (root, query, cb) -> cb.isNull(root.get("assetGroup"));
  }

  public static Specification<InjectExpectation> fromAssetGroup(
      @Nullable final String assetGroupId) {
    return (root, query, cb) -> cb.equal(root.get("assetGroup").get("id"), assetGroupId);
  }

  public static Specification<InjectExpectation> from(@NotBlank final Instant date) {
    return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), date);
  }

  public static Specification<InjectExpectation> agentNotNull() {
    return (root, query, cb) -> cb.isNotNull(root.get("agent"));
  }

  public static Specification<InjectExpectation> assetNotNull() {
    return (root, query, cb) -> cb.isNotNull(root.get("asset"));
  }

  public static Specification<InjectExpectation> fromAgents(
      @NotBlank final String injectId, @NotEmpty final List<String> agentIds) {
    return (root, query, cb) ->
        cb.and(
            cb.equal(root.get("inject").get("id"), injectId),
            root.get("agent").get("id").in(agentIds));
  }

  public static Specification<InjectExpectation> fromAssets(
      @NotBlank final String injectId, @NotEmpty final List<String> assetIds) {
    return (root, query, cb) ->
        cb.and(
            cb.equal(root.get("inject").get("id"), injectId),
            cb.isNull(root.get("agent")),
            root.get("asset").get("id").in(assetIds));
  }
}
