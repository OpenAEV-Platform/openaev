package io.openaev.ocsf.parser.generator.utility;

import static io.openaev.ocsf.parser.schema.SchemaDimension.SINGLE_CLASS;

import io.openaev.ocsf.parser.generator.emission.ClassMetadata;
import io.openaev.ocsf.parser.generator.emission.Emitter;
import io.openaev.ocsf.parser.generator.emission.meta.Modifier;
import io.openaev.ocsf.parser.generator.emission.meta.annotation.AnnotationMeta;
import io.openaev.ocsf.parser.generator.emission.meta.enums.EnumMeta;
import io.openaev.ocsf.parser.generator.emission.meta.enums.OptionMeta;
import io.openaev.ocsf.parser.generator.emission.meta.field.FieldMeta;
import io.openaev.ocsf.parser.generator.emission.meta.method.ArgumentMeta;
import io.openaev.ocsf.parser.generator.emission.meta.method.MethodMeta;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;

public class OcsfClassUidEmitter implements Emitter {
  private final Map<String, ClassMetadata> tracker;
  private final String packageName;

  public OcsfClassUidEmitter(Map<String, ClassMetadata> tracker, String packageName) {
    this.tracker = tracker;
    this.packageName = packageName;
  }

  @Override
  public String emit() {
    // class UID enum
    EnumMeta classIdMeta =
        new EnumMeta()
            .withName("OcsfClassUid")
            .withPackage(packageName)
            .withImport(Getter.class.getCanonicalName())
            .withField(
                new FieldMeta(Modifier.PRIVATE, "final " + String.class.getCanonicalName(), "value")
                    .withAnnotation(new AnnotationMeta(Getter.class)))
            .withMethod(
                new MethodMeta(Modifier.NONE, "OcsfClassUid", "", "this.value = value;")
                    .withArgument(new ArgumentMeta(String.class, "value")))
            .withMethod(
                new MethodMeta(
                        Modifier.PUBLIC,
                        "static OcsfClassUid",
                        "fromClassUid",
                        """
                                            for(OcsfClassUid opt : OcsfClassUid.values()) {
                                              if(value.equals(opt.getValue())) {
                                                return opt;
                                              }
                                            }
                                            throw new IllegalArgumentException("No such class UID: %s".formatted(value));
                                            """)
                    .withArgument(new ArgumentMeta(String.class, "value")));
    for (ClassMetadata md : tracker.values()) {
      if (SINGLE_CLASS.equals(Objects.requireNonNull(md.dimension()))) {
        classIdMeta =
            classIdMeta.withOption(
                new OptionMeta(md.ocsfIdentifier().toUpperCase()).withValue(md.ocsfClassUid()));
      }
    }

    return classIdMeta.emit();
  }
}
