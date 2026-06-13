package io.openaev.tools.analyzer.view;

import java.util.List;

/** View model for a single @Repository method with its full caller tree. */
public final class MethodTreeView {
  /** Unique HTML id for anchor links. */
  public final String id;
  /** Value for data-tx attribute (green / red / grey). */
  public final String txData;
  /** Value for data-ep attribute (blue / violet / grey). */
  public final String epData;
  /** Comma-separated annotation CSS class values for data-ann attribute. */
  public final String annData;
  /** Comma-separated override FQNs for data-overrides attribute. */
  public final String overridesData;
  /** True when the method has no callers in indexed source. */
  public final boolean neverCalled;
  /** CSS class for the ① TX coverage dot. */
  public final String txCssClass;
  /** Tooltip for the TX coverage dot. */
  public final String txTooltip;
  /** CSS class for the ② endpoint origin dot. */
  public final String epCssClass;
  /** Tooltip for the endpoint origin dot. */
  public final String epTooltip;
  /** Method name (without parentheses). */
  public final String methodName;
  /** Parameter summary, e.g. "(String, Long)". */
  public final String paramSummary;
  /** Badges shown in the tree summary header. */
  public final List<BadgeView> headerBadges;
  /** Override FQNs of the root @Repository method itself (comma-separated, may be empty). */
  public final String rootOverridesFrom;
  /** Direct caller nodes (first level below the @Repository root). */
  public final List<NodeView> callers;

  public MethodTreeView(String id, String txData, String epData,
      String annData, String overridesData, boolean neverCalled,
      String txCssClass, String txTooltip, String epCssClass, String epTooltip,
      String methodName, String paramSummary,
      List<BadgeView> headerBadges, String rootOverridesFrom, List<NodeView> callers) {
    this.id = id;
    this.txData = txData;
    this.epData = epData;
    this.annData = annData;
    this.overridesData = overridesData;
    this.neverCalled = neverCalled;
    this.txCssClass = txCssClass;
    this.txTooltip = txTooltip;
    this.epCssClass = epCssClass;
    this.epTooltip = epTooltip;
    this.methodName = methodName;
    this.paramSummary = paramSummary;
    this.headerBadges = headerBadges;
    this.rootOverridesFrom = rootOverridesFrom;
    this.callers = callers;
  }
}
