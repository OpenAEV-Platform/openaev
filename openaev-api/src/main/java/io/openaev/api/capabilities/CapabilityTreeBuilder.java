package io.openaev.api.capabilities;

import io.openaev.database.model.Capability;
import io.openaev.database.model.CapabilityGroup;
import io.openaev.database.model.CapabilityScope;
import io.openaev.rest.settings.PreviewFeature;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds a tree of {@link CapabilityOutput} from the {@link Capability} enum. Trees are
 * pre-computed once at class-loading time because the source data is an enum (fully static).
 */
public final class CapabilityTreeBuilder {

  /**
   * Capabilities that exist only while their preview feature is on. A capability left visible while
   * its feature is off advertises the feature on the role screen and lets an admin grant a
   * permission whose endpoints answer 404.
   */
  private static final Map<Capability, PreviewFeature> FEATURE_GATES =
      Map.of(
          Capability.ACCESS_CREDENTIALS, PreviewFeature.CREDENTIAL_ASSET,
          Capability.MANAGE_CREDENTIALS, PreviewFeature.CREDENTIAL_ASSET,
          Capability.DELETE_CREDENTIALS, PreviewFeature.CREDENTIAL_ASSET,
          Capability.ACCESS_SNAPSHOT_OBSERVATION, PreviewFeature.BULK_SNAPSHOT_EXPORT);

  private static final Set<PreviewFeature> ALL_GATES =
      Collections.unmodifiableSet(EnumSet.copyOf(FEATURE_GATES.values()));

  /** Pre-computed trees keyed by scope ({@code null} key = all scopes), every gate open. */
  private static final Map<CapabilityScope, List<CapabilityOutput>> CACHE;

  static {
    CACHE = new EnumMap<>(CapabilityScope.class);
    for (CapabilityScope s : CapabilityScope.values()) {
      CACHE.put(s, Collections.unmodifiableList(computeTree(s, ALL_GATES)));
    }
  }

  private static final List<CapabilityOutput> ALL_SCOPES_CACHE =
      Collections.unmodifiableList(computeTree(null, ALL_GATES));

  private CapabilityTreeBuilder() {}

  /** The preview features that gate at least one capability. */
  public static Set<PreviewFeature> featureGates() {
    return ALL_GATES;
  }

  /** Build the full capability tree (all scopes), every gate open. */
  public static List<CapabilityOutput> buildTree() {
    return ALL_SCOPES_CACHE;
  }

  /** Build the capability tree filtered by scope, every gate open. */
  public static List<CapabilityOutput> buildTree(CapabilityScope scope) {
    return buildTree(scope, ALL_GATES);
  }

  /**
   * Build the capability tree filtered by scope, hiding the capabilities whose preview feature is
   * absent from {@code enabledFeatures}.
   */
  public static List<CapabilityOutput> buildTree(
      CapabilityScope scope, Set<PreviewFeature> enabledFeatures) {
    if (!enabledFeatures.containsAll(ALL_GATES)) {
      return computeTree(scope, enabledFeatures);
    }
    if (scope == null) {
      return ALL_SCOPES_CACHE;
    }
    return CACHE.get(scope);
  }

  private static boolean isEnabled(Capability capability, Set<PreviewFeature> enabledFeatures) {
    PreviewFeature gate = FEATURE_GATES.get(capability);
    return gate == null || enabledFeatures.contains(gate);
  }

  /** Computes the tree (called once per scope at class-loading time). */
  private static List<CapabilityOutput> computeTree(
      CapabilityScope scope, Set<PreviewFeature> enabledFeatures) {
    // Group children by parent capability
    Map<Capability, List<Capability>> childrenByParent =
        Arrays.stream(Capability.values())
            .filter(c -> c.getParent() != null)
            .filter(c -> !c.isHidden())
            .filter(c -> isEnabled(c, enabledFeatures))
            .filter(c -> scope == null || c.getScopes().contains(scope))
            .collect(Collectors.groupingBy(Capability::getParent));

    // Roots are capabilities without parent
    List<Capability> roots =
        Arrays.stream(Capability.values())
            .filter(c -> c.getParent() == null)
            .filter(c -> !c.isHidden())
            .filter(c -> isEnabled(c, enabledFeatures))
            .filter(c -> scope == null || c.getScopes().contains(scope))
            // Stable sort on the group: keeps the CapabilityGroup declaration order between
            // groups, and the Capability declaration order inside a group.
            .sorted(Comparator.comparing(Capability::getGroup))
            .toList();

    // Group roots by CapabilityGroup, preserving CapabilityGroup declaration order
    Map<CapabilityGroup, List<Capability>> rootsByGroup = new LinkedHashMap<>();
    for (Capability root : roots) {
      rootsByGroup.computeIfAbsent(root.getGroup(), k -> new ArrayList<>()).add(root);
    }

    // Build category nodes wrapping root capabilities, BYPASS stands alone
    List<CapabilityOutput> result = new ArrayList<>();

    for (var entry : rootsByGroup.entrySet()) {
      CapabilityGroup group = entry.getKey();
      List<Capability> groupRoots = entry.getValue();

      if (group == CapabilityGroup.SUPERUSER) {
        // BYPASS is special: no category wrapper, directly checkable
        result.add(toNode(groupRoots.getFirst(), childrenByParent));
      } else {
        List<CapabilityOutput> children =
            groupRoots.stream().map(root -> toNode(root, childrenByParent)).toList();
        result.add(CapabilityMapper.toOutput(group, groupRoots, children));
      }
    }

    return result;
  }

  private static CapabilityOutput toNode(
      Capability cap, Map<Capability, List<Capability>> childrenByParent) {
    List<CapabilityOutput> children =
        childrenByParent.getOrDefault(cap, List.of()).stream()
            .map(child -> toNode(child, childrenByParent))
            .toList();

    return CapabilityMapper.toOutput(cap, children);
  }
}
