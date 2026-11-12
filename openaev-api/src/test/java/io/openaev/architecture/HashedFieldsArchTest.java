package io.openaev.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.openaev.database.model.ContractOutputElement;
import io.openaev.database.model.DetectionRemediation;
import io.openaev.database.model.OutputParser;
import io.openaev.database.model.Payload;
import io.openaev.database.model.PayloadArgument;
import io.openaev.database.model.PayloadPrerequisite;
import io.openaev.database.model.RegexGroup;
import io.openaev.database.model.hash.Hashed;
import io.openaev.database.model.hash.NotHashed;
import jakarta.persistence.Transient;
import java.util.List;

@AnalyzeClasses(packages = "io.openaev", importOptions = ImportOption.DoNotIncludeTests.class)
class HashedFieldsArchTest {

  private static final List<Class<?>> HASHED_TYPES =
      List.of(
          Payload.class,
          PayloadArgument.class,
          PayloadPrerequisite.class,
          DetectionRemediation.class,
          OutputParser.class,
          ContractOutputElement.class,
          RegexGroup.class);

  private static final DescribedPredicate<JavaField> PERSISTENT_FIELD_OF_HASHED_TYPE =
      DescribedPredicate.describe(
          "persistent fields of the payload, its types and the objects hashed with it",
          field ->
              isHashedType(field.getOwner())
                  && !field.getModifiers().contains(JavaModifier.STATIC)
                  && !field.getModifiers().contains(JavaModifier.TRANSIENT)
                  && !field.isAnnotatedWith(Transient.class));

  @ArchTest
  static final ArchRule every_field_says_whether_it_enters_the_content_hash =
      fields()
          .that(PERSISTENT_FIELD_OF_HASHED_TYPE)
          .should(carryExactlyOneHashMark())
          .because(
              "a field nobody decided about would silently stay out of the content hash, and two"
                  + " different payloads would be linked as the same content");

  private static boolean isHashedType(JavaClass owner) {
    return HASHED_TYPES.stream().anyMatch(owner::isAssignableTo);
  }

  private static ArchCondition<JavaField> carryExactlyOneHashMark() {
    return new ArchCondition<>("carry exactly one of @Hashed or @NotHashed") {
      @Override
      public void check(JavaField field, ConditionEvents events) {
        boolean hashed = field.isAnnotatedWith(Hashed.class);
        boolean notHashed = field.isAnnotatedWith(NotHashed.class);
        if (hashed == notHashed) {
          events.add(
              SimpleConditionEvent.violated(
                  field, field.getFullName() + " must carry exactly one of @Hashed or @NotHashed"));
        }
      }
    };
  }
}
