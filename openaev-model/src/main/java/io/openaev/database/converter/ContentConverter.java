package io.openaev.database.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Resource;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.io.IOException;

/**
 * JPA attribute converter for Jackson {@link ObjectNode} to JSON string conversion.
 *
 * <p>This converter enables JPA entities to store arbitrary JSON content in database columns by
 * converting between Jackson's {@link ObjectNode} representation and JSON strings.
 *
 * <p>Example usage in an entity:
 *
 * <pre>{@code
 * @Convert(converter = ContentConverter.class)
 * @Column(name = "inject_content", columnDefinition = "text")
 * private ObjectNode content;
 * }</pre>
 *
 * <p>Note: Conversion errors are silently handled by returning {@code null}. Consider enabling
 * logging for debugging purposes in production environments.
 */
@Converter
public class ContentConverter implements AttributeConverter<ObjectNode, String> {

  @Resource private ObjectMapper mapper;

  /**
   * Converts an {@link ObjectNode} to its JSON string representation for database storage.
   *
   * @param meta the ObjectNode to convert (may be null)
   * @return the JSON string representation, or {@code null} if the input is null or conversion
   *     fails
   */
  @Override
  public String convertToDatabaseColumn(ObjectNode meta) {
    try {
      if (meta == null || meta.isNull()) {
        return null;
      }
      return mapper.writeValueAsString(meta);
    } catch (JsonProcessingException ex) {
      return null;
    }
  }

  /**
   * Converts a JSON string from the database to an {@link ObjectNode}.
   *
   * @param dbData the JSON string from the database (may be null)
   * @return the parsed ObjectNode, or {@code null} if the input is null or parsing fails
   */
  @Override
  public ObjectNode convertToEntityAttribute(String dbData) {
    try {
      if (dbData == null) {
        return null;
      }
      return mapper.readValue(dbData, ObjectNode.class);
    } catch (IOException ex) {
      return null;
    }
  }
}
