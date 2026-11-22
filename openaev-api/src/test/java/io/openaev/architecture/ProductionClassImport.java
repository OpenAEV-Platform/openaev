package io.openaev.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.ref.SoftReference;

/**
 * One import of the production classes of {@code io.openaev}, shared by the architecture tests that
 * read it outside {@code @AnalyzeClasses}.
 *
 * <p>The architecture tests run in one surefire fork. A {@code static final JavaClasses} field
 * keeps a whole import reachable from the moment its class is loaded until the fork ends, and two
 * such fields kept two imports, beside the ones ArchUnit caches for {@code @AnalyzeClasses}. The
 * import here is held softly: the fork reuses it while memory allows and the collector may drop it
 * under pressure, in which case the next caller imports again. A caller keeps the returned value in
 * a local variable only, never in a field.
 */
final class ProductionClassImport {

  private static SoftReference<JavaClasses> cached = new SoftReference<>(null);

  private ProductionClassImport() {}

  static synchronized JavaClasses get() {
    JavaClasses classes = cached.get();
    if (classes == null) {
      classes =
          new ClassFileImporter()
              .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
              .importPackages("io.openaev");
      cached = new SoftReference<>(classes);
    }
    return classes;
  }
}
