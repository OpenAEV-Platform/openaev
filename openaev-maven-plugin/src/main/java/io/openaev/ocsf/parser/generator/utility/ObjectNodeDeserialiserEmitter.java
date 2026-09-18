package io.openaev.ocsf.parser.generator.utility;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.ocsf.parser.generator.emission.ClassMetadata;
import io.openaev.ocsf.parser.generator.emission.Emitter;
import io.openaev.ocsf.parser.generator.emission.meta.Modifier;
import io.openaev.ocsf.parser.generator.emission.meta.annotation.AnnotationMeta;
import io.openaev.ocsf.parser.generator.emission.meta.cls.ClassMeta;
import io.openaev.ocsf.parser.generator.emission.meta.cls.ExtendMeta;
import io.openaev.ocsf.parser.generator.emission.meta.method.ArgumentMeta;
import io.openaev.ocsf.parser.generator.emission.meta.method.MethodMeta;
import java.io.IOException;

public class ObjectNodeDeserialiserEmitter implements Emitter {
  private final String helperClassPackage;
  private final ClassMetadata md;

  public ObjectNodeDeserialiserEmitter(String helperClassPackage, ClassMetadata md) {
    this.helperClassPackage = helperClassPackage;
    this.md = md;
  }

  @Override
  public String emit() {
    ClassMeta classMeta =
        new ClassMeta()
            .withPackage(helperClassPackage)
            .withName("ObjectNodeDeserialiser")
            .withExtend(
                new ExtendMeta(JsonDeserializer.class.getCanonicalName())
                    .withGenericTypeArgument(md.fullyQualifiedClassName()))
            .withMethod(
                new MethodMeta(
                        Modifier.PUBLIC,
                        md.fullyQualifiedClassName(),
                        "deserialize",
                        """
                        return new %s(p.readValueAs(%s.class));
                        """
                            .formatted(
                                md.fullyQualifiedClassName(), ObjectNode.class.getCanonicalName()))
                    .withAnnotation(new AnnotationMeta(Override.class))
                    .withArgument(new ArgumentMeta(JsonParser.class, "p"))
                    .withArgument(new ArgumentMeta(DeserializationContext.class, "ctxt"))
                    .withThrow(IOException.class));
    return classMeta.emit();
  }
}
