package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import io.openaev.architecture.fixture.TenantActiveTableAccessFixtures;
import io.openaev.database.model.Tenant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

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
}
