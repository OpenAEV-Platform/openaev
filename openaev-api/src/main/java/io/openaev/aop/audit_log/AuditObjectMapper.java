package io.openaev.aop.audit_log;

import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import io.openaev.database.audit.AuditLogIgnore;
import org.springframework.stereotype.Component;

/** Dedicated mapper for audit payloads that can ignore {@link AuditLogIgnore} fields. */
@Component
public class AuditObjectMapper {

  private final ObjectMapper mapper;

  public AuditObjectMapper(ObjectMapper source) {
    ObjectMapper copy = source.copy();
    AnnotationIntrospector base = copy.getSerializationConfig().getAnnotationIntrospector();
    copy.setAnnotationIntrospector(
        AnnotationIntrospector.pair(new AuditLogIgnoreIntrospector(), base));
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
}
