package io.openaev.engine.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.Tenant;
import io.openaev.engine.model.EsBase;
import io.openaev.engine.model.finding.EsFinding;
import io.openaev.engine.model.injectexpectation.EsInjectExpectation;
import io.openaev.engine.model.snapshotobservation.EsAttackObservation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link EngineReadScope#stripHiddenAssets}: a returned document keeps its visible asset references
 * and loses the hidden ones. Security platforms are assets, so a hidden platform goes from the
 * expectations and observations it reported on.
 */
@DisplayName("Engine read scope: hidden asset references removed from returned documents")
class EngineReadScopeTest {

  private static final String VISIBLE = "visible-asset";
  private static final String HIDDEN = "hidden-asset";

  private static final EngineReadScope HIDING =
      EngineReadScope.forTenant(Tenant.DEFAULT_TENANT_UUID, Set.of(), Map.of(), Set.of(HIDDEN));

  @Nested
  @DisplayName("Security platforms")
  class SecurityPlatforms {

    @Test
    @DisplayName("given a hidden platform on an expectation, should keep only the visible one")
    void given_hiddenPlatformOnExpectation_should_keepOnlyTheVisibleOne() {
      // -- ARRANGE --
      EsInjectExpectation expectation = new EsInjectExpectation();
      expectation.setBase_security_platforms_side(Set.of(VISIBLE, HIDDEN));

      // -- ACT --
      HIDING.stripHiddenAssets(expectation);

      // -- ASSERT --
      assertThat(expectation.getBase_security_platforms_side()).containsExactly(VISIBLE);
    }

    @Test
    @DisplayName(
        "given a hidden platform on an observation, should drop it from reporting and succeeded")
    void given_hiddenPlatformOnObservation_should_dropItFromBothFields() {
      // -- ARRANGE --
      EsAttackObservation observation = new EsAttackObservation();
      observation.setBase_security_platforms_side(Set.of(VISIBLE, HIDDEN));
      observation.setAttack_observation_platforms_succeeded(Set.of(VISIBLE, HIDDEN));

      // -- ACT --
      HIDING.stripHiddenAssets(observation);

      // -- ASSERT --
      assertThat(observation.getBase_security_platforms_side()).containsExactly(VISIBLE);
      assertThat(observation.getAttack_observation_platforms_succeeded()).containsExactly(VISIBLE);
    }

    @Test
    @DisplayName("given an unrestricted reader, should keep every platform")
    void given_unrestrictedReader_should_keepEveryPlatform() {
      // -- ARRANGE --
      EsAttackObservation observation = new EsAttackObservation();
      observation.setBase_security_platforms_side(Set.of(VISIBLE, HIDDEN));
      observation.setAttack_observation_platforms_succeeded(Set.of(HIDDEN));

      // -- ACT --
      EngineReadScope.unrestricted(null).stripHiddenAssets(observation);

      // -- ASSERT --
      assertThat(observation.getBase_security_platforms_side())
          .containsExactlyInAnyOrder(VISIBLE, HIDDEN);
      assertThat(observation.getAttack_observation_platforms_succeeded()).containsExactly(HIDDEN);
    }
  }

  @Nested
  @DisplayName("Findings")
  class Findings {

    @Test
    @DisplayName("given a finding on a visible and a hidden endpoint, should keep the visible one")
    void given_findingOnVisibleAndHidden_should_keepTheVisibleEndpoint() {
      // -- ARRANGE --
      EsFinding finding = new EsFinding();
      finding.setBase_endpoint_side(Set.of(VISIBLE, HIDDEN));

      // -- ACT --
      HIDING.stripHiddenAssets(finding);

      // -- ASSERT --
      assertThat(finding.getBase_endpoint_side()).containsExactly(VISIBLE);
    }

    @Test
    @DisplayName("given a finding depending on a hidden endpoint, should drop it from dependencies")
    void given_findingDependingOnHiddenEndpoint_should_dropItFromDependencies() {
      // -- ARRANGE --
      EsFinding finding = new EsFinding();
      finding.setBase_endpoint_side(Set.of(VISIBLE, HIDDEN));
      finding.setBase_dependencies(new ArrayList<>(List.of("inject-id", VISIBLE, HIDDEN)));

      // -- ACT --
      HIDING.stripHiddenAssets(finding);

      // -- ASSERT --
      assertThat(finding.getBase_dependencies()).containsExactly("inject-id", VISIBLE);
    }
  }

  /**
   * A guard against an asset reference field the strip forgets: every field of {@link
   * EngineSecurityRegistry#ASSET_REFERENCE_FIELDS} exists, a multi-valued one is stripped on every
   * model holding it, and a single-valued one excludes its document (an asset field of the type).
   */
  @Nested
  @DisplayName("Every asset reference field")
  class EveryAssetReferenceField {

    @Test
    @DisplayName("given the asset reference fields, should each exist on an indexed model")
    void given_assetReferenceFields_should_existOnAnIndexedModel() throws Exception {
      // -- ARRANGE --
      Set<String> fields = new HashSet<>();
      for (Class<?> type : EngineSecurityRegistryTest.indexedTypes().values()) {
        fields.addAll(EngineSecurityRegistryTest.fieldNames(type));
      }

      // -- ACT & ASSERT --
      assertThat(fields).containsAll(EngineSecurityRegistry.ASSET_REFERENCE_FIELDS);
    }

    @Test
    @DisplayName("given a model holding several asset references, should strip the hidden one")
    void given_multiValuedReference_should_beStrippedOnEveryModel() throws Exception {
      for (Map.Entry<String, Class<?>> indexed :
          EngineSecurityRegistryTest.indexedTypes().entrySet()) {
        for (Field field : referenceFields(indexed.getValue())) {
          if (!Collection.class.isAssignableFrom(field.getType())) {
            continue;
          }
          // -- ARRANGE --
          EsBase document = (EsBase) indexed.getValue().getDeclaredConstructor().newInstance();
          field.set(
              document,
              Set.class.isAssignableFrom(field.getType())
                  ? new HashSet<>(Set.of(VISIBLE, HIDDEN))
                  : new ArrayList<>(List.of(VISIBLE, HIDDEN)));

          // -- ACT --
          HIDING.stripHiddenAssets(document);

          // -- ASSERT --
          @SuppressWarnings("unchecked")
          Collection<Object> references = (Collection<Object>) field.get(document);
          assertThat(references)
              .as(indexed.getKey() + "." + field.getName())
              .containsExactly(VISIBLE);
        }
      }
    }

    @Test
    @DisplayName("given a model holding one asset reference, should exclude its document instead")
    void given_singleValuedReference_should_beAnAssetFieldOfItsType() throws Exception {
      for (Map.Entry<String, Class<?>> indexed :
          EngineSecurityRegistryTest.indexedTypes().entrySet()) {
        for (Field field : referenceFields(indexed.getValue())) {
          if (Collection.class.isAssignableFrom(field.getType())) {
            continue;
          }
          // -- ACT & ASSERT --
          assertThat(EngineSecurityRegistry.get(indexed.getKey()).assetFields())
              .as(indexed.getKey() + "." + field.getName())
              .contains(field.getName());
        }
      }
    }

    private static List<Field> referenceFields(Class<?> type) {
      List<Field> fields = new ArrayList<>();
      for (Class<?> current = type; current != null; current = current.getSuperclass()) {
        for (Field field : current.getDeclaredFields()) {
          if (EngineSecurityRegistry.ASSET_REFERENCE_FIELDS.contains(field.getName())) {
            field.setAccessible(true);
            fields.add(field);
          }
        }
      }
      return fields;
    }
  }
}
