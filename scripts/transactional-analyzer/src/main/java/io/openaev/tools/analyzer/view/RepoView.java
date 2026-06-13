package io.openaev.tools.analyzer.view;

import java.util.List;

/** View model for a single @Repository type and all its method trees. */
public final class RepoView {
  /** Unique HTML id for anchor links. */
  public final String id;
  /** Simple class name (e.g. "UserRepository"). */
  public final String simpleName;
  /** Relative file path. */
  public final String filePath;
  /** CSS class for the aggregated TX coverage dot. */
  public final String txCssClass;
  /** Tooltip for the TX coverage dot. */
  public final String txTooltip;
  /** CSS class for the aggregated endpoint origin dot. */
  public final String epCssClass;
  /** Tooltip for the endpoint origin dot. */
  public final String epTooltip;
  /** All method trees for this repository, in declaration order. */
  public final List<MethodTreeView> methods;

  public RepoView(String id, String simpleName, String filePath,
      String txCssClass, String txTooltip, String epCssClass, String epTooltip,
      List<MethodTreeView> methods) {
    this.id = id;
    this.simpleName = simpleName;
    this.filePath = filePath;
    this.txCssClass = txCssClass;
    this.txTooltip = txTooltip;
    this.epCssClass = epCssClass;
    this.epTooltip = epTooltip;
    this.methods = methods;
  }
}
