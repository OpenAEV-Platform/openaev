package io.openaev.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.openaev.database.model.Payload;

@AnalyzeClasses(packages = "io.openaev", importOptions = ImportOption.DoNotIncludeTests.class)
class PayloadHashedFieldsArchTest {

  @ArchTest
  static final ArchRule every_payload_type_hashes_its_own_fields =
      classes()
          .that()
          .areAssignableTo(Payload.class)
          .and()
          .doNotHaveFullyQualifiedName(Payload.class.getName())
          .should(declareHashedFields())
          .because(
              "a payload type that does not write its own fields would hash like another type,"
                  + " and two different payloads would be linked as the same content");

  private static ArchCondition<JavaClass> declareHashedFields() {
    return new ArchCondition<>("declare hashedFields") {
      @Override
      public void check(JavaClass payloadType, ConditionEvents events) {
        boolean declared =
            payloadType.getMethods().stream()
                .anyMatch(method -> method.getName().equals("hashedFields"));
        if (!declared) {
          events.add(
              SimpleConditionEvent.violated(
                  payloadType, payloadType.getName() + " does not override hashedFields"));
        }
      }
    };
  }
}
