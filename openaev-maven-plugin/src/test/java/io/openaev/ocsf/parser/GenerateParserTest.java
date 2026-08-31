package io.openaev.ocsf.parser;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.ocsf.parser.generator.Generator;
import io.openaev.ocsf.parser.generator.emission.DatatypeClassGenerator;
import io.openaev.ocsf.parser.generator.emission.meta.Modifier;
import io.openaev.ocsf.parser.generator.emission.meta.annotation.AnnotationMeta;
import io.openaev.ocsf.parser.generator.emission.meta.cls.ClassMeta;
import io.openaev.ocsf.parser.generator.emission.meta.method.ArgumentMeta;
import io.openaev.ocsf.parser.generator.emission.meta.method.MethodMeta;
import io.openaev.ocsf.parser.schema.Ocsf;
import io.openaev.ocsf.parser.schema.OcsfSchemaVersion;
import io.openaev.ocsf.parser.schema.SchemaDimension;
import io.openaev.ocsf.parser.schema.SchemaSource;
import io.openaev.ocsf.parser.schema.source.ReferentialSource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import lombok.Getter;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;

public class GenerateParserTest {
  @Test
  void test() throws MojoExecutionException {
    new GenerateParser(new File(".."), "test").execute();
  }

  @Test
  void emitDatatypes() throws IOException {
    Path resources = Paths.get(getClass().getResource("").getPath());
    PluginContext ctx = new PluginContext(resources, Paths.get(""));

    SchemaSource schema = Ocsf.schema(OcsfSchemaVersion._1_9_0, ctx);
    schema.refreshAllSources();

    DatatypeClassGenerator gen = new DatatypeClassGenerator();

    JsonNode datatypes = schema.getContents(SchemaDimension.DATATYPES.name());

    for (Map.Entry<String, JsonNode> prop : datatypes.properties()) {
      gen.metadata(schema.getVersion(), prop.getKey(), prop.getValue(), null);
    }
  }

  @Test
  void emitDatatypes2() throws IOException {
    Path resources = Paths.get(getClass().getResource("").getPath());
    PluginContext ctx = new PluginContext(resources, resources);

    SchemaSource schema = Ocsf.schema(OcsfSchemaVersion._1_9_0, ctx);
    schema.refreshAllSources();
    Generator gen = new Generator(schema, ctx);
    gen.generate();
  }

  @Test
  void objectsResource() throws IOException {
    Path resources = Paths.get(getClass().getResource("").getPath());
    PluginContext ctx = new PluginContext(resources, Paths.get(""));

    SchemaSource schema = Ocsf.schema(OcsfSchemaVersion._1_9_0, ctx);
    // schema.refreshAllSources();

    JsonNode objects = schema.getContents(SchemaDimension.OBJECTS.name());
    JsonNode reg_key = schema.getContents("reg_key");
    ((ReferentialSource) schema.getSource("OBJECTS")).getSubsourceKeys();
  }

  @Test
  void emit() {
    ClassMeta classMeta = new ClassMeta();
    classMeta
        .withName("TestClass")
        .withPackage("io.openaev.ocsf.datatypes")
        .withImport("org.apache.hc.client.Client")
        .withMethod(
            new MethodMeta(Modifier.PUBLIC, String.class.getName(), "toString", "return arg;")
                .withAnnotation(new AnnotationMeta(Override.class))
                .withArgument(new ArgumentMeta(String.class, "arg")))
        .withAnnotation(
            new AnnotationMeta(Getter.class).withAttribute("value", "AccessLevel.NONE"));
    assertThat(classMeta.emit())
        .isEqualTo(
            """
                package io.openaev.ocsf.datatypes;

                import org.apache.hc.client.Client;

                @lombok.Getter()
                public class TestClass {
                  @java.lang.Override
                  public java.lang.String toString(java.lang.String arg) {
                    return arg;
                  }
                }
                """);
  }
}
