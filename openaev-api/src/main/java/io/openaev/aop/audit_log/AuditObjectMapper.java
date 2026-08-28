package io.openaev.aop.audit_log;

import static io.openaev.helper.CryptoHelper.hashWithSHA256;

import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.openaev.database.audit.AuditLogHash;
import io.openaev.database.audit.AuditLogIgnore;
import io.openaev.database.audit.AuditLogRedact;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Dedicated mapper for audit payloads that can ignore/mask annotated fields. */
@Component
public class AuditObjectMapper {

  private final ObjectMapper mapper;

  public AuditObjectMapper(ObjectMapper source) {
    ObjectMapper copy = source.copy();
    copy.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    AnnotationIntrospector base = copy.getSerializationConfig().getAnnotationIntrospector();
    copy.setAnnotationIntrospector(
        AnnotationIntrospector.pair(new AuditLogIgnoreIntrospector(), base));
    copy.setSerializerFactory(
        copy.getSerializerFactory().withSerializerModifier(new AuditMaskingSerializerModifier()));
    this.mapper = copy;
  }

  public JsonNode valueToTree(Object value) {
    return mapper.valueToTree(value);
  }

  private static class AuditLogIgnoreIntrospector extends JacksonAnnotationIntrospector {
    @Override
    public boolean hasIgnoreMarker(AnnotatedMember member) {
      return member.hasAnnotation(AuditLogIgnore.class) || super.hasIgnoreMarker(member);
    }
  }

  private static class AuditMaskingSerializerModifier extends BeanSerializerModifier {

    @Override
    public List<BeanPropertyWriter> changeProperties(
        SerializationConfig config,
        com.fasterxml.jackson.databind.BeanDescription beanDesc,
        List<BeanPropertyWriter> beanProperties) {
      for (BeanPropertyWriter writer : beanProperties) {
        AnnotatedMember member = writer.getMember();
        if (member == null) {
          continue;
        }
        if (member.hasAnnotation(AuditLogHash.class)) {
          writer.assignSerializer(AuditHashSerializer.INSTANCE);
        } else if (member.hasAnnotation(AuditLogRedact.class)) {
          writer.assignSerializer(AuditRedactSerializer.INSTANCE);
        }
      }
      return beanProperties;
    }
  }

  private static class AuditHashSerializer extends StdSerializer<Object> {

    private static final AuditHashSerializer INSTANCE = new AuditHashSerializer();
    private static final ObjectMapper HASH_INPUT_MAPPER = new ObjectMapper();

    private AuditHashSerializer() {
      super(TypeFactory.defaultInstance().constructType(Object.class));
    }

    @Override
    public void serialize(
        Object value, com.fasterxml.jackson.core.JsonGenerator gen, SerializerProvider provider)
        throws IOException {
      if (value == null) {
        provider.defaultSerializeNull(gen);
        return;
      }
      gen.writeString(hashWithSHA256(toHashInput(value)));
    }

    private static String toHashInput(Object value) throws IOException {
      if (value.getClass().isArray() || value instanceof List<?>) {
        return HASH_INPUT_MAPPER.writeValueAsString(normalizeStructuredValue(value));
      }
      return String.valueOf(value);
    }

    private static Object normalizeStructuredValue(Object value) {
      if (value == null) {
        return null;
      }
      if (value.getClass().isArray()) {
        int length = Array.getLength(value);
        List<Object> normalized = new ArrayList<>(length);
        for (int index = 0; index < length; index++) {
          normalized.add(normalizeStructuredValue(Array.get(value, index)));
        }
        return normalized;
      }
      if (value instanceof List<?> listValue) {
        List<Object> normalized = new ArrayList<>(listValue.size());
        for (Object element : listValue) {
          normalized.add(normalizeStructuredValue(element));
        }
        return normalized;
      }
      return value;
    }
  }

  private static class AuditRedactSerializer extends StdSerializer<Object> {

    private static final AuditRedactSerializer INSTANCE = new AuditRedactSerializer();
    private static final String REDACTED = "[REDACTED]";

    private AuditRedactSerializer() {
      super(TypeFactory.defaultInstance().constructType(Object.class));
    }

    @Override
    public void serialize(
        Object value, com.fasterxml.jackson.core.JsonGenerator gen, SerializerProvider provider)
        throws IOException {
      gen.writeString(REDACTED);
    }
  }
}
