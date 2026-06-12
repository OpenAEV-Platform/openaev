package io.openaev.rewrite;

import java.util.Comparator;
import java.util.Set;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;

public class AddTransactionalToMappedMethods extends Recipe {

  private static final Set<String> MAPPING_ANNOTATIONS =
      Set.of("PostMapping", "GetMapping", "PutMapping", "DeleteMapping", "PatchMapping");

  private static final String SPRING_TRANSACTIONAL =
      "org.springframework.transaction.annotation.Transactional";
  private static final String JAKARTA_TRANSACTIONAL = "jakarta.transaction.Transactional";

  @Override
  public String getDisplayName() {
    return "Add @Transactional on mapped methods";
  }

  @Override
  public String getDescription() {
    return "Adds @Transactional to methods annotated with Spring web mapping annotations.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaIsoVisitor<>() {
      @Override
      public J.MethodDeclaration visitMethodDeclaration(
          J.MethodDeclaration method, ExecutionContext ctx) {
        J.MethodDeclaration currentMethod = super.visitMethodDeclaration(method, ctx);
        if (!hasMappingAnnotation(currentMethod) || hasTransactional(currentMethod)) {
          return currentMethod;
        }

        // Reuse whichever @Transactional the file already imports (Spring or Jakarta) so we never
        // add a second single-type import with the same simple name, which would not compile.
        String transactionalType = resolveTransactionalType(getCursor());
        maybeAddImport(transactionalType, false);
        JavaTemplate transactionalTemplate =
            JavaTemplate.builder("@Transactional").imports(transactionalType).build();

        return transactionalTemplate.apply(
            updateCursor(currentMethod),
            currentMethod
                .getCoordinates()
                .addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
      }
    };
  }

  /**
   * Returns the fully qualified {@code Transactional} type to use for the current file: the one
   * already imported (Jakarta or Spring) if any, otherwise the Spring one by default.
   */
  private static String resolveTransactionalType(Cursor cursor) {
    J.CompilationUnit compilationUnit = cursor.firstEnclosing(J.CompilationUnit.class);
    if (compilationUnit != null) {
      for (J.Import anImport : compilationUnit.getImports()) {
        String typeName = anImport.getTypeName();
        if (JAKARTA_TRANSACTIONAL.equals(typeName)) {
          return JAKARTA_TRANSACTIONAL;
        }
        if (SPRING_TRANSACTIONAL.equals(typeName)) {
          return SPRING_TRANSACTIONAL;
        }
      }
    }
    return SPRING_TRANSACTIONAL;
  }

  private static boolean hasMappingAnnotation(J.MethodDeclaration methodDeclaration) {
    return methodDeclaration.getLeadingAnnotations().stream()
        .map(J.Annotation::getSimpleName)
        .anyMatch(MAPPING_ANNOTATIONS::contains);
  }

  private static boolean hasTransactional(J.MethodDeclaration methodDeclaration) {
    return methodDeclaration.getLeadingAnnotations().stream()
        .map(J.Annotation::getSimpleName)
        .anyMatch("Transactional"::equals);
  }
}
