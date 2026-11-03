package io.openaev.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import io.openaev.architecture.LazyAssociationCatalog;
import io.openaev.architecture.LazyAssociationCatalog.Association;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.hibernate.Hibernate;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

/**
 * The other half of the lazy-after-commit measurement: for every lazy association Jackson writes in
 * a response, whether it was already initialised at that moment.
 *
 * <p>{@link LazyAfterCommitProbeListener} sees the reads that happen after the commit. It cannot
 * see an endpoint that was exercised and stayed silent because its association had been fetched
 * inside the scope, which is exactly the evidence an attenuated site needs. This recorder supplies
 * it: an association observed initialised when written was fetched inside the transaction (a fetch
 * join, an entity graph, an eager mapping or an explicit initialisation), and one observed
 * uninitialised is either loaded right after by an id or model serializer, which the probe then
 * reports, or written as {@code null} by {@code Hibernate7Module}.
 *
 * <p>It reads the field behind the property, never the getter, so recording never initialises
 * anything itself.
 */
public final class LazySerializationRecorder {

  static final String OUTPUT_SUFFIX = ".serialized.tsv";

  /** One association written by one endpoint, initialised or not. */
  public record Observation(
      String endpoint, String handler, String association, boolean initialized) {}

  private static final Map<Observation, LongAdder> COUNTS = new ConcurrentHashMap<>();

  private LazySerializationRecorder() {}

  public static void clear() {
    COUNTS.clear();
  }

  /** How many times each observation was made since the last {@link #clear()}. */
  public static Map<Observation, Long> counts() {
    Map<Observation, Long> snapshot =
        new java.util.TreeMap<>(java.util.Comparator.comparing(Observation::toString));
    COUNTS.forEach((observation, count) -> snapshot.put(observation, count.sum()));
    return snapshot;
  }

  /** The Jackson module that wraps every lazy association of an entity with a recording writer. */
  static SimpleModule module() {
    SimpleModule module = new SimpleModule("lazy-serialization-recorder");
    module.setSerializerModifier(
        new BeanSerializerModifier() {
          @Override
          public List<BeanPropertyWriter> changeProperties(
              SerializationConfig config,
              BeanDescription description,
              List<BeanPropertyWriter> properties) {
            Class<?> type = description.getBeanClass();
            if (!LazyAssociationCatalog.isEntity(type)) {
              return properties;
            }
            List<BeanPropertyWriter> changed = new ArrayList<>(properties.size());
            for (BeanPropertyWriter writer : properties) {
              Association association = match(type, writer.getMember());
              changed.add(association == null ? writer : new RecordingWriter(writer, association));
            }
            return changed;
          }
        });
    return module;
  }

  private static Association match(Class<?> type, AnnotatedMember member) {
    if (member == null) {
      return null;
    }
    String name = member.getName();
    String fieldName =
        name.startsWith("get") && name.length() > 3
            ? Character.toLowerCase(name.charAt(3)) + name.substring(4)
            : name;
    for (Association association : LazyAssociationCatalog.associationsOf(type)) {
      if (!association.lazy()) {
        continue;
      }
      if (association.property().equals(fieldName) || association.property().equals(name + "()")) {
        return association;
      }
    }
    return null;
  }

  private static void observe(Association association, boolean initialized) {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
      return;
    }
    HttpServletRequest request = servletAttributes.getRequest();
    Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
    if (!(handler instanceof HandlerMethod method)) {
      return;
    }
    Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    Observation observation =
        new Observation(
            request.getMethod() + " " + (pattern == null ? request.getRequestURI() : pattern),
            method.getBeanType().getSimpleName() + "." + method.getMethod().getName(),
            association.key(),
            initialized);
    LongAdder previous = COUNTS.putIfAbsent(observation, new LongAdder());
    COUNTS.get(observation).increment();
    if (previous == null) {
      appendFirstSight(observation);
    }
  }

  private static synchronized void appendFirstSight(Observation observation) {
    String output = System.getProperty(LazyAfterCommitProbeListener.OUTPUT_PROPERTY);
    if (output == null || output.isBlank()) {
      return;
    }
    String line =
        String.join(
            "\t",
            observation.initialized() ? "INITIALIZED" : "LAZY",
            observation.endpoint(),
            observation.handler(),
            observation.association());
    try {
      Files.writeString(
          Path.of(output + OUTPUT_SUFFIX),
          line + System.lineSeparator(),
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Records the state of the field behind the property, then writes it unchanged. */
  private static final class RecordingWriter extends BeanPropertyWriter {

    private final transient Association association;

    RecordingWriter(BeanPropertyWriter base, Association association) {
      super(base);
      this.association = association;
    }

    @Override
    public void serializeAsField(Object bean, JsonGenerator gen, SerializerProvider prov)
        throws Exception {
      observe(association, initialized(bean));
      super.serializeAsField(bean, gen, prov);
    }

    private boolean initialized(Object bean) {
      Field field = ReflectionUtils.findField(bean.getClass(), association.sourceField());
      if (field == null) {
        return true;
      }
      ReflectionUtils.makeAccessible(field);
      Object value = ReflectionUtils.getField(field, bean);
      return value == null || Hibernate.isInitialized(value);
    }
  }
}
