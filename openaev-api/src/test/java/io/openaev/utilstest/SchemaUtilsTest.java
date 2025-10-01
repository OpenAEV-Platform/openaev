package io.openaev.utilstest;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.annotation.Queryable;
import io.openaev.database.model.Filters;
import io.openaev.schema.PropertySchema;
import io.openaev.schema.SchemaUtils;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Schema Utils tests")
public class SchemaUtilsTest extends IntegrationTest {
  @Test
  @DisplayName(
      "When override operators are set on Queryable, PropertySchema has these operators set")
  public void WhenOverrideOperatorsAreSetOnQueryable_PropertySchemaHasTheseOperatorsSet() {
    List<Filters.FilterOperator> expectedOperators =
        List.of(Filters.FilterOperator.eq, Filters.FilterOperator.contains);

    @Getter
    class TestClass {
      @Queryable(
          filterable = true,
          overrideOperators = {Filters.FilterOperator.eq, Filters.FilterOperator.contains})
      private String stringAttribute;

      @Queryable(filterable = true)
      private boolean booleanAttribute;
    }

    List<PropertySchema> propertySchemas = SchemaUtils.schema(TestClass.class);

    PropertySchema stringAttribute =
        propertySchemas.stream()
            .filter(ps -> ps.getName().equals("stringAttribute"))
            .findFirst()
            .get();
    assertThat(stringAttribute.getOverrideOperators()).isEqualTo(expectedOperators);

    PropertySchema booleanAttribute =
        propertySchemas.stream()
            .filter(ps -> ps.getName().equals("booleanAttribute"))
            .findFirst()
            .get();
    assertThat(booleanAttribute.getOverrideOperators()).isEqualTo(List.of());
  }

  @Test
  @DisplayName(
      "When clazz is NOT set on queryable method returning array-like, PropertySchema has correct type")
  public void wheClazzIsNotSetOnQueryableMethodReturningArrayLike_PropertySchemaHasCorrectType() {
    Class<?> expectedType = Set.class;

    @Getter
    class TestClass {
      @Queryable(filterable = true)
      private Set<String> getStrings() {
        return Set.of();
      }
    }

    List<PropertySchema> propertySchemas = SchemaUtils.schema(TestClass.class);

    PropertySchema stringAttribute =
        propertySchemas.stream().filter(ps -> ps.getName().equals("getStrings")).findFirst().get();
    assertThat(stringAttribute.getType()).isEqualTo(expectedType);
  }

  @Test
  @DisplayName(
      "When clazz is set on queryable method returning array-like, PropertySchema has correct type")
  public void wheClazzIsSetOnQueryableMethodReturningArrayLike_PropertySchemaHasCorrectType() {
    Class<?> expectedType = String[].class;

    @Getter
    class TestClass {
      @Queryable(filterable = true, clazz = String[].class)
      private Set<String> getStrings() {
        return Set.of();
      }
    }

    List<PropertySchema> propertySchemas = SchemaUtils.schema(TestClass.class);

    PropertySchema stringAttribute =
        propertySchemas.stream().filter(ps -> ps.getName().equals("getStrings")).findFirst().get();
    assertThat(stringAttribute.getType()).isEqualTo(expectedType);
  }

  @Test
  @DisplayName(
      "When refEnumClazz is NOT set on queryable method returning array-like, PropertySchema has correct type")
  public void
      wheRefEnumClazzIsNotSetOnQueryableMethodReturningArrayLike_PropertySchemaHasCorrectType() {
    @Getter
    class TestClass {
      @Queryable(filterable = true)
      private Set<String> getOptions() {
        return Set.of();
      }
    }

    List<PropertySchema> propertySchemas = SchemaUtils.schema(TestClass.class);

    PropertySchema stringAttribute =
        propertySchemas.stream().filter(ps -> ps.getName().equals("getOptions")).findFirst().get();
    assertThat(stringAttribute.getAvailableValues()).isNull();
  }

  @Test
  @DisplayName(
      "When refEnumClazz is set on queryable method returning array-like, PropertySchema has correct type")
  public void
      wheRefEnumClazzIsSetOnQueryableMethodReturningArrayLike_PropertySchemaHasCorrectType() {
    List<String> expectedAvailableValues = List.of("A", "B", "C");

    enum TestEnum {
      A,
      B,
      C
    }
    @Getter
    class TestClass {
      @Queryable(filterable = true, refEnumClazz = TestEnum.class)
      private Set<String> getOptions() {
        return Set.of();
      }
    }

    List<PropertySchema> propertySchemas = SchemaUtils.schema(TestClass.class);

    PropertySchema stringAttribute =
        propertySchemas.stream().filter(ps -> ps.getName().equals("getOptions")).findFirst().get();
    assertThat(stringAttribute.getAvailableValues()).isEqualTo(expectedAvailableValues);
  }
}
