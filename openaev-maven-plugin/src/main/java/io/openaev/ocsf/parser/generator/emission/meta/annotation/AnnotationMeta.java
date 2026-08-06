package io.openaev.ocsf.parser.generator.emission.meta.annotation;

import io.openaev.ocsf.parser.generator.emission.Emitter;
import java.lang.annotation.Annotation;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Set;

public class AnnotationMeta implements Emitter {
  private final Set<AttributeMeta<?>> attributes = new HashSet<>();
  private final Class<? extends Annotation> cls;

  public AnnotationMeta(Class<? extends Annotation> cls) {
    this.cls = cls;
  }

  public <T> AnnotationMeta withAttribute(String key, T obj) {
    this.attributes.add(new AttributeMeta<T>(key, obj));
    return this;
  }

  @Override
  public String emit() {
    StringBuilder render = new StringBuilder(MessageFormat.format("@{0}", cls.getName()));
    if (!attributes.isEmpty()) {
      render.append("(");
      for (AttributeMeta<?> attr : this.attributes) {
        attr.emit();
      }
      render.append(")");
    }
    return render.toString();
  }
}
