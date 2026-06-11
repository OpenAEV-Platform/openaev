package io.openaev.annotation;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

/**
 * Compile-time rule that forbids direct instantiation of tenant-scoped {@code @Entity} classes
 * (i.e. classes annotated with {@code @Entity} that implement {@code TenantBase}).
 *
 * <p>Tenant entities must be created through dedicated factory methods or builders to ensure tenant
 * context and audit metadata are properly set. Direct {@code new Entity()} calls bypass these
 * safeguards.
 *
 * <p>This processor uses the {@code com.sun.source.util.Trees} API to scan method bodies
 * (AST-level) for {@link NewClassTree} nodes whose target type matches these criteria.
 *
 * <p>Enable via compiler option:
 *
 * <pre>
 *   -Aentity.rule.no.direct.new=true   (default: false)
 * </pre>
 */
@SupportedAnnotationTypes("*")
@SupportedOptions(NoEntityInstantiationProcessor.OPT_NO_ENTITY_NEW)
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class NoEntityInstantiationProcessor extends AbstractProcessor {

  static final String OPT_NO_ENTITY_NEW = "entity.rule.no.direct.new";

  private static final String ENTITY_ANNOTATION = "jakarta.persistence.Entity";
  private static final String TENANT_BASE_TYPE = "io.openaev.database.model.TenantBase";

  private boolean listenerRegistered = false;

  /** Names of @Entity classes that implement TenantBase, discovered during processing. */
  private final Set<String> tenantEntityClassNames = new HashSet<>();

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (!isEnabled()) {
      return false;
    }

    // Collect all @Entity classes that implement TenantBase
    TypeElement entityAnnotation =
        processingEnv.getElementUtils().getTypeElement(ENTITY_ANNOTATION);
    TypeElement tenantBaseElement =
        processingEnv.getElementUtils().getTypeElement(TENANT_BASE_TYPE);
    if (entityAnnotation != null && tenantBaseElement != null) {
      TypeMirror tenantBaseType = tenantBaseElement.asType();
      for (Element element : roundEnv.getElementsAnnotatedWith(entityAnnotation)) {
        if (element instanceof TypeElement typeElement) {
          if (processingEnv.getTypeUtils().isAssignable(typeElement.asType(), tenantBaseType)) {
            tenantEntityClassNames.add(typeElement.getQualifiedName().toString());

            boolean hasBuilder =
                typeElement.getAnnotationMirrors().stream()
                    .anyMatch(
                        am -> {
                          Element ae = am.getAnnotationType().asElement();
                          return ae instanceof TypeElement te
                              && te.getQualifiedName().toString().equals("lombok.Builder");
                        });
            if (hasBuilder) {
              processingEnv
                  .getMessager()
                  .printMessage(
                      Diagnostic.Kind.ERROR,
                      "Usage of @Builder on tenant entities is forbidden.",
                      typeElement);
            }
          }
        }
      }
    }

    // Register the AST scanner once (on the first round)
    if (!listenerRegistered) {
      listenerRegistered = true;
      try {
        javax.annotation.processing.ProcessingEnvironment unwrapped =
            jbUnwrap(javax.annotation.processing.ProcessingEnvironment.class, processingEnv);
        Trees trees = Trees.instance(unwrapped);
        com.sun.source.util.JavacTask.instance(unwrapped)
            .addTaskListener(new EntityNewClassListener(trees));
      } catch (Exception e) {
        // Log a warning or ignore if running in an environment where JavacTask is unavailable
        processingEnv
            .getMessager()
            .printMessage(
                Diagnostic.Kind.WARNING,
                "Could not register AST scanner for entity instantiation check: " + e.getMessage());
      }
    }

    return false;
  }

  private boolean isEnabled() {
    String value = processingEnv.getOptions().get(OPT_NO_ENTITY_NEW);
    return value != null && Boolean.parseBoolean(value);
  }

  private static <T> T jbUnwrap(Class<? extends T> iface, T wrapper) {
    T unwrapped = null;
    try {
      final Class<?> apiWrappers =
          wrapper.getClass().getClassLoader().loadClass("org.jetbrains.jps.javac.APIWrappers");
      final java.lang.reflect.Method unwrapMethod =
          apiWrappers.getDeclaredMethod("unwrap", Class.class, Object.class);
      unwrapped = iface.cast(unwrapMethod.invoke(null, iface, wrapper));
    } catch (Throwable ignored) {
    }
    return unwrapped != null ? unwrapped : wrapper;
  }

  /**
   * Checks whether the given type is an @Entity that implements TenantBase. Uses both the collected
   * set and a direct check as fallback for entities from dependencies.
   */
  private boolean isTenantEntity(TypeMirror typeMirror) {
    if (typeMirror instanceof DeclaredType declaredType) {
      Element element = declaredType.asElement();
      if (element instanceof TypeElement typeElement) {
        String qualifiedName = typeElement.getQualifiedName().toString();
        if (tenantEntityClassNames.contains(qualifiedName)) {
          return true;
        }
        // Fallback: check directly (for entities from dependencies)
        boolean hasEntity =
            typeElement.getAnnotationMirrors().stream()
                .anyMatch(
                    am -> {
                      Element ae = am.getAnnotationType().asElement();
                      return ae instanceof TypeElement te
                          && te.getQualifiedName().toString().equals(ENTITY_ANNOTATION);
                    });
        if (hasEntity) {
          TypeElement tenantBase = processingEnv.getElementUtils().getTypeElement(TENANT_BASE_TYPE);
          return tenantBase != null
              && processingEnv
                  .getTypeUtils()
                  .isAssignable(typeElement.asType(), tenantBase.asType());
        }
      }
    }
    return false;
  }

  /**
   * TaskListener that fires after type analysis of each compilation unit. Scans the AST for {@code
   * new EntityClass()} calls.
   */
  private class EntityNewClassListener implements TaskListener {

    private final Trees trees;

    EntityNewClassListener(Trees trees) {
      this.trees = trees;
    }

    @Override
    public void finished(TaskEvent event) {
      if (event.getKind() != TaskEvent.Kind.ANALYZE) {
        return;
      }

      CompilationUnitTree compilationUnit = event.getCompilationUnit();
      compilationUnit.accept(
          new TreeScanner<Void, Void>() {
            @Override
            public Void visitNewClass(NewClassTree node, Void unused) {
              try {
                TypeMirror typeMirror =
                    trees.getTypeMirror(trees.getPath(compilationUnit, node.getIdentifier()));
                if (typeMirror != null && isTenantEntity(typeMirror)) {
                  // Extract the simple class name for the error message
                  String className = node.getIdentifier().toString();
                  trees.printMessage(
                      Diagnostic.Kind.ERROR,
                      String.format(
                          "Direct instantiation of tenant entity '%s' is forbidden."
                              + " Use a factory method or builder instead of 'new %s()'.",
                          className, className),
                      node,
                      compilationUnit);
                }
              } catch (Exception e) {
                // Silently skip nodes we can't resolve (e.g. non-entity types)
              }
              return super.visitNewClass(node, unused);
            }
          },
          null);
    }
  }
}
