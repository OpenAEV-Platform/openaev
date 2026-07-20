package io.openaev.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.openaev.database.audit.AuditStateCapturable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class JsonTestUtils {

  private static ObjectMapper mapper;

  private static ObjectMapper getMapper() {
    if (mapper != null) {
      return mapper;
    }
    mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.setFilterProvider(
        new SimpleFilterProvider()
            .addFilter(
                AuditStateCapturable.AUDIT_STATE_FILTER, SimpleBeanPropertyFilter.serializeAll()));
    return mapper;
  }

  public static String asJsonString(@NotNull final Object obj) {
    try {
      return getMapper().writeValueAsString(obj);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> T asStringJson(@NotBlank final String obj, @NotNull final Class<T> clazz) {
    try {
      return getMapper().readValue(obj, clazz);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> T asStringJson(
      @NotBlank final String obj, @NotNull final TypeReference<T> typeReference) {
    try {
      return getMapper().readValue(obj, typeReference);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
