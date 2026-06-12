package io.openaev.rewrite;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.marker.Markers;

/**
 * Single-pass recipe that adds {@code TxCtx txCtx} as first parameter on non-endpoint
 * {@code @Transactional} methods AND prepends {@code TxCtx.missing()} to every direct call site.
 *
 * <p>Call sites are matched against signatures captured during the scan (structurally, by {@code
 * owner#name/arity}), NOT against the invocation's re-attributed {@code methodType} which is stale
 * after the declaration changes in the same pass.
 *
 * <p>Three families of methods are intentionally excluded because changing their signature in
 * isolation would not compile (the whole hierarchy or every usage would have to change together):
 *
 * <ul>
 *   <li>{@code @Override} methods — they implement an interface/superclass contract.
 *   <li>methods whose name/arity is overridden somewhere (i.e. they are part of an override
 *       hierarchy as the parent side) — collected from every {@code @Override} method.
 *   <li>methods used as a method reference ({@code obj::method}) — a reference cannot receive an
 *       extra argument without being rewritten into a lambda.
 * </ul>
 */
public class AddTxCtxAndFixCallSites extends ScanningRecipe<AddTxCtxAndFixCallSites.Accumulator> {

  private static final String TX_CTX_TYPE = "io.openaev.context.TxCtx";

  private static final Set<String> MAPPING_ANNOTATIONS =
      Set.of("PostMapping", "GetMapping", "PutMapping", "DeleteMapping", "PatchMapping");

  private static final Set<String> TRANSACTIONAL_ANNOTATIONS =
      Set.of(
          "org.springframework.transaction.annotation.Transactional",
          "jakarta.transaction.Transactional");

  static class Accumulator {
    /** owner#name/arity of candidate methods that will receive a TxCtx first parameter. */
    final Set<String> targets = new HashSet<>();

    /** owner#name of methods used as a method reference ({@code obj::method}) — excluded. */
    final Set<String> referencedMethods = new HashSet<>();

    /** name/arity of every {@code @Override} method — the parent side is excluded. */
    final Set<String> overriddenSignatures = new HashSet<>();
  }

  @Override
  public String getDisplayName() {
    return "Add TxCtx parameter and fix call sites";
  }

  @Override
  public String getDescription() {
    return "Adds `TxCtx txCtx` as first parameter on non-endpoint @Transactional methods and "
        + "prepends `TxCtx.missing()` to every direct call site that does not pass it.";
  }

