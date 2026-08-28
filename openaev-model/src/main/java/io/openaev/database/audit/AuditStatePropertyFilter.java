package io.openaev.database.audit;

import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;

/**
 * Jackson property filter that excludes fields annotated with {@link AuditStateIgnore}. Fields
 * without the annotation pass through normally.
 *
 * <p>Used by {@link AuditStateCapturable} to avoid serializing non-significant fields during audit
 * state capture.
 */
public class AuditStatePropertyFilter extends SimpleBeanPropertyFilter {

  @Override
  protected boolean include(BeanPropertyWriter writer) {
    return isAuditStateValid(writer);
  }

  @Override
  protected boolean include(PropertyWriter writer) {
    if (writer instanceof BeanPropertyWriter bpw) {
      return include(bpw);
    }
    return true;
  }

  private static boolean isAuditStateValid(BeanPropertyWriter writer) {
    return writer.getAnnotation(AuditStateIgnore.class) == null;
  }
}
