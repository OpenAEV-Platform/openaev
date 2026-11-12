package io.openaev.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.regex.Pattern;
import org.springframework.data.jpa.repository.Query;

@AnalyzeClasses(packages = "io.openaev", importOptions = ImportOption.DoNotIncludeTests.class)
class PayloadNativeDeleteArchTest {

  private static final Pattern DELETES_PAYLOADS =
      Pattern.compile("(?is)\bdelete\s+from\s+payloads?\b");

  @ArchTest
  static final ArchRule no_query_deletes_payloads =
      methods()
          .that()
          .areAnnotatedWith(Query.class)
          .should(notDeletePayloads())
          .because(
              "PayloadDeleteGuard refuses deleting a payload that injects run; a query delete"
                  + " skips it. Delete payloads through the repository's delete or deleteById");

  private static ArchCondition<JavaMethod> notDeletePayloads() {
    return new ArchCondition<>("not delete payloads") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        String query = method.getAnnotationOfType(Query.class).value();
        if (DELETES_PAYLOADS.matcher(query).find()) {
          events.add(
              SimpleConditionEvent.violated(method, method.getFullName() + " deletes payloads"));
        }
      }
    };
  }
}
