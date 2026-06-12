package io.openaev.rewrite;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;

import java.util.List;
import java.util.Set;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.marker.Markers;

public class AddTxCtxAsFirstParameterOnTransactionalMethods extends Recipe {

  private static final String TX_CTX_TYPE = "io.openaev.context.TxCtx";

  private static final Set<String> MAPPING_ANNOTATIONS =
      Set.of("PostMapping", "GetMapping", "PutMapping", "DeleteMapping", "PatchMapping");

  private static final Set<String> TRANSACTIONAL_ANNOTATIONS =
      Set.of(
          "org.springframework.transaction.annotation.Transactional",
          "jakarta.transaction.Transactional");

  @Override
  public String getDisplayName() {
    return "Add TxCtx first parameter on @Transactional methods";
  }

  @Override
  public String getDescription() {
    return "Adds `TxCtx txCtx` as first parameter on non-endpoint @Transactional methods.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaIsoVisitor<>() {
      @Override
      public J.MethodDeclaration visitMethodDeclaration(
          J.MethodDeclaration method, ExecutionContext ctx) {
        J.MethodDeclaration currentMethod = super.visitMethodDeclaration(method, ctx);
        if (!hasTransactionalAnnotation(currentMethod) || hasMappingAnnotation(currentMethod)) {
          return currentMethod;
        }

        int txCtxIndex = findTxCtxParameterIndex(currentMethod.getParameters());
        if (txCtxIndex == 0) {
          return currentMethod;
        }

        maybeAddImport(TX_CTX_TYPE, false);
        J.MethodDeclaration updatedMethod =
            txCtxIndex < 0
                ? addTxCtxAsFirstParameter(currentMethod)
                : moveTxCtxAsFirstParameter(currentMethod, txCtxIndex);
        return maybeAutoFormat(currentMethod, updatedMethod, ctx);
      }
    };
  }

  private static J.MethodDeclaration addTxCtxAsFirstParameter(J.MethodDeclaration method) {
    List<Statement> parameters = sanitizeParameters(method.getParameters());
    J.VariableDeclarations txCtxParameter = createTxCtxParameter(method);
    if (!parameters.isEmpty()) {
      txCtxParameter = txCtxParameter.withPrefix(Space.EMPTY);
      parameters =
          ListUtils.mapFirst(
              parameters, p -> p.getPrefix().isEmpty() ? p.withPrefix(Space.SINGLE_SPACE) : p);
    }
    return method.withParameters(ListUtils.insert(parameters, txCtxParameter, 0));
  }

  private static J.MethodDeclaration moveTxCtxAsFirstParameter(
      J.MethodDeclaration method, int txCtxIndex) {
    List<Statement> parameters = sanitizeParameters(method.getParameters());
    Statement txCtxParameter = parameters.get(txCtxIndex).withPrefix(Space.EMPTY);
    parameters = ListUtils.map(parameters, (i, p) -> i == txCtxIndex ? null : p);
    if (!parameters.isEmpty()) {
      parameters =
          ListUtils.mapFirst(
              parameters, p -> p.getPrefix().isEmpty() ? p.withPrefix(Space.SINGLE_SPACE) : p);
    }
    return method.withParameters(ListUtils.insert(parameters, txCtxParameter, 0));
  }

  private static List<Statement> sanitizeParameters(List<Statement> parameters) {
    if (parameters.isEmpty() || (parameters.size() == 1 && parameters.get(0) instanceof J.Empty)) {
      return List.of();
    }
    return parameters;
  }

  private static int findTxCtxParameterIndex(List<Statement> parameters) {
    for (int i = 0; i < parameters.size(); i++) {
      Statement parameter = parameters.get(i);
      if (!(parameter instanceof J.VariableDeclarations variableDeclarations)) {
        continue;
      }
      TypeTree typeExpression = variableDeclarations.getTypeExpression();
      if (typeExpression == null) {
        continue;
      }
      JavaType type = typeExpression.getType();
      if (type != null && TX_CTX_TYPE.equals(type.toString())) {
        return i;
      }
      if (TX_CTX_TYPE.equals(typeExpression.toString())
          || "TxCtx".equals(typeExpression.toString())) {
        return i;
      }
    }
    return -1;
  }

  private static boolean hasMappingAnnotation(J.MethodDeclaration methodDeclaration) {
    return methodDeclaration.getLeadingAnnotations().stream()
        .map(J.Annotation::getSimpleName)
        .anyMatch(MAPPING_ANNOTATIONS::contains);
  }

  private static boolean hasTransactionalAnnotation(J.MethodDeclaration methodDeclaration) {
    return methodDeclaration.getLeadingAnnotations().stream()
        .anyMatch(
            annotation ->
                TRANSACTIONAL_ANNOTATIONS.contains(
                        annotation.getType() != null ? annotation.getType().toString() : "")
                    || "Transactional".equals(annotation.getSimpleName()));
  }

  private static J.VariableDeclarations createTxCtxParameter(J.MethodDeclaration method) {
    TypeTree typeTree = TypeTree.build(TX_CTX_TYPE);
    if (typeTree instanceof J.FieldAccess) {
      typeTree =
          ((J.FieldAccess) typeTree)
              .withName(((J.FieldAccess) typeTree).getName().withType(typeTree.getType()));
    } else if (typeTree.getType() == null) {
      typeTree = ((J.Identifier) typeTree).withType(JavaType.ShallowClass.build(TX_CTX_TYPE));
    }

    return new J.VariableDeclarations(
        randomId(),
        Space.EMPTY,
        Markers.EMPTY,
        emptyList(),
        emptyList(),
        typeTree,
        null,
        singletonList(
            new JRightPadded<>(
                new J.VariableDeclarations.NamedVariable(
                    randomId(),
                    Space.EMPTY,
                    Markers.EMPTY,
                    new J.Identifier(
                        randomId(),
                        Space.EMPTY,
                        Markers.EMPTY,
                        emptyList(),
                        "txCtx",
                        typeTree.getType(),
                        new JavaType.Variable(
                            null, 0, "txCtx", method.getMethodType(), typeTree.getType(), null)),
                    emptyList(),
                    null,
                    null),
                Space.EMPTY,
                Markers.EMPTY)));
  }
}
