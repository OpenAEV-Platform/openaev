package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AbstractOutputProcessorTest {

    private static class TestOutputProcessor extends AbstractOutputProcessor {
        TestOutputProcessor() {
            super(null, null, Collections.emptyList(), false);
        }
    }

    private TestOutputProcessor processor;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        processor = new TestOutputProcessor();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("buildString should join array elements and trim quotes")
    void testBuildStringArray() throws Exception {
        JsonNode node = objectMapper.readTree("[\"foo\", \"bar\"]");
        String result = processor.buildString(node);
        assertEquals("foo bar", result);
    }

    @Test
    @DisplayName("buildString should trim quotes from string node")
    void testBuildStringString() throws Exception {
        JsonNode node = objectMapper.readTree("\"baz\"");
        String result = processor.buildString(node);
        assertEquals("baz", result);
    }

    @Test
    @DisplayName("buildString with key should extract and process value")
    void testBuildStringWithKey() throws Exception {
        JsonNode node = objectMapper.readTree("{\"key\": [\"a\", \"b\"]}");
        String result = processor.buildString(node, "key");
        assertEquals("a b", result);
    }

    @Test
    @DisplayName("buildString with key should return empty string if key missing or null")
    void testBuildStringWithKeyMissingOrNull() throws Exception {
        JsonNode node = objectMapper.readTree("{}");
        assertEquals("", processor.buildString(node, "missing"));
        JsonNode node2 = objectMapper.readTree("{\"key\": null}");
        assertEquals("", processor.buildString(node2, "key"));
    }

    @Test
    @DisplayName("trimQuotes should remove leading and trailing quotes")
    void testTrimQuotes() {
        assertEquals("foo", processor.trimQuotes("\"foo\""));
        assertEquals("bar", processor.trimQuotes("bar"));
        assertEquals("foo\"bar", processor.trimQuotes("\"foo\"bar"));
        assertEquals("foo\"bar", processor.trimQuotes("foo\"bar"));
    }
}

