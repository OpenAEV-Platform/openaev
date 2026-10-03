package io.openaev.database.converter;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.hypersistence.utils.hibernate.type.util.JsonSerializer;
import io.hypersistence.utils.hibernate.type.util.ObjectMapperWrapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Deep-copies {@code @Type(JsonType.class)} attributes through Jackson instead of Java
 * serialization.
 *
 * <p>Hypersistence 3.14+ (the first releases compatible with Hibernate 7) clone JSON attributes
 * with {@code SerializationHelper}, which requires every mapped payload — and every object it
 * transitively references — to implement {@link java.io.Serializable}. Our JSON payloads are plain
 * model objects that are not serializable, so Hibernate's snapshot of the loaded state blew up at
 * startup. Since these attributes are round-tripped through Jackson to reach the database in the
 * first place, cloning them the same way is both sufficient and faithful.
 *
 * <p>Registered through {@code hypersistence-utils.properties} ({@code
 * hypersistence.utils.json.serializer}), which hypersistence reads from the classpath and
 * instantiates reflectively, so the class must keep a public no-arg constructor.
 */
public class JacksonJsonCloner implements JsonSerializer {

  @Override
  @SuppressWarnings("unchecked")
  public <T> T clone(T value) {
    if (value == null || value instanceof String || value instanceof Number) {
      return value;
    }
    if (value instanceof JsonNode node) {
      return (T) node.deepCopy();
    }
    ObjectMapper mapper = ObjectMapperWrapper.INSTANCE.getObjectMapper();
    JavaType targetType = describe(mapper.getTypeFactory(), value);
    try {
      return mapper.readValue(mapper.writeValueAsBytes(value), targetType);
    } catch (IOException e) {
      throw new IllegalArgumentException(
          "cannot clone the JSON attribute of type " + value.getClass().getName(), e);
    }
  }

  /**
   * Describes {@code value} precisely enough for Jackson to rebuild it. Element and entry types are
   * inferred from the actual content because the erased runtime type alone would deserialize a
   * {@code List<PayloadArgument>} back into a list of maps, which then never compares equal to the
   * managed value and makes Hibernate report the attribute dirty on every flush.
   */
  private static JavaType describe(TypeFactory typeFactory, Object value) {
    if (value instanceof Collection<?> collection) {
      Class<?> containerType = value instanceof SortedSet ? TreeSet.class : collectionType(value);
      Class<?> elementType = commonType(collection);
      return elementType == null
          ? typeFactory.constructCollectionType(
              (Class<? extends Collection>) containerType, Object.class)
          : typeFactory.constructCollectionType(
              (Class<? extends Collection>) containerType, elementType);
    }
    if (value instanceof Map<?, ?> map) {
      Class<?> containerType = value instanceof SortedMap ? TreeMap.class : LinkedHashMap.class;
      Class<?> keyType = commonType(map.keySet());
      Class<?> valueType = commonType(map.values());
      return typeFactory.constructMapType(
          (Class<? extends Map>) containerType,
          keyType == null ? Object.class : keyType,
          valueType == null ? Object.class : valueType);
    }
    return typeFactory.constructType(value.getClass());
  }

  /**
   * The concrete container Jackson should instantiate. The original class is deliberately not
   * reused: it may be an immutable or otherwise non-instantiable implementation, and the standard
   * collections compare equal to any other implementation holding the same elements.
   */
  private static Class<?> collectionType(Object value) {
    return value instanceof Set ? LinkedHashSet.class : ArrayList.class;
  }

  /** The single class shared by every non-null element, or {@code null} when they differ. */
  private static Class<?> commonType(Collection<?> values) {
    Class<?> common = null;
    for (Object element : values) {
      if (element == null) {
        continue;
      }
      if (common == null) {
        common = element.getClass();
      } else if (common != element.getClass()) {
        return null;
      }
    }
    return common;
  }
}
