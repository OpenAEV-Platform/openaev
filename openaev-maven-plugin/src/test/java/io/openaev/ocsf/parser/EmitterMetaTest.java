package io.openaev.ocsf.parser;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.openaev.ocsf.parser.generator.emission.meta.Modifier;
import io.openaev.ocsf.parser.generator.emission.meta.annotation.AnnotationMeta;
import io.openaev.ocsf.parser.generator.emission.meta.cls.ClassMeta;
import io.openaev.ocsf.parser.generator.emission.meta.cls.ExtendMeta;
import io.openaev.ocsf.parser.generator.emission.meta.doc.JavadocMeta;
import io.openaev.ocsf.parser.generator.emission.meta.enums.EnumMeta;
import io.openaev.ocsf.parser.generator.emission.meta.enums.OptionMeta;
import io.openaev.ocsf.parser.generator.emission.meta.field.FieldMeta;
import io.openaev.ocsf.parser.generator.emission.meta.method.ArgumentMeta;
import io.openaev.ocsf.parser.generator.emission.meta.method.MethodMeta;
import java.io.IOException;
import lombok.Getter;
import org.junit.jupiter.api.Test;

public class EmitterMetaTest {
  @Test
  void classMetaEmitsCorrectly() {
    ClassMeta classMeta = new ClassMeta();
    classMeta
        .withName("TestClass")
        .withPackage("io.openaev.ocsf.datatypes")
        .withImport("org.apache.hc.client.Client")
        .withExtend(new ExtendMeta("BaseClass").withGenericTypeArgument(String.class))
        .withField(
            new FieldMeta(Modifier.PRIVATE, "String", "stringField")
                .withAnnotation(new AnnotationMeta(Getter.class))
                .withInitialiser("\"test\"")
                .withJavadoc(new JavadocMeta("String member field")))
        .withMethod(
            new MethodMeta(Modifier.PUBLIC, String.class.getName(), "toString", "return arg;")
                .withAnnotation(new AnnotationMeta(Override.class))
                .withArgument(new ArgumentMeta(String.class, "arg"))
                .withThrow(IOException.class))
        .withAnnotation(
            new AnnotationMeta(Getter.class).withAttribute("value", "AccessLevel.NONE"));
    assertThat(classMeta.emit())
        .isEqualTo(
            """
               package io.openaev.ocsf.datatypes;

               import org.apache.hc.client.Client;

               @lombok.Getter(value = "AccessLevel.NONE")
               public class TestClass extends BaseClass<java.lang.String> {
                 /**
                  * String member field
                  */
                 @lombok.Getter
                 private String stringField = "test";
                 @java.lang.Override
                 public java.lang.String toString(java.lang.String arg) throws java.io.IOException {
                   return arg;
                 }
               }
               """);
  }

  @Test
  void enumMetaEmitsCorrectly() {
    EnumMeta meta = new EnumMeta();
    meta.withName("TestEnum")
        .withPackage("io.openaev.ocsf.enums")
        .withImport("org.apache.hc.client.Client")
        .withOption(new OptionMeta("ONE").withValue("one"))
        .withOption(new OptionMeta("TWO").withValue("two"))
        .withOption(new OptionMeta("THREE").withValue("three"))
        .withField(
            new FieldMeta(Modifier.PRIVATE, "String", "stringField")
                .withAnnotation(new AnnotationMeta(Getter.class))
                .withInitialiser("\"test\"")
                .withJavadoc(new JavadocMeta("String member field")))
        .withMethod(
            new MethodMeta(Modifier.PUBLIC, String.class.getName(), "toString", "return arg;")
                .withAnnotation(new AnnotationMeta(Override.class))
                .withArgument(new ArgumentMeta(String.class, "arg"))
                .withThrow(IOException.class));
    assertThat(meta.emit())
        .isEqualTo(
            """
                     package io.openaev.ocsf.enums;

                     import org.apache.hc.client.Client;

                     public enum TestEnum {
                       ONE("one"),
                       THREE("three"),
                       TWO("two");

                       /**
                        * String member field
                        */
                       @lombok.Getter
                       private String stringField = "test";
                       @java.lang.Override
                       public java.lang.String toString(java.lang.String arg) throws java.io.IOException {
                         return arg;
                       }
                     }
                     """);
  }
}
