package io.openaev.ocsf.parser.generator.utility;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.openaev.ocsf.parser.generator.emission.ClassMetadata;
import io.openaev.ocsf.parser.generator.emission.Emitter;
import io.openaev.ocsf.parser.generator.emission.meta.Modifier;
import io.openaev.ocsf.parser.generator.emission.meta.cls.ClassMeta;
import io.openaev.ocsf.parser.generator.emission.meta.field.FieldMeta;
import io.openaev.ocsf.parser.generator.emission.meta.method.ArgumentMeta;
import io.openaev.ocsf.parser.generator.emission.meta.method.MethodMeta;
import io.openaev.ocsf.parser.schema.SchemaDimension;
import io.openaev.utils.StringUtils;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class OcsfFilterEmitter implements Emitter {
  private final Map<String, ClassMetadata> tracker;
  private final String packageName;
  private final StringUtils stringUtils = new StringUtils();

  public OcsfFilterEmitter(Map<String, ClassMetadata> tracker, String packageName) {
    this.tracker = tracker;
    this.packageName = packageName;
  }

  @Override
  public String emit() {
    // converter
    ClassMeta classMeta =
        new ClassMeta()
            .withName("OcsfFilter")
            .withPackage(packageName)
            .withField(
                new FieldMeta(Modifier.PRIVATE, "OcsfConverter", "converter")
                    .withInitialiser("new OcsfConverter()"));
    for (ClassMetadata md : tracker.values()) {
      if (SchemaDimension.SINGLE_CLASS.equals(md.dimension())) {
        classMeta =
            classMeta.withMethod(
                new MethodMeta(
                        Modifier.PUBLIC,
                        List.class.getCanonicalName() + "<" + md.fullyQualifiedClassName() + ">",
                        "filter" + stringUtils.pluralise(md.className()),
                        """
                                            %s<%s> selected = new %s<>();
                                                for (%s<%s> it = nodes.elements(); it.hasNext();) {
                                                  %s node = it.next();
                                                  if (node.has("class_uid")
                                                      && "%s".equals(node.get("class_uid").asText())) {
                                                    selected.add(converter.to%s(node));
                                                  }
                                                }
                                                return selected;
                                            """
                            .formatted(
                                List.class.getCanonicalName(),
                                md.fullyQualifiedClassName(),
                                ArrayList.class.getCanonicalName(),
                                Iterator.class.getCanonicalName(),
                                JsonNode.class.getCanonicalName(),
                                JsonNode.class.getCanonicalName(),
                                md.ocsfClassUid(),
                                md.className()))
                    .withArgument(new ArgumentMeta(ArrayNode.class, "nodes"))
                    .withThrow(JsonProcessingException.class));
      }
    }

    return classMeta.emit();
  }
}
