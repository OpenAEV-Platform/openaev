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
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.data.jpa.repository.Query;

@AnalyzeClasses(packages = "io.openaev", importOptions = ImportOption.DoNotIncludeTests.class)
class HookedDeleteArchTest {

  /** Tables whose delete runs a hook, and what the hook does. */
  private static final Map<String, String> HOOKS =
      Map.of(
          "payloads", "PayloadDeleteGuard refuses deleting a payload that injects run",
          "scenarios", "CatalogScenarioUninstallListener records the uninstall of a catalogue one");

  @ArchTest
  static final ArchRule no_query_deletes_a_hooked_table =
      methods()
          .that()
          .areAnnotatedWith(Query.class)
          .should(notDeleteAHookedTable())
          .because(
              "a query delete skips the hook of the rows it deletes. Delete them through the"
                  + " repository's delete or deleteById");

  private static ArchCondition<JavaMethod> notDeleteAHookedTable() {
    return new ArchCondition<>("not delete a table with a delete hook") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        String query = method.getAnnotationOfType(Query.class).value();
        HOOKS.forEach(
            (table, hook) -> {
              if (deletes(query, table)) {
                events.add(
                    SimpleConditionEvent.violated(
                        method, method.getFullName() + " deletes " + table + ": " + hook));
              }
            });
      }
    };
  }

  // A table or its entity, singular or plural: "delete from payloads", "DELETE FROM Scenario s".
  private static boolean deletes(String query, String table) {
    String singular = table.substring(0, table.length() - 1);
    return Pattern.compile("(?is)\\bdelete\\s+from\\s+" + singular + "s?\\b").matcher(query).find();
  }
}
