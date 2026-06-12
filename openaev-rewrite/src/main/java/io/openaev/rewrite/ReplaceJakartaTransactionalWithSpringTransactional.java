package io.openaev.rewrite;

import java.util.List;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeAnnotationAttributeName;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

public class ReplaceJakartaTransactionalWithSpringTransactional extends Recipe {

  private static final String JAKARTA_TRANSACTIONAL = "jakarta.transaction.Transactional";
  private static final String SPRING_TRANSACTIONAL =
      "org.springframework.transaction.annotation.Transactional";

  @Override
  public String getDisplayName() {
    return "Replace Jakarta @Transactional with Spring @Transactional";
  }

  @Override
  public String getDescription() {
    return "Migrates usages of jakarta.transaction.Transactional to "
        + "org.springframework.transaction.annotation.Transactional.";
  }

  @Override
  public List<Recipe> getRecipeList() {
    return List.of(
        new ChangeAnnotationAttributeName(JAKARTA_TRANSACTIONAL, "rollbackOn", "rollbackFor"),
        new ChangeAnnotationAttributeName(JAKARTA_TRANSACTIONAL, "dontRollbackOn", "noRollbackFor"),
        new ChangeType(JAKARTA_TRANSACTIONAL, SPRING_TRANSACTIONAL, true),
        new TranslateTxTypeToPropagation());
  }

  /**
   * Translates the positional Jakarta value {@code @Transactional(Transactional.TxType.X)} into the
   * Spring form {@code @Transactional(propagation = Propagation.X)}. The {@code rollbackOn} /
   * {@code dontRollbackOn} attributes are already handled by {@link ChangeAnnotationAttributeName}.
   */
  static class TranslateTxTypeToPropagation extends Recipe {

    private static final String PROPAGATION =
        "org.springframework.transaction.annotation.Propagation";

    @Override
    public String getDisplayName() {
      return "Translate @Transactional TxType to Spring Propagation";
    }

    @Override
    public String getDescription() {
      return "Replaces `@Transactional(Transactional.TxType.X)` with "
          + "`@Transactional(propagation = Propagation.X)`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
      return new JavaIsoVisitor<>() {
        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
          J.Annotation a = super.visitAnnotation(annotation, ctx);
          if (!"Transactional".equals(a.getSimpleName())
              || a.getArguments() == null
              || a.getArguments().size() != 1) {
            return a;
          }
          Expression only = a.getArguments().get(0);
          if (only instanceof J.Assignment) {
            return a;
          }
          String source = only.toString();
          if (!source.contains("TxType.")) {
            return a;
          }
          String constant = source.substring(source.lastIndexOf('.') + 1).trim();
          maybeAddImport(PROPAGATION, false);
          return JavaTemplate.builder("@Transactional(propagation = Propagation." + constant + ")")
              .imports(PROPAGATION)
              .build()
              .apply(getCursor(), a.getCoordinates().replace());
        }
      };
    }
  }
}
