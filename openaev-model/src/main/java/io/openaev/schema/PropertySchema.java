package io.openaev.schema;

import static lombok.AccessLevel.NONE;
import static org.springframework.util.StringUtils.hasText;

import io.openaev.database.model.Filters;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/**
 * Schema representation of an entity property for dynamic query building and API documentation.
 *
 * <p>This class captures metadata about entity properties, including:
 *
 * <ul>
 *   <li>Basic property info (name, type, JSON name)
 *   <li>Validation constraints (mandatory, unique)
 *   <li>Query capabilities (searchable, filterable, sortable)
 *   <li>Relationship information (join tables, paths)
 *   <li>Available values for enum/select fields
 * </ul>
 *
 * <p>Property schemas are built from entity classes using {@link SchemaUtils} and are used by the
 * filter/search system to validate and construct dynamic queries.
 *
 * @see SchemaUtils
 * @see io.openaev.annotation.Queryable
 */
@Builder
@Getter
public class PropertySchema {

  /** The Java field name. */
  @NotBlank private final String name;

  /** The JSON property name (from @JsonProperty annotation). */
  @Getter(NONE)
  private final String jsonName;

  /** The Java type of the property. */
  @NotNull private final Class<?> type;

  /** The generic subtype for collections (e.g., List&lt;String&gt;). */
  private final Type subtype;

  /** Whether this field has a unique constraint. */
  private final boolean unicity;

  /** Whether this field is mandatory (not null). */
  private final boolean mandatory;

  /** Whether this field is a collection or array. */
  private final boolean multiple;

  /** Whether this field is full-text searchable. */
  private final boolean searchable;

  /** Whether this field can be used in filters. */
  private final boolean filterable;

  /** Available values for enum or select fields. */
  private final List<String> availableValues;

  /** Whether values should be dynamically loaded from the database. */
  private final boolean dynamicValues;

  /** Whether this field can be used for sorting. */
  private final boolean sortable;

  /** Custom filter operators for this property. */
  private final List<Filters.FilterOperator> overrideOperators;

  /** Join table configuration for many-to-many relationships. */
  private final JoinTable joinTable;

  /** JPA path to this property (for nested properties). */
  private final String path;

  /** Human-readable label for UI display. */
  private final String label;

  /** Entity/index name this property belongs to. */
  private final String entity;

  /** Multiple possible JPA paths for this property. */
  private final String[] paths;

  /** Whether this is an Elasticsearch keyword field. */
  private final boolean keyword;

  /** Nested property schemas for complex types. */
  @Singular("propertySchema")
  private final List<PropertySchema> propertiesSchema;

  /**
   * Returns the JSON property name, falling back to the Java field name if not specified.
   *
   * @return the JSON property name
   */
  public String getJsonName() {
    return Optional.ofNullable(this.jsonName).orElse(this.name);
  }

  /** Represents join table configuration for many-to-many relationships. */
  @Builder
  @Getter
  public static class JoinTable {
    /** The field name to join on. */
    private final String joinOn;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    PropertySchema that = (PropertySchema) o;
    return Objects.equals(name, that.name) && Objects.equals(type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, type);
  }

  // -- VALIDATION --

  public static PropertySchemaBuilder builder() {
    return new ValidationBuilder();
  }

  private static class ValidationBuilder extends PropertySchemaBuilder {

    public PropertySchema build() {
      if (!hasText(super.name)) {
        throw new RuntimeException("Property name should not be empty");
      }
      if (super.type == null) {
        throw new RuntimeException("Property type should not be empty");
      }

      return super.build();
    }
  }
}
