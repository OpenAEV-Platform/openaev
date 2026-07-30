package io.openaev.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.openaev.aop.AccessControl;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Guards the chaining API surface: every HTTP endpoint on a chaining controller must declare {@link
 * AccessControl}. The engine tables (workflow/step/condition) carry no tenant_id and are not
 * tenant-active (see {@code ChainingRbacIsolationTest}, #6357), so their cross-tenant protection is
 * the {@code @AccessControl} parent-permission chain that resolves each resource up to its
 * tenant-scoped simulation/scenario grant. There is no SQL-level tenant filter to catch a miss, so
 * a new chaining endpoint added without the annotation would leak silently. This test fails the
 * build in that case, which is the standing guardrail while the engine tables stay pre-v2.
 *
 * <p>Requiring the annotation to be present (whether it enforces RBAC or explicitly declares {@code
 * skipRBAC = true}) forces a conscious access-control decision on every new endpoint.
 */
@AnalyzeClasses(
    packages = "io.openaev.api.chaining",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ChainingEndpointsAccessControlArchTest {

  @ArchTest
  static final ArchRule chaining_http_endpoints_must_declare_access_control =
      methods()
          .that(
              new DescribedPredicate<JavaMethod>("are chaining HTTP endpoints") {
                @Override
                public boolean test(JavaMethod method) {
                  return method.isAnnotatedWith(GetMapping.class)
                      || method.isAnnotatedWith(PostMapping.class)
                      || method.isAnnotatedWith(PutMapping.class)
                      || method.isAnnotatedWith(DeleteMapping.class)
                      || method.isAnnotatedWith(PatchMapping.class)
                      || method.isAnnotatedWith(RequestMapping.class);
                }
              })
          .should(
              new ArchCondition<JavaMethod>("declare @AccessControl") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                  if (!method.isAnnotatedWith(AccessControl.class)) {
                    events.add(
                        SimpleConditionEvent.violated(
                            method,
                            "Chaining endpoint "
                                + method.getOwner().getSimpleName()
                                + "#"
                                + method.getName()
                                + " must declare @AccessControl: the engine tables have no SQL"
                                + " tenant filter, so the grant chain on the parent"
                                + " simulation/scenario is the only cross-tenant guard"));
                  }
                }
              });
}