  @Override
  public Accumulator getInitialValue(ExecutionContext ctx) {
    return new Accumulator();
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
    return new JavaIsoVisitor<>() {
      @Override
      public J.MethodDeclaration visitMethodDeclaration(
          J.MethodDeclaration method, ExecutionContext ctx) {
        J.MethodDeclaration current = super.visitMethodDeclaration(method, ctx);
        if (hasOverrideAnnotation(current)) {
          acc.overriddenSignatures.add(
              arityKey(current.getSimpleName(), realArity(current.getParameters())));
        }
        if (isCandidate(current)) {
          J.ClassDeclaration owner = getCursor().firstEnclosing(J.ClassDeclaration.class);
          if (owner != null && owner.getType() != null) {
            acc.targets.add(
                methodKey(
                    owner.getType().getFullyQualifiedName(),
                    current.getSimpleName(),
                    realArity(current.getParameters())));
          }
        }
        return current;
      }

      @Override
      public J.MemberReference visitMemberReference(
          J.MemberReference memberRef, ExecutionContext ctx) {
        J.MemberReference current = super.visitMemberReference(memberRef, ctx);
        JavaType.Method methodType = current.getMethodType();
        if (methodType != null && methodType.getDeclaringType() != null) {
          acc.referencedMethods.add(
              nameKey(methodType.getDeclaringType().getFullyQualifiedName(), methodType.getName()));
        }
        return current;
      }
    };
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
    return new JavaIsoVisitor<>() {
      @Override
      public J.MethodDeclaration visitMethodDeclaration(
          J.MethodDeclaration method, ExecutionContext ctx) {
        J.MethodDeclaration current = super.visitMethodDeclaration(method, ctx);
        if (!isCandidate(current)) {
          return current;
        }
        if (acc.overriddenSignatures.contains(
            arityKey(current.getSimpleName(), realArity(current.getParameters())))) {
          return current;
        }
        J.ClassDeclaration owner = getCursor().firstEnclosing(J.ClassDeclaration.class);
        if (owner == null || owner.getType() == null) {
          return current;
        }
        if (acc.referencedMethods.contains(
            nameKey(owner.getType().getFullyQualifiedName(), current.getSimpleName()))) {
          return current;
        }
        maybeAddImport(TX_CTX_TYPE, false);
        return maybeAutoFormat(current, addTxCtxAsFirstParameter(current), ctx);
      }

      @Override
      public J.MethodInvocation visitMethodInvocation(
          J.MethodInvocation method, ExecutionContext ctx) {
        J.MethodInvocation current = super.visitMethodInvocation(method, ctx);
        if (firstArgumentIsTxCtx(current)) {
          return current;
        }
        JavaType.Method methodType = current.getMethodType();
        if (methodType == null || methodType.getDeclaringType() == null) {
          return current;
        }
        String ownerFqn = methodType.getDeclaringType().getFullyQualifiedName();
        String name = current.getSimpleName();
        List<Expression> args = realArguments(current.getArguments());
        if (acc.referencedMethods.contains(nameKey(ownerFqn, name))
            || acc.overriddenSignatures.contains(arityKey(name, args.size()))
            || !acc.targets.contains(methodKey(ownerFqn, name, args.size()))) {
          return current;
        }

        maybeAddImport(TX_CTX_TYPE, false);
        StringBuilder tpl = new StringBuilder("TxCtx.missing()");
        for (int i = 0; i < args.size(); i++) {
          tpl.append(", #{any()}");
        }
        return JavaTemplate.builder(tpl.toString())
            .imports(TX_CTX_TYPE)
            .build()
            .apply(getCursor(), current.getCoordinates().replaceArguments(), args.toArray());
      }
    };
  }

  // -------------------------------------------------------------------------
  // Predicates / keys
  // -------------------------------------------------------------------------

  private static boolean isCandidate(J.MethodDeclaration method) {
    return hasTransactionalAnnotation(method)
        && !hasMappingAnnotation(method)
        && !hasOverrideAnnotation(method)
        && findTxCtxParameterIndex(method.getParameters()) < 0;
  }

  private static boolean hasOverrideAnnotation(J.MethodDeclaration method) {
    return method.getLeadingAnnotations().stream()
        .map(J.Annotation::getSimpleName)
        .anyMatch("Override"::equals);
  }

  private static String methodKey(String owner, String name, int arity) {
    return owner + "#" + name + "/" + arity;
  }

  private static String nameKey(String owner, String name) {
    return owner + "#" + name;
  }

  private static String arityKey(String name, int arity) {
    return name + "/" + arity;
  }

  private static int realArity(List<Statement> parameters) {
    return sanitizeParameters(parameters).size();
  }

  private static List<Expression> realArguments(List<Expression> arguments) {
    return arguments.stream().filter(a -> !(a instanceof J.Empty)).toList();
  }

  private static boolean firstArgumentIsTxCtx(J.MethodInvocation method) {
    List<Expression> args = realArguments(method.getArguments());
    if (args.isEmpty()) {
      return false;
    }
    Expression first = args.get(0);
    if (isTxCtxType(first.getType())) {
      return true;
    }
    String src = first.toString();
    return "TxCtx.missing()".equals(src) || "io.openaev.context.TxCtx.missing()".equals(src);
  }

  // -------------------------------------------------------------------------
  // Parameter insertion (adapted from AddTxCtxAsFirstParameterOnTransactionalMethods)
  // -------------------------------------------------------------------------

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

  private static boolean isTxCtxType(JavaType type) {
    return type instanceof JavaType.FullyQualified fullyQualified
        && TX_CTX_TYPE.equals(fullyQualified.getFullyQualifiedName());
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
