package io.openaev.architecture.fixture;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** The shapes in which a test class can pin the activation list, for the pinned-list guard. */
public final class PinnedActiveTablesFixtures {

  private PinnedActiveTablesFixtures() {}

  @TestPropertySource(properties = "openaev.tenant.active-tables=tags,documents")
  public static class PinsThroughTestPropertySource {}

  /** Inherits the pin: the site is the parent that wrote it, not this class. */
  public static class InheritsThePin extends PinsThroughTestPropertySource {}

  @SpringBootTest(
      properties = {"openaev.other=1", "openaev.tenant.active-tables: assets , findings"})
  public static class PinsThroughSpringBootTestWithColon {}

  @Retention(RetentionPolicy.RUNTIME)
  @TestPropertySource(properties = "openaev.tenant.active-tables=" + "secrets")
  public @interface ComposedPin {}

  @ComposedPin
  public static class PinsThroughAComposedAnnotation {}

  @TestPropertySource(properties = "openaev.tenant.active-tables=")
  public static class PinsAnEmptyList {}

  @TestPropertySource(properties = "openaev.marking.active-tables=assets")
  public static class SetsAnotherProperty {}

  public static class PinsNothing {}
}
