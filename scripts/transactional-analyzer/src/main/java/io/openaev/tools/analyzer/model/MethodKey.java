package io.openaev.tools.analyzer.model;

import java.util.Objects;

/**
 * Identifies a method by its declaring class (fully qualified), method name, and arity
 * (parameter count). Overloads with different arities are treated as distinct methods,
 * which prevents false cycle detection when one overload delegates to another.
 * Same-arity overloads with different parameter types are still merged (type resolution
 * at call sites is imprecise without a full symbol solver), but this covers the vast
 * majority of real-world cases.
 */
public record MethodKey(String qualifiedClassName, String methodName, int arity) {

  public String simpleClassName() {
    int idx = qualifiedClassName.lastIndexOf('.');
    return idx >= 0 ? qualifiedClassName.substring(idx + 1) : qualifiedClassName;
  }

  /** Short display: ClassName.method() */
  public String display() {
    return simpleClassName() + "." + methodName + "()";
  }

  /** Full display: pkg.ClassName.method() */
  public String fullDisplay() {
    return qualifiedClassName + "." + methodName + "()";
  }

  /** Safe HTML element ID (includes arity to keep overloads distinct). */
  public String htmlId() {
    return (qualifiedClassName + "__" + methodName + "__" + arity)
        .replace('.', '_').replaceAll("[^a-zA-Z0-9_]", "_");
  }
}
