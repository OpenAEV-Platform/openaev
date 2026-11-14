package io.openaev.service.hash;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Document;
import io.openaev.database.model.hash.FileReference;
import io.openaev.database.model.hash.Hashed;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.exception.ElementNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.erdtman.jcs.JsonCanonicalizer;
import org.hibernate.Hibernate;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Component;

/**
 * The canonical form of content marked with {@link Hashed} (RFC 0005, Appendix B), and its hash:
 * SHA-256 of the RFC 8785 text, so the same content gives the same hash on any platform.
 */
@Component
@RequiredArgsConstructor
public class CanonicalContent {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Map<Class<?>, List<HashedMember>> MEMBERS = new ConcurrentHashMap<>();

  private final DocumentService documentService;

  /**
   * The canonical form of an object, empty values left out.
   *
   * @throws UnreadableFileException when an attached file cannot be read
   */
  public ObjectNode tree(Object hashed) {
    return (ObjectNode) withoutEmpty(object(hashed));
  }

  /** The keys a class enters the canonical form under. */
  public Set<String> keys(Class<?> type) {
    return membersOf(type).stream().map(HashedMember::key).collect(Collectors.toSet());
  }

  /** The SHA-256 of the RFC 8785 text of a canonical form. */
  public String hash(JsonNode tree) {
    return sha256(canonical(withoutEmpty(tree)));
  }

  private String fileHash(Document document) {
    return documentService.contentHash(document).orElseThrow(UnreadableFileException::new);
  }

  private ObjectNode object(Object source) {
    Object value = Hibernate.unproxy(source);
    ObjectNode node = MAPPER.createObjectNode();
    for (HashedMember member : membersOf(value.getClass())) {
      Object memberValue = member.read(value);
      if (memberValue != null) {
        node.set(member.key(), entry(member.hashed(), memberValue));
      }
    }
    return node;
  }

  private JsonNode entry(Hashed hashed, Object value) {
    if (value instanceof Document document) {
      return MAPPER.valueToTree(fileHash(document));
    }
    if (value instanceof FileReference file) {
      return MAPPER.valueToTree(fileHash(documentOf(file.documentId())));
    }
    return switch (hashed.order()) {
      case VALUE -> MAPPER.valueToTree(value);
      case SET ->
          value instanceof Map<?, ?> map ? sortedValuesByKey(map) : sortedValues(items(value));
      case LIST -> objects(items(value));
      case OBJECTS -> sortedObjects(items(value));
    };
  }

  private Document documentOf(String documentId) {
    try {
      return documentService.document(documentId);
    } catch (ElementNotFoundException e) {
      throw new UnreadableFileException();
    }
  }

  private ArrayNode objects(Collection<?> items) {
    ArrayNode array = MAPPER.createArrayNode();
    items.forEach(item -> array.add(object(item)));
    return array;
  }

  // Sets of objects: sorted by their canonical form, so storage order never changes the hash.
  private ArrayNode sortedObjects(Collection<?> items) {
    ArrayNode array = MAPPER.createArrayNode();
    items.stream()
        .map(item -> withoutEmpty(object(item)))
        .sorted(Comparator.comparing(CanonicalContent::canonical))
        .forEach(array::add);
    return array;
  }

  private ObjectNode sortedValuesByKey(Map<?, ?> map) {
    ObjectNode node = MAPPER.createObjectNode();
    map.forEach(
        (key, values) -> node.set(MAPPER.valueToTree(key).asText(), sortedValues(items(values))));
    return node;
  }

  /** A set of values: order does not matter, so they are sorted by their text. */
  public ArrayNode sortedValues(Collection<?> values) {
    ArrayNode array = MAPPER.createArrayNode();
    values.stream()
        .filter(Objects::nonNull)
        .map(value -> MAPPER.valueToTree(value).asText())
        .sorted()
        .forEach(array::add);
    return array;
  }

  private static Collection<?> items(Object value) {
    if (value == null) {
      return List.of();
    }
    return value instanceof Object[] array ? Arrays.asList(array) : (Collection<?>) value;
  }

  private static List<HashedMember> membersOf(Class<?> type) {
    return MEMBERS.computeIfAbsent(type, CanonicalContent::findMembers);
  }

  // Fields and no-argument methods marked @Hashed, from the class and its parents.
  private static List<HashedMember> findMembers(Class<?> type) {
    Map<String, HashedMember> members = new HashMap<>();
    for (Class<?> current = type; current != Object.class; current = current.getSuperclass()) {
      for (Field field : current.getDeclaredFields()) {
        hashedMember(field).ifPresent(member -> addOnce(members, member));
      }
      for (Method method : current.getDeclaredMethods()) {
        hashedMember(method).ifPresent(member -> addOnce(members, member));
      }
    }
    return List.copyOf(members.values());
  }

  private static void addOnce(Map<String, HashedMember> members, HashedMember member) {
    HashedMember other = members.putIfAbsent(member.key(), member);
    if (other != null) {
      throw new IllegalStateException(
          "Two hashed members share the key "
              + member.key()
              + ": "
              + member.member()
              + ", "
              + other.member());
    }
  }

  private static <M extends AccessibleObject & Member> Optional<HashedMember> hashedMember(
      M member) {
    Hashed hashed = member.getAnnotation(Hashed.class);
    if (hashed == null || Modifier.isStatic(member.getModifiers())) {
      return Optional.empty();
    }
    if (member instanceof Method method && method.getParameterCount() > 0) {
      throw new IllegalStateException("A hashed method takes no argument: " + member);
    }
    member.setAccessible(true);
    return Optional.of(new HashedMember(keyOf(member, hashed), hashed, member));
  }

  private static String keyOf(AccessibleObject member, Hashed hashed) {
    if (!hashed.key().isEmpty()) {
      return hashed.key();
    }
    JsonProperty property = member.getAnnotation(JsonProperty.class);
    if (property == null) {
      throw new IllegalStateException("A hashed member needs a key or a JSON name: " + member);
    }
    return property.value();
  }

  private record HashedMember(String key, Hashed hashed, AccessibleObject member) {

    Object read(Object owner) {
      Object value;
      try {
        value = member instanceof Field field ? field.get(owner) : ((Method) member).invoke(owner);
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new IllegalStateException("Cannot read the hashed member " + member, e);
      }
      if (value == null || hashed.property().isEmpty()) {
        return value;
      }
      return new BeanWrapperImpl(Hibernate.unproxy(value)).getPropertyValue(hashed.property());
    }
  }

  private static String sha256(String canonical) {
    return DigestUtils.sha256Hex(canonical.getBytes(StandardCharsets.UTF_8));
  }

  private static String canonical(JsonNode node) {
    try {
      return new JsonCanonicalizer(node.toString()).getEncodedString();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // Appendix B: null and empty fields are left out.
  private static JsonNode withoutEmpty(JsonNode node) {
    if (node.isObject()) {
      ObjectNode cleaned = MAPPER.createObjectNode();
      node.properties()
          .forEach(
              entry -> {
                JsonNode value = withoutEmpty(entry.getValue());
                if (!isEmpty(value)) {
                  cleaned.set(entry.getKey(), value);
                }
              });
      return cleaned;
    }
    if (node.isArray()) {
      ArrayNode cleaned = MAPPER.createArrayNode();
      node.forEach(item -> cleaned.add(withoutEmpty(item)));
      return cleaned;
    }
    return node;
  }

  private static boolean isEmpty(JsonNode node) {
    return node == null
        || node.isNull()
        || (node.isTextual() && node.asText().isEmpty())
        || (node.isContainerNode() && node.isEmpty());
  }
}
