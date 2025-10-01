package io.openaev.database.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Resource;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.io.IOException;

@Converter
public class ContentConverter implements AttributeConverter<ObjectNode, String> {

  @Resource private ObjectMapper mapper;

  @Override
  public String convertToDatabaseColumn(ObjectNode meta) {
    try {
      if (meta == null || meta.isNull()) {
        return null;
      }
      return mapper.writeValueAsString(meta);
    } catch (JsonProcessingException ex) {
      return null;
      // or throw an error
    }
  }

  @Override
  public ObjectNode convertToEntityAttribute(String dbData) {
    try {
      if (dbData == null) {
        return null;
      }
      return mapper.readValue(dbData, ObjectNode.class);
    } catch (IOException ex) {
      // logger.error("Unexpected IOEx decoding json from database: " + dbData);
      return null;
    }
  }
}
