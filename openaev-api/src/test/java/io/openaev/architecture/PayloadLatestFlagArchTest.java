package io.openaev.architecture;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.openaev.database.model.Payload;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.service.payload.PayloadVersionService;

@AnalyzeClasses(packages = "io.openaev", importOptions = ImportOption.DoNotIncludeTests.class)
class PayloadLatestFlagArchTest {

  @ArchTest
  static final ArchRule only_payload_version_service_writes_latest_flag =
      noClasses()
          .that()
          .doNotBelongToAnyOf(PayloadVersionService.class)
          .should()
          .callMethodWhere(
              target(name("setLatest")).and(target(owner(assignableTo(Payload.class)))))
          .because("payload_is_latest has a single writer, PayloadVersionService.refreshLatest");

  @ArchTest
  static final ArchRule only_payload_version_service_lists_versions_of_an_external_id =
      noClasses()
          .that()
          .doNotBelongToAnyOf(PayloadVersionService.class)
          .should()
          .callMethod(PayloadRepository.class, "findAllByExternalId", String.class)
          .because("picking a version belongs to PayloadVersionService");
}
