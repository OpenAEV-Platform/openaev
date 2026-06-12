package io.openaev.rewrite;

import java.util.ArrayList;
import java.util.List;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

public class SetReadOnlyOnGetMappingTransactionalMethods extends Recipe {

  private static final String SPRING_TRANSACTIONAL =
      "org.springframework.transaction.annotation.Transactional";
  private static final String JAKARTA_TRANSACTIONAL = "jakarta.transaction.Transactional";

  @Override
  public String getDisplayName() {
    return "Set readOnly=true on GET @Transactional methods";
  }

  @Override
  public String getDescription() {
    return "Adds or updates readOnly=true on @Transactional for methods annotated with @GetMapping.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaIsoVisitor<>() {
      @Override
      public J.MethodDeclaration visitMethodDeclaration(
          J.MethodDeclaration method, ExecutionContext ctx) {
        J.MethodDeclaration currentMethod = super.visitMethodDeclaration(method, ctx);
        if (!hasGetMapping(currentMethod)) {
          return currentMethod;
        }

        return currentMethod.withLeadingAnnotations(
            ListUtils.map(
                currentMethod.getLeadingAnnotations(),
                annotation -> updateTransactionalAnnotation(annotation, getCursor())));
      }
    };
  }

  private static J.Annotation updateTransactionalAnnotation(
      J.Annotation annotation, Cursor cursor) {
    if (!"Transactional".equals(annotation.getSimpleName())) {
      return annotation;
    }

    String annotationSource = buildTransactionalSource(annotation, cursor);
    if (annotationSource == null) {
      return annotation;
    }

    String transactionalType = resolveTransactionalType(annotation);
    JavaTemplate template =
        JavaTemplate.builder(annotationSource).imports(transactionalType).build();
    return template.apply(new Cursor(cursor, annotation), annotation.getCoordinates().replace());
  }

  private static String buildTransactionalSource(J.Annotation annotation, Cursor cursor) {
    List<Expression> arguments = annotation.getArguments();
    if (arguments == null || arguments.isEmpty()) {
      return "@Transactional(readOnly = true)";
    }

    List<String> renderedArguments = new ArrayList<>();
    boolean hasReadOnly = false;
    boolean alreadyReadOnlyTrue = false;

    for (Expression argument : arguments) {
      if (argument instanceof J.Assignment assignment
          && assignment.getVariable() instanceof J.Identifier identifier
          && "readOnly".equals(identifier.getSimpleName())) {
        hasReadOnly = true;
        String valueSource = assignment.getAssignment().printTrimmed(cursor).replace(" ", "");
        if ("true".equals(valueSource)) {
          alreadyReadOnlyTrue = true;
          renderedArguments.add(argument.printTrimmed(cursor));
        } else {
          renderedArguments.add("readOnly = true");
        }
      } else {
        renderedArguments.add(argument.printTrimmed(cursor));
      }
    }

    if (hasReadOnly && alreadyReadOnlyTrue) {
      return null;
    }
    if (!hasReadOnly) {
      renderedArguments.add("readOnly = true");
    }
    return "@Transactional(" + String.join(", ", renderedArguments) + ")";
  }

  private static boolean hasGetMapping(J.MethodDeclaration method) {
    return method.getLeadingAnnotations().stream()
        .map(J.Annotation::getSimpleName)
        .anyMatch("GetMapping"::equals);
  }

  private static String resolveTransactionalType(J.Annotation annotation) {
    if (annotation.getType() != null
        && JAKARTA_TRANSACTIONAL.equals(annotation.getType().toString())) {
      return JAKARTA_TRANSACTIONAL;
    }
    return SPRING_TRANSACTIONAL;
  }
}
