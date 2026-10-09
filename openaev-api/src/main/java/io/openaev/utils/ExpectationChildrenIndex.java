package io.openaev.utils;

import static io.openaev.utils.ExpectationUtils.isAgentExpectation;
import static io.openaev.utils.ExpectationUtils.isAssetExpectation;
import static io.openaev.utils.ExpectationUtils.isAssetGroupExpectation;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE;
import io.openaev.database.model.Inject;
import io.openaev.database.model.TechnicalInjectExpectation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The children a parent expectation's score rolls up from, resolved like the score propagation
 * does: the agent expectations of an asset expectation ({@link
 * ExpectationUtils#getAgentsExpectationsForAsset}) and the asset expectations of an asset group
 * expectation ({@link ExpectationUtils#getExpectationsAssetsForAssetGroup}). Agent expectations and
 * agentless rows have none.
 *
 * <p>The expectations of an inject are indexed in one pass the first time one of its expectations
 * is resolved, so attributing every expectation of a simulation to every security platform never
 * scans an inject's expectations more than once. An index is meant for one computation over loaded
 * expectations: it does not see expectations added to an inject after it indexed that inject.
 */
public final class ExpectationChildrenIndex {

  private record ParentKey(String parentId, EXPECTATION_TYPE type) {}

  private record InjectChildren(
      Map<ParentKey, List<TechnicalInjectExpectation>> agentsByAsset,
      Map<ParentKey, List<TechnicalInjectExpectation>> assetsByAssetGroup) {}

  private final Map<Inject, InjectChildren> childrenByInject = new IdentityHashMap<>();

  /**
   * Returns the children of the expectation, in the order of its inject's expectations.
   *
   * @param expectation the parent expectation
   * @return the agent expectations of an asset expectation, the asset expectations of an asset
   *     group expectation, otherwise an empty list
   */
  public List<TechnicalInjectExpectation> childrenOf(final BaseInjectExpectation expectation) {
    if (!(expectation instanceof TechnicalInjectExpectation technical)
        || technical.getInject() == null
        || isAgentExpectation(technical)) {
      return List.of();
    }
    InjectChildren children =
        childrenByInject.computeIfAbsent(technical.getInject(), ExpectationChildrenIndex::index);
    if (isAssetGroupExpectation(technical)) {
      return children
          .assetsByAssetGroup()
          .getOrDefault(
              new ParentKey(technical.getAssetGroup().getId(), technical.getType()), List.of());
    }
    if (isAssetExpectation(technical)) {
      return children
          .agentsByAsset()
          .getOrDefault(
              new ParentKey(technical.getAsset().getId(), technical.getType()), List.of());
    }
    return List.of();
  }

  private static InjectChildren index(final Inject inject) {
    Map<ParentKey, List<TechnicalInjectExpectation>> agentsByAsset = new HashMap<>();
    Map<ParentKey, List<TechnicalInjectExpectation>> assetsByAssetGroup = new HashMap<>();
    for (BaseInjectExpectation expectation : inject.getExpectations()) {
      if (!(expectation instanceof TechnicalInjectExpectation technical)) {
        continue;
      }
      if (isAgentExpectation(technical) && technical.getAsset() != null) {
        agentsByAsset
            .computeIfAbsent(
                new ParentKey(technical.getAsset().getId(), technical.getType()),
                key -> new ArrayList<>())
            .add(technical);
      } else if (isAssetExpectation(technical) && technical.getAssetGroup() != null) {
        assetsByAssetGroup
            .computeIfAbsent(
                new ParentKey(technical.getAssetGroup().getId(), technical.getType()),
                key -> new ArrayList<>())
            .add(technical);
      }
    }
    return new InjectChildren(agentsByAsset, assetsByAssetGroup);
  }
}
