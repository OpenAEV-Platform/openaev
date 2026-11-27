package io.openaev.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaMethodReference;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Set;
import org.hibernate.SessionFactory;
import org.hibernate.SharedSessionContract;

/**
 * The Hibernate tenant filter is gone: no entity declares a filter, and tenant isolation is the
 * statement inspector's. A filter declaration or a call that arms or reads one would silently do
 * nothing, or throw on an undefined filter, so neither comes back. {@link
 * TenantFilterRemovedRulesTest} proves each rule fires on fixtures.
 */
@AnalyzeClasses(packages = "io.openaev", importOptions = ImportOption.DoNotIncludeTests.class)
class TenantFilterRemovedArchTest {

  private static final Set<String> FILTER_ANNOTATIONS =
      Set.of(
          "Filter",
          "Filters",
          "FilterDef",
          "FilterDefs",
          "FilterJoinTable",
          "FilterJoinTables",
          "ParamDef");

  private static final Set<String> FILTER_CALLS =
      Set.of("enableFilter", "disableFilter", "getEnabledFilter", "getDefinedFilterNames");

  /** Matches the annotation by its package and simple name, so a mistyped import counts too. */
  static final DescribedPredicate<JavaClass> HIBERNATE_FILTER_ANNOTATION =
      DescribedPredicate.describe(
          "a Hibernate filter annotation",
          type ->
              type.getPackageName().equals("org.hibernate.annotations")
                  && FILTER_ANNOTATIONS.contains(type.getSimpleName()));

  /**
   * Any Hibernate session receiver, which covers Session and StatelessSession, and the session
   * factory, which declares {@code getDefinedFilterNames}.
   */
  static final DescribedPredicate<JavaAccess<?>> SESSION_FILTER_CALL =
      DescribedPredicate.describe(
          "a call or a method reference that arms, disables or reads a Hibernate session filter",
          access ->
              (access instanceof JavaMethodCall || access instanceof JavaMethodReference)
                  && FILTER_CALLS.contains(access.getName())
                  && (access.getTargetOwner().isAssignableTo(SharedSessionContract.class)
                      || access.getTargetOwner().isAssignableTo(SessionFactory.class)));

  @ArchTest
  static final ArchRule no_class_declares_a_hibernate_filter =
      noClasses()
          .should()
          .dependOnClassesThat(HIBERNATE_FILTER_ANNOTATION)
          .because(
              "tenant isolation is the statement inspector's, a filter declaration scopes nothing");

  @ArchTest
  static final ArchRule no_class_arms_or_reads_a_session_filter =
      noClasses()
          .should()
          .accessTargetWhere(SESSION_FILTER_CALL)
          .because(
              "no filter is defined any more, so enabling one throws and disabling one widens"
                  + " nothing; a cross-tenant read goes through the scoped transaction primitive");
}
