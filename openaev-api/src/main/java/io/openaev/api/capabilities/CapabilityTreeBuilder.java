package io.openaev.api.capabilities;

import io.openaev.database.model.Capability;
import io.openaev.database.model.CapabilityGroup;
import io.openaev.database.model.CapabilityScope;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds a tree of {@link CapabilityOutput} from the {@link Capability} enum. Trees are
 * pre-computed once at class-loading time because the source data is an enum (fully static).
 */
public final class CapabilityTreeBuilder {

  /** Pre-computed trees keyed by scope ({@code null} key = all scopes). */
  private static final Map<CapabilityScope, List<CapabilityOutput>> CACHE;

  static {
    CACHE = new EnumMap<>(CapabilityScope.class);
    for (CapabilityScope s : CapabilityScope.values()) {
      CACHE.put(s, Collections.unmodifiableList(computeTree(s)));
    }
  }

  private static final List<CapabilityOutput> ALL_SCOPES_CACHE =
      Collections.unmodifiableList(computeTree(null));

  private CapabilityTreeBuilder() {}

  /** Build the full capability tree (all scopes). */
  public static List<CapabilityOutput> buildTree() {
    return ALL_SCOPES_CACHE;
  }

  /** Build the capability tree filtered by scope — returns a cached, unmodifiable list. */
  public static List<CapabilityOutput> buildTree(CapabilityScope scope) {
    if (scope == null) {
      return ALL_SCOPES_CACHE;
    }
    return CACHE.get(scope);
  }

  /** Computes the tree (called once per scope at class-loading time). */
  private static List<CapabilityOutput> computeTree(CapabilityScope scope) {
    // Group children by parent capability
    Map<Capability, List<Capability>> childrenByParent =
        Arrays.stream(Capability.values())
            .filter(c -> c.getParent() != null)
            .filter(c -> !c.isHidden())
            .filter(c -> scope == null || c.getScopes().contains(scope))
            .collect(Collectors.groupingBy(Capability::getParent));

    // Roots are capabilities without parent
    List<Capability> roots =
        Arrays.stream(Capability.values())
            .filter(c -> c.getParent() == null)
            .filter(c -> !c.isHidden())
            .filter(c -> scope == null || c.getScopes().contains(scope))
            .toList();

    // Group roots by CapabilityGroup, preserving enum declaration order
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
