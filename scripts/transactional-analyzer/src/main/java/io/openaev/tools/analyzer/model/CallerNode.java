package io.openaev.tools.analyzer.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A node in the upward caller tree.
 *
 * <p>The root of the tree is a @Repository method. Each node's {@code callers} list contains
 * the methods that call {@code method}. Leaves are top-level callers (controllers, schedulers,
 * command runners, …) that are not themselves called by any indexed code.
 *
 * <p>When a method that already appears on the current ancestor path is encountered again (a
 * cycle), {@code isCyclic} is set to {@code true} and the node has no children.
 *
 * <p>When the tree depth limit is exceeded, a sentinel node with {@code isDepthLimitExceeded =
 * true} is inserted to signal that the tree was truncated.
 */
public class CallerNode {

  public final MethodInfo method;
  public final List<CallerNode> callers = new ArrayList<>();

  /** True when this node was already on the current ancestor path — signals a cycle. */
  public final boolean isCyclic;

  /** True when this placeholder was inserted because the configured depth limit was reached. */
  public final boolean isDepthLimitExceeded;

  public CallerNode(MethodInfo method, boolean isCyclic, boolean isDepthLimitExceeded) {
    this.method = method;
    this.isCyclic = isCyclic;
    this.isDepthLimitExceeded = isDepthLimitExceeded;
  }

  /** Normal (non-cycle, non-truncated) node. */
  public CallerNode(MethodInfo method) {
    this(method, false, false);
  }

  /** True for leaf nodes that have no callers in the indexed codebase. */
  public boolean isLeaf() {
    return !isCyclic && !isDepthLimitExceeded && callers.isEmpty();
  }

  /**
   * Returns true when at least one node in this subtree (inclusive) carries @Transactional. Used
   * for colouring the "safe path" indicator in the HTML report.
   */
  public boolean hasTransactionalInSubtree() {
    if (method.isTransactional()) return true;
    for (CallerNode c : callers) {
      if (c.hasTransactionalInSubtree()) return true;
    }
    return false;
  }
}
