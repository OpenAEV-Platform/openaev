package io.openaev.ocsf.parser;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.openaev.ocsf.parser.generator.emission.meta.annotation.AnnotationMeta;
import io.openaev.ocsf.parser.generator.emission.meta.cls.ClassMeta;
import io.openaev.ocsf.parser.generator.emission.meta.method.ArgumentMeta;
import io.openaev.ocsf.parser.generator.emission.meta.method.MethodMeta;
import java.io.File;
import lombok.Getter;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;

public class GenerateParserTest {
  // @Test
  void test() throws MojoExecutionException {
    new GenerateParser(new File(".."), "test").execute();
  }

  @Test
  void emit() {
    ClassMeta classMeta = new ClassMeta();
    classMeta
        .withName("TestClass")
        .withPackage("io.openaev.ocsf.datatypes")
        .withImport("org.apache.hc.client.Client")
        .withMethod(
            new MethodMeta("public", String.class, "toString", "return arg;")
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
