package io.openaev.logging;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

@DisplayName("StackDepthLimitingJsonEncoder tests")
class StackDepthLimitingJsonEncoderTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private StackDepthLimitingJsonEncoder encoder;
  private LoggerContext loggerContext;

  @BeforeEach
  void setUp() {
    loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    encoder = new StackDepthLimitingJsonEncoder();
    encoder.setContext(loggerContext);
    encoder.start();
  }

  private LoggingEvent createEvent(Throwable throwable) {
    LoggingEvent event = new LoggingEvent();
    event.setLoggerContext(loggerContext);
    event.setLoggerName("io.openaev.logging.Test");
    event.setLevel(ch.qos.logback.classic.Level.ERROR);
    event.setMessage("test error");
    event.setThrowableProxy(new ThrowableProxy(throwable));
    event.setTimeStamp(System.currentTimeMillis());
    return event;
  }

  @Test
  @DisplayName("message field should carry the resolved placeholders, not the raw pattern")
  void testMessageIsFormatted() throws Exception {
    encoder.setWithArguments(false);

    LoggingEvent event = new LoggingEvent();
    event.setLoggerContext(loggerContext);
    event.setLoggerName("io.openaev.logging.Test");
    event.setLevel(ch.qos.logback.classic.Level.INFO);
    event.setMessage("Indexing ({}) in progress for {}");
    event.setArgumentArray(new Object[] {500, "expectation-inject"});
    event.setTimeStamp(System.currentTimeMillis());

    JsonNode node = MAPPER.readTree(new String(encoder.encode(event)));

    assertEquals("Indexing (500) in progress for expectation-inject", node.get("message").asText());
    assertNull(node.get("arguments"), "arguments must not duplicate the resolved values");
  }

  @Test
  @DisplayName("Formatted message and truncated stack trace should combine on the same event")
  void testFormattedMessageWithThrowable() throws Exception {
    encoder.setMaxStackDepth(1);

    LoggingEvent event = new LoggingEvent();
    event.setLoggerContext(loggerContext);
    event.setLoggerName("io.openaev.logging.Test");
    event.setLevel(ch.qos.logback.classic.Level.ERROR);
    event.setMessage("Indexing failed for {}");
    event.setArgumentArray(new Object[] {"expectation-inject"});
    event.setThrowableProxy(new ThrowableProxy(new RuntimeException("boom")));
    event.setTimeStamp(System.currentTimeMillis());

    JsonNode node = MAPPER.readTree(new String(encoder.encode(event)));

    assertEquals("Indexing failed for expectation-inject", node.get("message").asText());
    assertTrue(node.has("throwable"), "throwable must still be serialized");
  }

  @Test
  @DisplayName("maxStackDepth=1 should produce valid JSON with truncation marker")
  void testMaxStackDepth1() throws Exception {
    encoder.setMaxStackDepth(1);

    // Create a deep exception chain: root -> cause1 -> cause2 -> cause3
    Exception root =
        new RuntimeException(
            "root",
            new IllegalStateException(
                "cause1",
                new IllegalArgumentException("cause2", new NullPointerException("cause3"))));

    LoggingEvent event = createEvent(root);
    byte[] encoded = encoder.encode(event);
    String json = new String(encoded);

    // Must be valid JSON
    JsonNode node =
        assertDoesNotThrow(() -> MAPPER.readTree(json), "Output must be valid JSON: " + json);

    // Should have throwable info
    assertTrue(json.contains("RuntimeException"), "Should contain root exception class");

    // All causes should be present (no cause depth limit)
    assertTrue(json.contains("cause1"), "cause1 should be present");
    assertTrue(json.contains("cause2"), "cause2 should be present");
    assertTrue(json.contains("cause3"), "cause3 should be present");

    // The truncation marker puts the full message in the StackTraceElement className field,
    // which the JsonEncoder serializes as the "className" JSON property.
    assertTrue(
        json.contains("non-application frames truncated (io.openaev frames preserved)"),
        "Should contain truncation marker message");

    System.out.println("=== maxStackDepth=1 ===");
    System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node));
  }

  @Test
  @DisplayName("maxStackDepth=2 should produce valid JSON")
  void testMaxStackDepth2() throws Exception {
    encoder.setMaxStackDepth(2);

    Exception ex = new RuntimeException("boom");
    LoggingEvent event = createEvent(ex);
    byte[] encoded = encoder.encode(event);
    String json = new String(encoded);

    JsonNode node =
        assertDoesNotThrow(() -> MAPPER.readTree(json), "Output must be valid JSON: " + json);

    System.out.println("=== maxStackDepth=2 ===");
    System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node));
  }

  @Test
  @DisplayName("No throwable should produce valid JSON without exception data")
  void testNoThrowable() throws Exception {
    LoggingEvent event = new LoggingEvent();
    event.setLoggerContext(loggerContext);
    event.setLoggerName("io.openaev.logging.Test");
    event.setLevel(ch.qos.logback.classic.Level.INFO);
    event.setMessage("no error here");
    event.setTimeStamp(System.currentTimeMillis());

    byte[] encoded = encoder.encode(event);
    String json = new String(encoded);

    assertDoesNotThrow(() -> MAPPER.readTree(json), "Output must be valid JSON: " + json);
    // No exception class names should appear
    assertFalse(json.contains("RuntimeException"), "Should not contain exception classes");
    assertFalse(json.contains("NullPointerException"), "Should not contain exception classes");
  }

  @Test
  @DisplayName("Default values should preserve all causes in a deep chain")
  void testDefaultValues() throws Exception {
    // Use defaults (maxStackDepth=80, no cause limit)
    // 13 throwables: level0 (root) through level12
    Exception deep =
        new RuntimeException(
            "level0",
            new RuntimeException(
                "level1",
                new RuntimeException(
                    "level2",
                    new RuntimeException(
                        "level3",
                        new RuntimeException(
                            "level4",
                            new RuntimeException(
                                "level5",
                                new RuntimeException(
                                    "level6",
                                    new RuntimeException(
                                        "level7",
                                        new RuntimeException(
                                            "level8",
                                            new RuntimeException(
                                                "level9",
                                                new RuntimeException(
                                                    "level10",
                                                    new RuntimeException(
                                                        "level11",
                                                        new RuntimeException("level12")))))))))))));

    LoggingEvent event = createEvent(deep);
    byte[] encoded = encoder.encode(event);
    String json = new String(encoded);

    assertDoesNotThrow(() -> MAPPER.readTree(json), "Output must be valid JSON: " + json);

    // All causes should be present (no cause depth limit)
    assertTrue(json.contains("level0"), "level0 (root) should be present");
    assertTrue(json.contains("level9"), "level9 should be present");
    assertTrue(json.contains("level10"), "level10 should be present");
    assertTrue(json.contains("level11"), "level11 should be present");
    assertTrue(json.contains("level12"), "level12 should be present");

    System.out.println("=== defaults (80, all causes), 13-deep chain ===");
    System.out.println("JSON length: " + json.length() + " bytes");
  }

  @Test
  @DisplayName("App frames (io.openaev) in the middle section should always be preserved")
  void testAppFramesPreserved() throws Exception {
    encoder.setMaxStackDepth(5);

    // Build a synthetic exception with a mix of framework and app frames.
    // Total: 20 frames. With maxStackDepth=5: keepTop=3, keepBottom=2.
    // Middle section (indices 3..17) should have its io.openaev frames preserved.
    Exception ex = new RuntimeException("app frame test");
    StackTraceElement[] fakeTrace = new StackTraceElement[20];
    for (int i = 0; i < 20; i++) {
      // Sprinkle app frames in the middle at positions 7, 10, 14
      if (i == 7 || i == 10 || i == 14) {
        fakeTrace[i] =
            new StackTraceElement("io.openaev.service.MyService", "handle", "MyService.java", i);
      } else {
        fakeTrace[i] =
            new StackTraceElement(
                "org.springframework.proxy.CglibProxy$" + i, "invoke", "CglibProxy.java", i);
      }
    }
    ex.setStackTrace(fakeTrace);

    LoggingEvent event = createEvent(ex);
    byte[] encoded = encoder.encode(event);
    String json = new String(encoded);

    JsonNode node =
        assertDoesNotThrow(() -> MAPPER.readTree(json), "Output must be valid JSON: " + json);

    System.out.println("=== app frame preservation test ===");
    System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node));

    // All io.openaev frames should be preserved
    assertTrue(json.contains("io.openaev.service.MyService"), "App frames must be preserved");

    // The truncation marker should only count framework frames, not app frames
    assertTrue(
        json.contains("non-application frames truncated (io.openaev frames preserved)"),
        "Should contain truncation marker");

    // Framework frames from the middle should be truncated
    // Middle section is indices 3..17 (15 frames). 3 are app frames, so 12 framework frames cut.
    assertTrue(
        json.contains("12 non-application frames truncated"),
        "Should truncate exactly 12 framework frames");

    System.out.println("=== app frame preservation test ===");
    System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node));
  }
}
