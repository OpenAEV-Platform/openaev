package io.openaev.tools.analyzer.model;

import java.util.List;

/** Holds all discovered information about a single @Repository-annotated type. */
public class RepositoryInfo {

  /** Fully-qualified class/interface name. */
  public final String qualifiedName;

  public final String simpleName;

  /** Path relative to the repository root. */
  public final String relativeFilePath;

  /** Methods declared directly in this type (custom queries, finders, etc.). */
  public final List<MethodInfo> declaredMethods;

  public RepositoryInfo(
      String qualifiedName,
      String simpleName,
      String relativeFilePath,
      List<MethodInfo> declaredMethods) {
    this.qualifiedName = qualifiedName;
    this.simpleName = simpleName;
    this.relativeFilePath = relativeFilePath;
    this.declaredMethods = declaredMethods;
  }

  /** HTML-safe anchor id. */
  public String htmlId() {
    return "repo_" + qualifiedName.replace('.', '_');
  }
}
