package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import io.openaev.architecture.fixture.TenantActiveTableAccessFixtures;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.rest.injector_contract.InjectorContractService;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Proves the repository access rules of {@link TenantActiveTableAccessArchTest} fire on an accessor
 * they do not allowlist. The arch test runs them over production, where the allowlists keep them
 * green; here they run over fixtures, so a pass can only come from a REAL violation and never from
 * ArchUnit's empty-should error.
 */
@DisplayName("Tenant-active repository access rules: each one fires on an unreviewed accessor")
class TenantActiveTableAccessRulesTest {

  private static EvaluationResult evaluate(ArchRule rule, Class<?>... fixture) {
    return rule.evaluate(new ClassFileImporter().importClasses(fixture));
  }

  @Nested
  @DisplayName("autonomous_runs")
  class AutonomousRuns {

    @Test
    @DisplayName("given an accessor outside the allowlist, should violate")
    void given_anAccessorOutsideTheAllowlist_should_violate() {
      // Act
      EvaluationResult result =
          evaluate(
              TenantActiveTableAccessArchTest.autonomous_runs_repository_access_is_reviewed,
              TenantActiveTableAccessFixtures.UnreviewedAutonomousRunAccessor.class);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString())
          .contains("UnreviewedAutonomousRunAccessor")
          .contains("AutonomousRunRepository");
    }
  }

  @Nested
  @DisplayName("autonomous_objective_templates")
  class AutonomousObjectiveTemplates {

    @Test
    @DisplayName("given an accessor outside the allowlist, should violate")
    void given_anAccessorOutsideTheAllowlist_should_violate() {
      // Act
      EvaluationResult result =
          evaluate(
              TenantActiveTableAccessArchTest
                  .autonomous_objective_templates_repository_access_is_reviewed,
              TenantActiveTableAccessFixtures.UnreviewedObjectiveTemplateAccessor.class);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString())
          .contains("UnreviewedObjectiveTemplateAccessor")
          .contains("AutonomousObjectiveTemplateRepository");
    }
  }

  @Nested
  @DisplayName("parameters platform row writers")
  class ParametersPlatformRowWriters {

    @Test
    @DisplayName("given a writer reached from no marker, should violate")
    void given_aWriterReachedFromNoMarker_should_violate() {
      // Act
      EvaluationResult result =
          evaluate(
              TenantActiveTableAccessArchTest
                  .parameters_platform_row_writers_run_under_platform_write,
              TenantActiveTableAccessFixtures.UnmarkedPlatformRowWriter.class);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString())
          .contains("UnmarkedPlatformRowWriter.write")
          .contains("UnmarkedPlatformRowWriter.entry");
    }

    @Test
    @DisplayName("given a writer with one unmarked caller, should violate on that caller only")
    void given_aWriterWithOneUnmarkedCaller_should_violateOnThatCallerOnly() {
      // Act
      EvaluationResult result =
          evaluate(
              TenantActiveTableAccessArchTest
                  .parameters_platform_row_writers_run_under_platform_write,
              TenantActiveTableAccessFixtures.PlatformRowWriterWithAnUnmarkedCaller.class);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString())
          .contains("unmarkedEntry")
          .doesNotContain(".markedEntry(");
    }

    @Test
    @DisplayName("given writers under the annotation or the primitive, should not violate")
    void given_writersUnderTheAnnotationOrThePrimitive_should_notViolate() {
      // Act
      EvaluationResult annotated =
          evaluate(
              TenantActiveTableAccessArchTest
                  .parameters_platform_row_writers_run_under_platform_write,
              TenantActiveTableAccessFixtures.PlatformRowWriterUnderTheAnnotation.class);
      EvaluationResult primitive =
          evaluate(
              TenantActiveTableAccessArchTest
                  .parameters_platform_row_writers_run_under_platform_write,
              TenantActiveTableAccessFixtures.PlatformRowWriterUnderThePrimitive.class);

      // Assert
      assertThat(annotated.hasViolation()).as(annotated.getFailureReport().toString()).isFalse();
      assertThat(primitive.hasViolation()).as(primitive.getFailureReport().toString()).isFalse();
    }
  }

  @Nested
  @DisplayName("parameters platform row writers: holes of the guard")
  class ParametersPlatformRowWriterHoles {

    private EvaluationResult writers(Class<?>... fixtures) {
      return evaluate(
          TenantActiveTableAccessArchTest.parameters_platform_row_writers_run_under_platform_write,
          fixtures);
    }

    @Test
    @DisplayName("given a direct write after the primitive returned, should violate")
    void given_aDirectWriteAfterThePrimitiveReturned_should_violate() {
      // Act
      EvaluationResult result =
          writers(TenantActiveTableAccessFixtures.PlatformRowWriterAfterThePrimitiveReturned.class);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString())
          .contains("PlatformRowWriterAfterThePrimitiveReturned.entry");
    }

    @Test
    @DisplayName("given a lambda unrelated to the primitive in the same class, should violate")
    void given_aLambdaUnrelatedToThePrimitiveInTheSameClass_should_violate() {
      // Act
      EvaluationResult result =
          writers(
              TenantActiveTableAccessFixtures.UnrelatedLambdaInAClassThatOpensThePrimitive.class);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString())
          .contains("UnrelatedLambdaInAClassThatOpensThePrimitive.unrelated")
          .doesNotContain("UnrelatedLambdaInAClassThatOpensThePrimitive.marked");
    }

    @Test
    @DisplayName("given the current-transaction marker, should pass only for a write after it")
    void given_theCurrentTransactionMarker_should_passOnlyForAWriteAfterIt() {
      // Act
      EvaluationResult after =
          writers(
              TenantActiveTableAccessFixtures.PlatformRowWriterAfterTheCurrentTransactionMarker
                  .class);
      EvaluationResult before =
          writers(
              TenantActiveTableAccessFixtures.PlatformRowWriterBeforeTheCurrentTransactionMarker
                  .class);

      // Assert
      assertThat(after.hasViolation()).as(after.getFailureReport().toString()).isFalse();
      assertThat(before.hasViolation()).isTrue();
    }

    @Test
    @DisplayName("given the same write before and after the marker, should violate")
    void given_theSameWriteBeforeAndAfterTheMarker_should_violate() {
      // Act
      EvaluationResult result =
          writers(
              TenantActiveTableAccessFixtures
                  .PlatformRowWriterBeforeAndAfterTheCurrentTransactionMarker.class);

      // Assert
      assertThat(result.hasViolation()).isTrue();
    }

    @Test
    @DisplayName("given an annotation the aspect ignores, should violate")
    void given_anAnnotationTheAspectIgnores_should_violate() {
      // Act
      EvaluationResult withoutTransactional =
          writers(TenantActiveTableAccessFixtures.PlatformWriteWithoutTransactional.class);
      EvaluationResult withoutTxCtx =
          writers(TenantActiveTableAccessFixtures.PlatformWriteWithoutTxCtx.class);
      EvaluationResult honoured =
          writers(TenantActiveTableAccessFixtures.PlatformWriteHonoured.class);

      // Assert
      assertThat(withoutTransactional.hasViolation()).isTrue();
      assertThat(withoutTxCtx.hasViolation()).isTrue();
      assertThat(honoured.hasViolation()).as(honoured.getFailureReport().toString()).isFalse();
    }

    @Test
    @DisplayName("given a write other than save or delete, should violate")
    void given_aWriteOtherThanSaveOrDelete_should_violate() {
      // Act
      EvaluationResult entityManager =
          writers(TenantActiveTableAccessFixtures.PlatformRowEntityManagerWriter.class);
      EvaluationResult inherited =
          writers(TenantActiveTableAccessFixtures.PlatformRowInheritedWriter.class);

      // Assert
      assertThat(entityManager.hasViolation()).isTrue();
      assertThat(inherited.hasViolation()).isTrue();
    }

    @Test
    @DisplayName("given an entity manager write of another entity, should not be a writer")
    void given_anEntityManagerWriteOfAnotherEntity_should_notBeAWriter() {
      // Arrange
      var writer =
          new ClassFileImporter()
              .importClasses(
                  TenantActiveTableAccessFixtures.EntityManagerWriterOfAnotherEntity.class,
                  TenantActiveTableAccessFixtures.PlatformRowEntityManagerWriter.class);

      // Act
      var writers =
          writer.stream()
              .flatMap(c -> c.getMethods().stream())
              .filter(PlatformRowWriters.writeASetting(java.util.List.of()))
              .map(m -> m.getOwner().getSimpleName())
              .toList();

      // Assert
      assertThat(writers).containsExactly("PlatformRowEntityManagerWriter");
    }
  }

  @Nested
  @DisplayName("parameters platform row writers: every shape of write")
  class ParametersPlatformRowWriterShapes {

    private EvaluationResult writers(Class<?>... fixtures) {
      return evaluate(
          TenantActiveTableAccessArchTest.parameters_platform_row_writers_run_under_platform_write,
          fixtures);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unmarkedShapes")
    @DisplayName("given a write of the table in this shape reached from no marker, should violate")
    void given_aWriteOfTheTableInThisShape_should_violate(Class<?> fixture) {
      // Act
      EvaluationResult result = writers(fixture);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString()).contains(fixture.getSimpleName());
    }

    static Stream<Class<?>> unmarkedShapes() {
      return Stream.of(
          TenantActiveTableAccessFixtures.PlatformRowDirtyCheckedWriter.class,
          TenantActiveTableAccessFixtures.PlatformRowHqlWriter.class,
          TenantActiveTableAccessFixtures.PlatformRowNativeWriter.class,
          TenantActiveTableAccessFixtures.PlatformRowJdbcWriter.class,
          TenantActiveTableAccessFixtures.PlatformRowSessionWriter.class);
    }

    /**
     * No fixture can carry {@code @Modifying} here (the module forbids repository annotations
     * outside {@code openaev-model}), so the statement match a {@code @Modifying @Query} of another
     * repository goes through is tested on its own.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource(
        delimiter = '|',
        value = {
          "update Setting s set s.value = :v where s.key = :k|true",
          "DELETE FROM parameters WHERE parameter_key = ?1|true",
          "insert into parameters (parameter_id) values (?1)|true",
          "update \"parameters\" set parameter_value = 'x'|true",
          "delete from Exercise e where e.id = :id|false",
          "select s from Setting s where s.key = :k|false",
          "update exercises set exercise_name = 'parameters'|false"
        })
    @DisplayName("given a statement, should recognise a write of the table")
    void given_aStatement_should_recogniseAWriteOfTheTable(String statement, boolean write) {
      // Act + Assert
      assertThat(PlatformRowWriters.isWriteOnTheTable(statement)).isEqualTo(write);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("methodReferenceShapes")
    @DisplayName("given a write through a method reference reached from no marker, should violate")
    void given_aWriteThroughAMethodReference_should_violate(Class<?> fixture) {
      // Act
      EvaluationResult result = writers(fixture);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString()).contains(fixture.getSimpleName() + ".entry");
    }

    static Stream<Class<?>> methodReferenceShapes() {
      return Stream.of(
          TenantActiveTableAccessFixtures.PlatformRowMethodReferenceDeleter.class,
          TenantActiveTableAccessFixtures.PlatformRowMethodReferenceSaver.class,
          TenantActiveTableAccessFixtures.PlatformRowSetterMethodReference.class);
    }

    @Test
    @DisplayName(
        "given method references created in the platform-write callback, should not violate")
    void given_methodReferencesInThePlatformWriteCallback_should_notViolate() {
      // Act
      EvaluationResult result =
          writers(
              TenantActiveTableAccessFixtures.PlatformRowMethodReferencesUnderThePrimitive.class);

      // Assert
      assertThat(result.hasViolation()).as(result.getFailureReport().toString()).isFalse();
    }

    @Test
    @DisplayName("given a constructor reference of a setting, should not be a writer")
    void given_aConstructorReferenceOfASetting_should_notBeAWriter() {
      // Arrange
      var classes =
          new ClassFileImporter()
              .importClasses(TenantActiveTableAccessFixtures.SettingConstructorReference.class);

      // Act
      var writers =
          classes.stream()
              .flatMap(c -> c.getMethods().stream())
              .filter(PlatformRowWriters.writeASetting(java.util.List.of()))
              .toList();

      // Assert
      assertThat(writers).isEmpty();
    }

    @Test
    @DisplayName("given statements and reads that do not write the table, should not be writers")
    void given_statementsAndReadsThatDoNotWriteTheTable_should_notBeWriters() {
      // Arrange
      var classes =
          new ClassFileImporter()
              .importClasses(
                  TenantActiveTableAccessFixtures.StatementsThatAreNotPlatformRowWriters.class);

      // Act
      var writers =
          classes.stream()
              .flatMap(c -> c.getMethods().stream())
              .filter(PlatformRowWriters.writeASetting(java.util.List.of()))
              .toList();

      // Assert
      assertThat(writers).isEmpty();
    }

    @Test
    @DisplayName("given every new shape inside the platform-write callback, should not violate")
    void given_everyNewShapeInsideThePlatformWriteCallback_should_notViolate() {
      // Act
      EvaluationResult result =
          writers(TenantActiveTableAccessFixtures.PlatformRowWritersUnderThePrimitive.class);

      // Assert
      assertThat(result.hasViolation()).as(result.getFailureReport().toString()).isFalse();
    }

    @Test
    @DisplayName("given a service that writes on behalf of its callers, should check the callers")
    void given_aServiceThatWritesOnBehalfOfItsCallers_should_checkTheCallers() {
      // Arrange
      var classes =
          new ClassFileImporter()
              .importClasses(
                  io.openaev.service.PlatformSettingsService.class,
                  TenantActiveTableAccessFixtures.UnmarkedCallerOfThePlatformSettingsService.class);

      // Act
      EvaluationResult result =
          TenantActiveTableAccessArchTest.parameters_platform_row_writers_run_under_platform_write
              .evaluate(classes);

      // Assert
      assertThat(result.getFailureReport().toString())
          .contains("UnmarkedCallerOfThePlatformSettingsService");
    }
  }

  @Nested
  @DisplayName("parameters platform row writers: exemptions of tenant-row-only writers")
  class ParametersTenantRowWriterExemptions {

    private final List<PlatformRowWriters.TenantRowWriter> threeArgumentUpsert =
        List.of(
            new PlatformRowWriters.TenantRowWriter(
                TenantActiveTableAccessFixtures.TenantRowWriterWithAPlatformOverload.class,
                "upsert",
                List.of(Tenant.class, String.class, String.class),
                "builds the row for the tenant it is given"));

    private List<String> writersOf(Class<?> fixture, List<PlatformRowWriters.TenantRowWriter> ex) {
      return new ClassFileImporter()
          .importClasses(fixture).stream()
              .flatMap(c -> c.getMethods().stream())
              .filter(PlatformRowWriters.writeASetting(ex))
              .map(m -> m.getName() + "/" + m.getRawParameterTypes().size())
              .toList();
    }

    @Test
    @DisplayName("given an exemption with a signature, should exempt that overload only")
    void given_anExemptionWithASignature_should_exemptThatOverloadOnly() {
      // Act
      List<String> exempted =
          writersOf(
              TenantActiveTableAccessFixtures.TenantRowWriterWithAPlatformOverload.class,
              threeArgumentUpsert);
      List<String> unexempted =
          writersOf(
              TenantActiveTableAccessFixtures.TenantRowWriterWithAPlatformOverload.class,
              List.of());

      // Assert
      assertThat(exempted).containsExactly("upsert/2");
      assertThat(unexempted).containsExactlyInAnyOrder("upsert/2", "upsert/3");
    }

    @Test
    @DisplayName("given an exemption, should match the method that has that signature")
    void given_anExemption_should_matchTheMethodThatHasThatSignature() {
      // Arrange
      var classes =
          new ClassFileImporter()
              .importClasses(
                  TenantActiveTableAccessFixtures.TenantRowWriterWithAPlatformOverload.class);

      // Act
      List<Integer> matched =
          classes.stream()
              .flatMap(c -> c.getMethods().stream())
              .filter(m -> PlatformRowWriters.isTenantRowWriter(m, threeArgumentUpsert))
              .map(m -> m.getRawParameterTypes().size())
              .toList();

      // Assert
      assertThat(matched).containsExactly(3);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
      "TenantRowWriterThatStaysTenantOnly,0",
      "TenantRowWriterBuildingARowWithoutATenant,1",
      "TenantRowWriterLookingUpThePlatformRow,1",
      "TenantRowWriterDeletingByKeyOnly,1"
    })
    @DisplayName("given an exempt writer, should report what takes it out of the tenant rows")
    void given_anExemptWriter_should_reportWhatTakesItOutOfTheTenantRows(
        String fixture, int violations) throws ClassNotFoundException {
      // Arrange
      Class<?> type =
          Class.forName(TenantActiveTableAccessFixtures.class.getName() + "$" + fixture);
      var write =
          new ClassFileImporter()
              .importClasses(type).stream()
                  .flatMap(c -> c.getMethods().stream())
                  .filter(m -> m.getName().equals("write"))
                  .findFirst()
                  .orElseThrow();

      // Act
      List<String> found = PlatformRowWriters.tenantRowOnlyViolations(write);

      // Assert
      assertThat(found).as(found.toString()).hasSize(violations);
    }
  }

  @Nested
  @DisplayName("platform_write_is_honoured_only_on_a_transactional_method_with_a_tx_ctx")
  class PlatformWriteMarker {

    private EvaluationResult marker(Class<?> fixture) {
      return evaluate(
          TenantActiveTableAccessArchTest
              .platform_write_is_honoured_only_on_a_transactional_method_with_a_tx_ctx,
          fixture);
    }

    @Test
    @DisplayName("given a method the aspect would ignore, should violate")
    void given_aMethodTheAspectWouldIgnore_should_violate() {
      // Act
      EvaluationResult withoutTransactional =
          marker(TenantActiveTableAccessFixtures.PlatformWriteWithoutTransactional.class);
      EvaluationResult withoutTxCtx =
          marker(TenantActiveTableAccessFixtures.PlatformWriteWithoutTxCtx.class);

      // Assert
      assertThat(withoutTransactional.hasViolation()).isTrue();
      assertThat(withoutTxCtx.hasViolation()).isTrue();
    }

    @Test
    @DisplayName("given a transactional method with a TxCtx, should not violate")
    void given_aTransactionalMethodWithATxCtx_should_notViolate() {
      // Act
      EvaluationResult result = marker(TenantActiveTableAccessFixtures.PlatformWriteHonoured.class);

      // Assert
      assertThat(result.hasViolation()).as(result.getFailureReport().toString()).isFalse();
    }
  }

  @Nested
  @DisplayName("parameters_tenant_row_readers_off_the_request_thread")
  class ParametersTenantRowReaders {

    private EvaluationResult readers(Class<?>... fixtures) {
      return evaluate(
          TenantActiveTableAccessArchTest.parameters_tenant_row_readers_off_the_request_thread,
          fixtures);
    }

    @Test
    @DisplayName("given a read through a method reference from an unscoped root, should violate")
    void given_aReadThroughAMethodReference_should_violate() {
      // Act
      EvaluationResult result =
          readers(TenantActiveTableAccessFixtures.TenantRowReaderByMethodReference.class);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString())
          .contains("TenantRowReaderByMethodReference.job");
    }

    @Test
    @DisplayName("given a reader reached from a root that sets no scope, should violate")
    void given_aReaderReachedFromARootThatSetsNoScope_should_violate() {
      // Act
      EvaluationResult result =
          readers(TenantActiveTableAccessFixtures.TenantRowReaderFromAnUnscopedRoot.class);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString())
          .contains("TenantRowReaderFromAnUnscopedRoot.read")
          .contains("TenantRowReaderFromAnUnscopedRoot.job");
    }

    @Test
    @DisplayName("given a transactional method with a TxCtx, should not violate")
    void given_aTransactionalMethodWithATxCtx_should_notViolate() {
      // Act
      EvaluationResult result =
          readers(TenantActiveTableAccessFixtures.TenantRowReaderUnderATxCtx.class);

      // Assert
      assertThat(result.hasViolation()).as(result.getFailureReport().toString()).isFalse();
    }

    @Test
    @DisplayName("given the read inside a primitive callback or its helper, should not violate")
    void given_theReadInsideAPrimitiveCallbackOrItsHelper_should_notViolate() {
      // Act
      EvaluationResult direct =
          readers(TenantActiveTableAccessFixtures.TenantRowReaderInThePrimitiveCallback.class);
      EvaluationResult helper =
          readers(TenantActiveTableAccessFixtures.TenantRowReaderInAHelperCallback.class);

      // Assert
      assertThat(direct.hasViolation()).as(direct.getFailureReport().toString()).isFalse();
      assertThat(helper.hasViolation()).as(helper.getFailureReport().toString()).isFalse();
    }

    @Test
    @DisplayName("given a read after the primitive returned, should violate")
    void given_aReadAfterThePrimitiveReturned_should_violate() {
      // Act
      EvaluationResult result =
          readers(TenantActiveTableAccessFixtures.TenantRowReaderAfterThePrimitiveReturned.class);

      // Assert
      assertThat(result.hasViolation()).isTrue();
    }

    @Test
    @DisplayName("given a read of the platform row only, should not be a reader")
    void given_aReadOfThePlatformRowOnly_should_notBeAReader() {
      // Arrange
      var classes =
          new ClassFileImporter()
              .importClasses(TenantActiveTableAccessFixtures.PlatformRowReaderOnly.class);

      // Act
      var readers =
          classes.stream()
              .flatMap(c -> c.getMethods().stream())
              .filter(PlatformRowWriters.readATenantRowOfSettings())
              .toList();

      // Assert
      assertThat(readers).isEmpty();
    }
  }

  @Nested
  @DisplayName("injectors_contracts: queries that take no tenant")
  class InjectorContractTenantlessQuery {

    static Stream<Arguments> tenantlessAccessors() {
      return Stream.of(
          Arguments.of(TenantActiveTableAccessFixtures.DeclaredBareIdLookup.class, "findById"),
          Arguments.of(
              TenantActiveTableAccessFixtures.DeclaredPayloadLookup.class,
              "findInjectorContractByPayload"),
          Arguments.of(TenantActiveTableAccessFixtures.SpecificationRead.class, "findAll"),
          Arguments.of(TenantActiveTableAccessFixtures.ArglessCrudRead.class, "count"),
          Arguments.of(TenantActiveTableAccessFixtures.BareIdLookupReference.class, "findById"),
          Arguments.of(
              TenantActiveTableAccessFixtures.ServiceBareIdLookup.class, "injectorContract"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tenantlessAccessors")
    @DisplayName("given an accessor outside the allowlist, should violate")
    void given_anAccessorOutsideTheAllowlist_should_violate(Class<?> fixture, String method) {
      // Act
      EvaluationResult result =
          evaluate(
              TenantActiveTableAccessArchTest.injector_contract_tenantless_query_is_called_only_by,
              fixture);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString()).contains(method);
    }

    @Test
    @DisplayName("given a new lookup in a class with a reviewed method, should violate on it only")
    void given_aNewLookupInAClassWithAReviewedMethod_should_violateOnTheNewMethodOnly() {
      // Arrange
      Class<?> fixture = TenantActiveTableAccessFixtures.ReviewedAndUnreviewedLookup.class;
      ArchRule rule =
          TenantActiveTableAccessArchTest.tenantlessInjectorContractQueryIsCalledOnlyBy(
              new TenantActiveTableAccessArchTest.AllowedCall(
                  fixture, "approvedLookup", "findById", "the fixture's reviewed method"));

      // Act
      EvaluationResult result = evaluate(rule, fixture);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString())
          .contains("newLookup")
          .doesNotContain("approvedLookup");
    }

    @Test
    @DisplayName("given an access that carries a tenant, should not violate")
    void given_anAccessThatCarriesATenant_should_notViolate() {
      // Act
      EvaluationResult result =
          evaluate(
              TenantActiveTableAccessArchTest.injector_contract_tenantless_query_is_called_only_by,
              TenantActiveTableAccessFixtures.TenantCarryingAccess.class);

      // Assert
      assertThat(result.hasViolation()).isFalse();
    }
  }

  @Nested
  @DisplayName("injectors_contracts: the pin an allowlist reason relies on")
  class InjectorContractPinOrder {

    private static TenantActiveTableAccessArchTest.AllowedCall pinnedCall(Class<?> fixture) {
      return new TenantActiveTableAccessArchTest.AllowedCall(
          fixture,
          "find",
          "injectorContract",
          "the fixture's endpoint pins first",
          TenantActiveTableAccessArchTest.Pin.requireSingleTenant(
              fixture, "find", InjectorContractService.class, "injectorContract"));
    }

    static Stream<Arguments> pinsThatDoNotComeFirst() {
      return Stream.of(
          Arguments.of(TenantActiveTableAccessFixtures.PinAfterLookup.class, "after"),
          Arguments.of(TenantActiveTableAccessFixtures.LookupWithoutPin.class, "never calls"),
          Arguments.of(TenantActiveTableAccessFixtures.PinInAnotherMethod.class, "never calls"),
          Arguments.of(TenantActiveTableAccessFixtures.PinInsideIf.class, "only after a branch"),
          Arguments.of(TenantActiveTableAccessFixtures.PinInsideTry.class, "only after a branch"),
          Arguments.of(TenantActiveTableAccessFixtures.PinInsideLambda.class, "never calls"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pinsThatDoNotComeFirst")
    @DisplayName(
        "given a pin that is missing, conditional, in a lambda or after the lookup, should violate")
    void given_aPinThatDoesNotAlwaysRunFirst_should_violate(Class<?> fixture, String expected) {
      // Act
      EvaluationResult result =
          evaluate(
              TenantActiveTableAccessArchTest.pinnedCallersPinBeforeTheLookup(pinnedCall(fixture)),
              fixture);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString()).contains(expected);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        classes = {
          TenantActiveTableAccessFixtures.PinBeforeLookup.class,
          TenantActiveTableAccessFixtures.PinBeforeABranch.class
        })
    @DisplayName("given a pin called first, before the lookup, should not violate")
    void given_aPinCalledFirstBeforeTheLookup_should_notViolate(Class<?> fixture) {
      // Act
      EvaluationResult result =
          evaluate(
              TenantActiveTableAccessArchTest.pinnedCallersPinBeforeTheLookup(pinnedCall(fixture)),
              fixture);

      // Assert
      assertThat(result.hasViolation()).isFalse();
    }

    @Test
    @DisplayName("given a pin whose class is not analysed, should violate instead of passing")
    void given_aPinWhoseClassIsNotAnalysed_should_violate() {
      // Arrange
      ArchRule rule =
          TenantActiveTableAccessArchTest.pinnedCallersPinBeforeTheLookup(
              pinnedCall(TenantActiveTableAccessFixtures.PinBeforeLookup.class));

      // Act
      EvaluationResult result =
          evaluate(rule, TenantActiveTableAccessFixtures.LookupWithoutPin.class);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString()).contains("was not analysed");
    }
  }

  @Nested
  @DisplayName("injectors_contracts: the callers and routes an allowlist reason relies on")
  class InjectorContractCallersAndRoutes {

    private final Class<?> shapes = TenantActiveTableAccessFixtures.CallerShapes.class;

    private TenantActiveTableAccessArchTest.CallersClaim claimOf(String... callers) {
      return new TenantActiveTableAccessArchTest.CallersClaim(
          shapes,
          "claimed",
          Stream.of(callers)
              .map(caller -> new TenantActiveTableAccessArchTest.At(shapes, caller))
              .toList());
    }

    @Test
    @DisplayName("given a call from a method the claim does not list, should violate on it only")
    void given_aCallFromAnUnlistedMethod_should_violateOnItOnly() {
      // Act
      EvaluationResult result =
          evaluate(TenantActiveTableAccessArchTest.callersAreOnly(claimOf("listed")), shapes);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString())
          .contains("unlisted")
          .doesNotContain("CallerShapes.listed()");
    }

    @Test
    @DisplayName("given every caller listed, should not violate")
    void given_everyCallerListed_should_notViolate() {
      // Act
      EvaluationResult result =
          evaluate(
              TenantActiveTableAccessArchTest.callersAreOnly(claimOf("listed", "unlisted")),
              shapes);

      // Assert
      assertThat(result.hasViolation()).isFalse();
    }

    @Test
    @DisplayName("given a listed caller that no longer makes the call, should violate")
    void given_aListedCallerThatNoLongerCalls_should_violate() {
      // Act
      EvaluationResult result =
          evaluate(
              TenantActiveTableAccessArchTest.callersAreOnly(
                  claimOf("listed", "unlisted", "renamed")),
              shapes);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString()).contains("renamed");
    }

    @Test
    @DisplayName("given a claim of no caller whose class is not analysed, should violate")
    void given_aClaimOfNoCallerWhoseClassIsNotAnalysed_should_violate() {
      // Act
      EvaluationResult result =
          evaluate(
              TenantActiveTableAccessArchTest.callersAreOnly(claimOf()),
              TenantActiveTableAccessFixtures.MethodRoutes.class);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString()).contains("was not analysed");
    }

    @Test
    @DisplayName("given an endpoint mapped on the tenant route only, should not violate")
    void given_anEndpointMappedOnTheTenantRouteOnly_should_notViolate() {
      // Act
      EvaluationResult result =
          evaluate(
              TenantActiveTableAccessArchTest.mappedOnTheTenantRouteOnly(
                  new TenantActiveTableAccessArchTest.RouteClaim(
                      TenantActiveTableAccessFixtures.MethodRoutes.class, "tenantOnly")),
              TenantActiveTableAccessFixtures.MethodRoutes.class);

      // Assert
      assertThat(result.hasViolation()).isFalse();
    }

    @Test
    @DisplayName("given an endpoint also mapped on the short route, should violate")
    void given_anEndpointAlsoMappedOnTheShortRoute_should_violate() {
      // Act
      EvaluationResult result =
          evaluate(
              TenantActiveTableAccessArchTest.mappedOnTheTenantRouteOnly(
                  new TenantActiveTableAccessArchTest.RouteClaim(
                      TenantActiveTableAccessFixtures.MethodRoutes.class, "bothRoutes")),
              TenantActiveTableAccessFixtures.MethodRoutes.class);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString()).contains("/api/things");
    }

    @Test
    @DisplayName("given a method with no mapping, should violate")
    void given_aMethodWithNoMapping_should_violate() {
      // Act
      EvaluationResult result =
          evaluate(
              TenantActiveTableAccessArchTest.mappedOnTheTenantRouteOnly(
                  new TenantActiveTableAccessArchTest.RouteClaim(
                      TenantActiveTableAccessFixtures.MethodRoutes.class, "unmapped")),
              TenantActiveTableAccessFixtures.MethodRoutes.class);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString()).contains("no mapping");
    }

    @Test
    @DisplayName("given a route that comes from the class mapping, should follow its prefix")
    void given_aRouteFromTheClassMapping_should_followItsPrefix() {
      // Act
      EvaluationResult tenant =
          evaluate(
              TenantActiveTableAccessArchTest.mappedOnTheTenantRouteOnly(
                  new TenantActiveTableAccessArchTest.RouteClaim(
                      TenantActiveTableAccessFixtures.TenantPrefixedClassRoute.class, "export")),
              TenantActiveTableAccessFixtures.TenantPrefixedClassRoute.class);
      EvaluationResult shortRoute =
          evaluate(
              TenantActiveTableAccessArchTest.mappedOnTheTenantRouteOnly(
                  new TenantActiveTableAccessArchTest.RouteClaim(
                      TenantActiveTableAccessFixtures.ShortClassRoute.class, "export")),
              TenantActiveTableAccessFixtures.ShortClassRoute.class);

      // Assert
      assertThat(tenant.hasViolation()).isFalse();
      assertThat(shortRoute.hasViolation()).isTrue();
    }

    @Test
    @DisplayName("given a route claim whose class is not analysed, should violate")
    void given_aRouteClaimWhoseClassIsNotAnalysed_should_violate() {
      // Act
      EvaluationResult result =
          evaluate(
              TenantActiveTableAccessArchTest.mappedOnTheTenantRouteOnly(
                  new TenantActiveTableAccessArchTest.RouteClaim(
                      TenantActiveTableAccessFixtures.MethodRoutes.class, "tenantOnly")),
              shapes);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString()).contains("was not analysed");
    }
  }

  @Nested
  @DisplayName("injectors_contracts: the scope of the methods a class is allowlisted for")
  class InjectorContractScopedMethods {

    private final Class<?> scoped = TenantActiveTableAccessFixtures.ScopedCallers.class;
    private final Class<?> unscoped = TenantActiveTableAccessFixtures.UnscopedCallers.class;

    private static TenantActiveTableAccessArchTest.CallersClaim claim(
        Class<?> owner, TenantActiveTableAccessArchTest.At... callers) {
      return new TenantActiveTableAccessArchTest.CallersClaim(owner, "target", List.of(callers));
    }

    private static TenantActiveTableAccessArchTest.At at(
        Class<?> in, String method, TenantActiveTableAccessArchTest.Scope scope) {
      return new TenantActiveTableAccessArchTest.At(in, method, scope);
    }

    @Test
    @DisplayName(
        "given callers that take a TxCtx or open a scope around the call, should not violate")
    void given_callersThatRunUnderAScope_should_notViolate() {
      // Arrange
      ArchRule rule =
          TenantActiveTableAccessArchTest.callersAreOnly(
              claim(
                  scoped,
                  at(scoped, "withTxCtx", TenantActiveTableAccessArchTest.Scope.TX_CTX_ENTRY_POINT),
                  at(
                      scoped,
                      "openedAroundLambda",
                      TenantActiveTableAccessArchTest.Scope.OPENED_AROUND_LAMBDA)));

      // Act
      EvaluationResult result = evaluate(rule, scoped);

      // Assert
      assertThat(result.hasViolation()).isFalse();
    }

    @Test
    @DisplayName("given a caller that takes no TxCtx, should violate")
    void given_aCallerWithoutATxCtx_should_violate() {
      // Arrange
      ArchRule rule =
          TenantActiveTableAccessArchTest.callersAreOnly(
              claim(
                  unscoped,
                  at(
                      unscoped,
                      "withoutTxCtx",
                      TenantActiveTableAccessArchTest.Scope.TX_CTX_ENTRY_POINT)));

      // Act
      EvaluationResult result = evaluate(rule, unscoped);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString())
          .contains("withoutTxCtx")
          .contains("is entered only through a public @Transactional method that takes a TxCtx");
    }

    @Test
    @DisplayName("given a call made outside the lambda that opens the scope, should violate")
    void given_aCallOutsideTheLambdaThatOpensTheScope_should_violate() {
      // Arrange
      ArchRule rule =
          TenantActiveTableAccessArchTest.callersAreOnly(
              claim(
                  unscoped,
                  at(
                      unscoped,
                      "outsideTheLambda",
                      TenantActiveTableAccessArchTest.Scope.OPENED_AROUND_LAMBDA)));

      // Act
      EvaluationResult result = evaluate(rule, unscoped);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString()).contains("outsideTheLambda");
    }

    @Test
    @DisplayName("given a lambda in a method that opens no scope, should violate")
    void given_aLambdaInAMethodThatOpensNoScope_should_violate() {
      // Arrange
      ArchRule rule =
          TenantActiveTableAccessArchTest.callersAreOnly(
              claim(
                  unscoped,
                  at(
                      unscoped,
                      "lambdaWithNoScope",
                      TenantActiveTableAccessArchTest.Scope.OPENED_AROUND_LAMBDA)));

      // Act
      EvaluationResult result = evaluate(rule, unscoped);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString()).contains("lambdaWithNoScope");
    }

    @Test
    @DisplayName(
        "given a method that opens a scope and makes the call in another lambda, should violate")
    void given_aCallInALambdaBesideTheScope_should_violate() {
      // Arrange
      ArchRule rule =
          TenantActiveTableAccessArchTest.callersAreOnly(
              claim(
                  unscoped,
                  at(
                      unscoped,
                      "executeBesideAnUnrelatedLambda",
                      TenantActiveTableAccessArchTest.Scope.OPENED_AROUND_LAMBDA)));

      // Act
      EvaluationResult result = evaluate(rule, unscoped);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString()).contains("executeBesideAnUnrelatedLambda");
    }

    @Test
    @DisplayName("given a caller that takes a TxCtx and is not transactional, should violate")
    void given_aTxCtxCallerThatIsNotTransactional_should_violate() {
      // Arrange
      ArchRule rule =
          TenantActiveTableAccessArchTest.callersAreOnly(
              claim(
                  unscoped,
                  at(
                      unscoped,
                      "txCtxWithoutTransactional",
                      TenantActiveTableAccessArchTest.Scope.TX_CTX_ENTRY_POINT)));

      // Act
      EvaluationResult result = evaluate(rule, unscoped);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString()).contains("txCtxWithoutTransactional");
    }

    @Test
    @DisplayName(
        "given a transactional TxCtx caller called only from its own class, should violate")
    void given_aTxCtxCallerCalledOnlyFromItsOwnClass_should_violate() {
      // Arrange
      Class<?> fixture = TenantActiveTableAccessFixtures.SelfCalledTxCtxHelper.class;
      ArchRule rule =
          TenantActiveTableAccessArchTest.callersAreOnly(
              claim(
                  fixture,
                  at(fixture, "helper", TenantActiveTableAccessArchTest.Scope.TX_CTX_ENTRY_POINT),
                  at(fixture, "entry", null)));

      // Act
      EvaluationResult result = evaluate(rule, fixture);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString()).contains("helper");
    }

    @Test
    @DisplayName("given an overloaded caller claimed by its name alone, should violate")
    void given_anOverloadedCallerClaimedByItsNameAlone_should_violate() {
      // Arrange
      Class<?> fixture = TenantActiveTableAccessFixtures.OverloadedCallers.class;
      ArchRule rule =
          TenantActiveTableAccessArchTest.callersAreOnly(
              claim(fixture, at(fixture, "caller", null)));

      // Act
      EvaluationResult result = evaluate(rule, fixture);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString()).contains("caller");
    }

    @Test
    @DisplayName(
        "given an overloaded caller claimed with its parameter types, should violate on the other"
            + " overload only")
    void given_anOverloadClaimedWithItsParameterTypes_should_violateOnTheOtherOverloadOnly() {
      // Arrange
      Class<?> fixture = TenantActiveTableAccessFixtures.OverloadedCallers.class;
      ArchRule rule =
          TenantActiveTableAccessArchTest.callersAreOnly(
              claim(
                  fixture,
                  new TenantActiveTableAccessArchTest.At(
                      fixture,
                      "caller",
                      TenantActiveTableAccessArchTest.Scope.TX_CTX_ENTRY_POINT,
                      List.of(TxCtx.class))));

      // Act
      EvaluationResult result = evaluate(rule, fixture);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString())
          .contains("OverloadedCallers.caller()")
          .doesNotContain("OverloadedCallers.caller(io.openaev.context.TxCtx)");
    }

    @Test
    @DisplayName("given an unlisted caller of a scoped method, should violate on it only")
    void given_anUnlistedCallerOfAScopedMethod_should_violateOnItOnly() {
      // Arrange
      ArchRule rule =
          TenantActiveTableAccessArchTest.callersAreOnly(
              claim(
                  scoped,
                  at(
                      scoped,
                      "withTxCtx",
                      TenantActiveTableAccessArchTest.Scope.TX_CTX_ENTRY_POINT)));

      // Act
      EvaluationResult result = evaluate(rule, scoped);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString())
          .contains("openedAroundLambda")
          .doesNotContain("ScopedCallers.withTxCtx()");
    }

    @Test
    @DisplayName(
        "given a method outside the listed ones that reaches the table, should violate on it only")
    void given_aMethodOutsideTheListedOnes_should_violateOnItOnly() {
      // Arrange
      Class<?> fixture = TenantActiveTableAccessFixtures.ListedAndUnlistedReach.class;

      // Act
      EvaluationResult result = evaluateReach(fixture, fixture, "listed");

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString())
          .contains("unlisted")
          .doesNotContain("ListedAndUnlistedReach.listed()");
    }

    @Test
    @DisplayName("given a listed method that does not reach the table, should violate")
    void given_aListedMethodThatDoesNotReachTheTable_should_violate() {
      // Arrange
      Class<?> fixture = TenantActiveTableAccessFixtures.ListedAndUnlistedReach.class;

      // Act
      EvaluationResult result = evaluateReach(fixture, fixture, "listed", "unlisted", "renamed");

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString()).contains("renamed");
    }

    @Test
    @DisplayName("given a listed class that is not analysed, should violate")
    void given_aListedClassThatIsNotAnalysed_should_violate() {
      // Act
      EvaluationResult result =
          evaluateReach(
              TenantActiveTableAccessFixtures.ListedAndUnlistedReach.class,
              TenantActiveTableAccessFixtures.TxCtxEntryPoint.class,
              "listed",
              "unlisted");

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString()).contains("was not analysed");
    }

    private static EvaluationResult evaluateReach(
        Class<?> listed, Class<?> analysed, String... methods) {
      return evaluate(
          TenantActiveTableAccessArchTest.tableIsReachedOnlyThrough(
              "the contract repository",
              access -> access.getTargetOwner().isAssignableTo(InjectorContractRepository.class),
              new TenantActiveTableAccessArchTest.Reaching(listed, List.of(methods))),
          analysed);
    }

    private static EvaluationResult evaluateEntryPoints(Class<?> in, String... methods) {
      return evaluate(
          TenantActiveTableAccessArchTest.methodsReachingTheTableTakeATxCtx(
              new TenantActiveTableAccessArchTest.Reaching(in, List.of(methods))),
          in);
    }

    @Test
    @DisplayName(
        "given a non-private method with a TxCtx that leads to the table, should not violate")
    void given_aTxCtxEntryPointThatLeadsToTheTable_should_notViolate() {
      // Arrange
      Class<?> fixture = TenantActiveTableAccessFixtures.TxCtxEntryPoint.class;

      // Act
      EvaluationResult result = evaluateEntryPoints(fixture, "helper");

      // Assert
      assertThat(result.hasViolation()).isFalse();
    }

    @Test
    @DisplayName("given an entry point that takes a TxCtx and is not transactional, should violate")
    void given_aTxCtxEntryPointThatIsNotTransactional_should_violate() {
      // Arrange
      Class<?> fixture = TenantActiveTableAccessFixtures.TxCtxEntryPointWithoutTransactional.class;

      // Act
      EvaluationResult result = evaluateEntryPoints(fixture, "helper");

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString()).contains("entry");
    }

    @Test
    @DisplayName(
        "given a transactional TxCtx method reached only from its own class, should violate")
    void given_aTxCtxMethodReachedOnlyFromItsOwnClass_should_violate() {
      // Arrange
      Class<?> fixture = TenantActiveTableAccessFixtures.SelfCalledTxCtxHelper.class;

      // Act
      EvaluationResult result = evaluateEntryPoints(fixture, "helper");

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString()).contains("entry");
    }

    @Test
    @DisplayName("given a non-private method with no TxCtx that leads to the table, should violate")
    void given_anEntryPointWithoutATxCtx_should_violate() {
      // Arrange
      Class<?> fixture = TenantActiveTableAccessFixtures.UnscopedEntryPoint.class;

      // Act
      EvaluationResult result = evaluateEntryPoints(fixture, "helper");

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString()).contains("entry").contains("no TxCtx");
    }

    @Test
    @DisplayName("given a table reached by a method no non-private method leads to, should violate")
    void given_noEntryPointToTheTable_should_violate() {
      // Arrange
      Class<?> fixture = TenantActiveTableAccessFixtures.NoEntryPoint.class;

      // Act
      EvaluationResult result = evaluateEntryPoints(fixture, "helper");

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString()).contains("has no caller");
    }
  }
}
