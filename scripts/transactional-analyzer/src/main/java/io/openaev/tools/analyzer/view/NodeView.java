package io.openaev.tools.analyzer.view;

import java.util.List;

/**
 * View model for a single node in the caller tree.
 * All display attributes are pre-computed — the template is purely presentational.
 */
public final class NodeView {
  /** True when this is a cycle-detection sentinel. */
  public final boolean cyclic;
  /** True when this is a depth-limit sentinel. */
  public final boolean depthLimit;
  /** CSS class applied to the node container (tx-spring, no-tx, external, …). */
  public final String cssClass;
  /** Simple class name part (e.g. "UserService"). */
  public final String className;
  /** Method name (e.g. "findAll"). */
  public final String methodName;
  /** Full FQN of overridden/implemented type(s); null when none; comma-separated for multi. */
  public final String overridesFrom;
  /** Badges to display next to the method name. */
  public final List<BadgeView> badges;
  /** Relative file path; null for external nodes. */
  public final String filePath;
  /** Source line number; ≤ 0 when unknown. */
  public final int lineNumber;
  /** False for external / no-source nodes. */
  public final boolean hasSource;
  /** True when this node's method is an HTTP endpoint. */
  public final boolean isEndpoint;
  /** Child caller nodes (empty for leaves). */
  public final List<NodeView> children;

  public NodeView(boolean cyclic, boolean depthLimit, String cssClass,
      String className, String methodName, String overridesFrom,
      List<BadgeView> badges, String filePath, int lineNumber,
      boolean hasSource, boolean isEndpoint, List<NodeView> children) {
    this.cyclic = cyclic;
    this.depthLimit = depthLimit;
    this.cssClass = cssClass;
    this.className = className;
    this.methodName = methodName;
    this.overridesFrom = overridesFrom;
    this.badges = badges;
    this.filePath = filePath;
    this.lineNumber = lineNumber;
    this.hasSource = hasSource;
    this.isEndpoint = isEndpoint;
    this.children = children;
  }
}
