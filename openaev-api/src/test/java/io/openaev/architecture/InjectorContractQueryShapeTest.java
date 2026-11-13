package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorInjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.repository.InjectorContractRepository;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

/**
 * The access allowlist of the injector contracts in {@code TenantActiveTableAccessArchTest} gives
 * reasons that rest on the shape of an entity or of a query. Each shape is read here from the
 * mapping itself, so changing it fails this class instead of leaving a reason that was true when it
 * was written.
 */
@DisplayName("Entity and query shapes the injector contract access allowlist relies on")
class InjectorContractQueryShapeTest {

  private static List<Field> idFields(Class<?> entity) {
    return Arrays.stream(entity.getDeclaredFields())
        .filter(f -> f.isAnnotationPresent(Id.class) || f.isAnnotationPresent(EmbeddedId.class))
        .toList();
  }

  private static Field field(Class<?> type, String name) throws NoSuchFieldException {
    return type.getDeclaredField(name);
  }

  @Nested
  @DisplayName("Payload keyed lookups")
  class PayloadKey {

    @Test
    @DisplayName("given_payloadEntity_should_haveOneSingleColumnPrimaryKey")
    void given_payloadEntity_should_haveOneSingleColumnPrimaryKey() throws Exception {
      // Act
      List<Field> ids = idFields(Payload.class);

      // Assert: a payload entity names one row, whichever tenants are in the scope
      assertThat(ids).extracting(Field::getName).containsExactly("id");
      assertThat(ids.get(0).isAnnotationPresent(EmbeddedId.class)).isFalse();
      assertThat(ids.get(0).getAnnotation(Column.class).name()).isEqualTo("payload_id");
      assertThat(Payload.class.isAnnotationPresent(IdClass.class)).isFalse();
    }

    @Test
    @DisplayName("given_contractByPayloadLookup_should_beDerivedFromThePayloadEntity")
    void given_contractByPayloadLookup_should_beDerivedFromThePayloadEntity() throws Exception {
      // Act
      var method =
          InjectorContractRepository.class.getMethod(
              "findInjectorContractByPayload", Payload.class);

      // Assert: a derived query on the payload association, with no query text of its own
      assertThat(method.isAnnotationPresent(Query.class)).isFalse();
    }
  }

  @Nested
  @DisplayName("Injector keyed lookups")
  class InjectorKey {

    @Test
    @DisplayName("given_injectorEntity_should_beKeyedByIdAndTenant")
    void given_injectorEntity_should_beKeyedByIdAndTenant() {
      // Act
      List<Field> ids = idFields(Injector.class);

      // Assert: an injector entity names the row of one tenant
      assertThat(ids).extracting(Field::getName).containsExactlyInAnyOrder("id", "tenantId");
      assertThat(Injector.class.isAnnotationPresent(IdClass.class)).isTrue();
    }

    @Test
    @DisplayName("given_injectorLink_should_joinTheInjectorOnIdAndTenant")
    void given_injectorLink_should_joinTheInjectorOnIdAndTenant() throws Exception {
      // Act
      JoinColumn[] columns =
          field(InjectorInjectorContract.class, "injector")
              .getAnnotation(JoinColumns.class)
              .value();

      // Assert
      assertThat(columns)
          .extracting(JoinColumn::name)
          .containsExactlyInAnyOrder("injector_id", "tenant_id");
    }

    @Test
    @DisplayName("given_contractsOfAnInjectorLookup_should_compareTheLinkToTheInjectorEntity")
    void given_contractsOfAnInjectorLookup_should_compareTheLinkToTheInjectorEntity()
        throws Exception {
      // Act
      var method =
          InjectorContractRepository.class.getMethod("findByInjectorsContaining", Injector.class);
      String jpql = method.getAnnotation(Query.class).value();

      // Assert
      assertThat(jpql).contains("JOIN ic.injectorLinks l").contains("l.injector = :injector");
    }
  }
}
