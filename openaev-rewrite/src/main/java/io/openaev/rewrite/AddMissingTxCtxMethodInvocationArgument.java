package io.openaev.rewrite;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

public class AddMissingTxCtxMethodInvocationArgument extends Recipe {

  private static final String TX_CTX_TYPE = "io.openaev.context.TxCtx";

  @Override
  public String getDisplayName() {
    return "Add missing TxCtx method argument";
  }

  @Override
  public String getDescription() {
    return "Adds `TxCtx.missing()` to method invocations when the called method requires "
        + "`TxCtx` as first parameter and the argument is missing.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaIsoVisitor<>() {
      @Override
      public J.MethodInvocation visitMethodInvocation(
          J.MethodInvocation method, ExecutionContext ctx) {
        J.MethodInvocation currentMethod = super.visitMethodInvocation(method, ctx);
        if (hasFirstArgumentTxCtx(currentMethod)) {
          return currentMethod;
        }

        if (!isMissingTxCtxArgument(currentMethod)) {
          return currentMethod;
        }

        maybeAddImport(TX_CTX_TYPE, false);
        StringBuilder templateBuilder = new StringBuilder("TxCtx.missing()");
        for (int i = 0; i < currentMethod.getArguments().size(); i++) {
          templateBuilder.append(", #{any()}");
        }
        JavaTemplate template =
            JavaTemplate.builder(templateBuilder.toString()).imports(TX_CTX_TYPE).build();
        return template.apply(
            getCursor(),
            currentMethod.getCoordinates().replaceArguments(),
            currentMethod.getArguments().toArray());
      }
    };
  }

  private static boolean isMissingTxCtxArgument(J.MethodInvocation method) {
    JavaType.Method methodType = method.getMethodType();
    if (methodType == null || methodType.getParameterTypes().isEmpty()) {
      return false;
    }
    JavaType firstParameterType = methodType.getParameterTypes().get(0);
    return isTxCtxType(firstParameterType)
        && method.getArguments().size() == methodType.getParameterTypes().size() - 1;
  }

  private static boolean hasFirstArgumentTxCtx(J.MethodInvocation method) {
    if (method.getArguments().isEmpty()) {
      return false;
    }
    Expression firstArgument = method.getArguments().get(0);
    if (isTxCtxType(firstArgument.getType())) {
      return true;
    }
    String source = firstArgument.toString();
    return "TxCtx.missing()".equals(source) || "io.openaev.context.TxCtx.missing()".equals(source);
  }

  private static boolean isTxCtxType(Expression expression) {
    return expression != null && isTxCtxType(expression.getType());
  }

  private static boolean isTxCtxType(JavaType type) {
    return type instanceof JavaType.FullyQualified fullyQualified
        && TX_CTX_TYPE.equals(fullyQualified.getFullyQualifiedName());
  }
}
