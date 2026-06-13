package io.openaev.tools.analyzer.model;

/**
 * Metadata for a single method discovered while indexing the source tree.
 *
 * <p>A method is considered "transactional" when either it carries @Transactional directly OR the
 * enclosing class/interface carries @Transactional (Spring propagation applies to all public
 * methods).
 */
public class MethodInfo {

  public final MethodKey key;

  /** Carries org.springframework.transaction.annotation.Transactional (method or class level). */
  public final boolean isSpringTransactional;

  /** Carries jakarta.transaction.Transactional or javax.transaction.Transactional. */
  public final boolean isJakartaTransactional;

  /** Declared inside a class/interface that bears @Repository. */
  public final boolean isRepositoryMethod;

  /** Carries any Spring MVC mapping annotation (@GetMapping, @PostMapping, …). */
  public final boolean isHttpEndpoint;

  /** Path relative to the repository root, or null for synthetic/unknown methods. */
  public final String relativeFilePath;

  public final int lineNumber;

  /** Human-readable parameter list, e.g. "(String, Long)". */
  public final String parameterSummary;

  /**
   * Simple name of the superclass or interface from which this method is inherited/overridden,
   * or null if not resolvable from indexed source.
   */
  public String overridesFrom;  // set during second-pass by SourceIndexer

  /** True when the method carries {@code @Override}. */
  public boolean hasOverride;   // set during second-pass by SourceIndexer

  public MethodInfo(
      MethodKey key,
      boolean isSpringTransactional,
      boolean isJakartaTransactional,
      boolean isRepositoryMethod,
      boolean isHttpEndpoint,
      String relativeFilePath,
      int lineNumber,
      String parameterSummary) {
    this.key = key;
    this.isSpringTransactional = isSpringTransactional;
    this.isJakartaTransactional = isJakartaTransactional;
    this.isRepositoryMethod = isRepositoryMethod;
    this.isHttpEndpoint = isHttpEndpoint;
    this.relativeFilePath = relativeFilePath;
    this.lineNumber = lineNumber;
    this.parameterSummary = parameterSummary;
  }

  /** True if any form of @Transactional is present. */
  public boolean isTransactional() {
    return isSpringTransactional || isJakartaTransactional;
  }

  /**
   * Placeholder for methods that are called in source but have no indexed declaration (inherited
   * JPA methods, external types, etc.).
   */
  public static MethodInfo unknown(MethodKey key) {
    return new MethodInfo(key, false, false, false, false, null, -1, "(…)");
  }

  /** Whether this method has source information (was found during indexing). */
  public boolean hasSource() {
    return relativeFilePath != null;
  }

  @Override
  public String toString() {
    return key.fullDisplay();
  }
}
