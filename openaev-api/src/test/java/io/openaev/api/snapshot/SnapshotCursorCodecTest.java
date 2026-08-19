package io.openaev.api.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.openaev.api.snapshot.SnapshotCursorCodec.SnapshotCursor;
import io.openaev.rest.exception.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link SnapshotCursorCodec}: no Spring context needed (story 7505, §10.3).
 */
class SnapshotCursorCodecTest {

  private static final String TENANT_ID = "tenant-a";

  private SnapshotCursorCodec codec;

  @BeforeEach
  void setUp() {
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    codec = new SnapshotCursorCodec(objectMapper);
  }

  private String base64Of(String json) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  @Nested
  @DisplayName("Round trip")
  class RoundTrip {

    @Test
    @DisplayName("encode then decode yields an equal cursor, microseconds included")
    void given_cursor_should_round_trip() {
      // -- ARRANGE --
      Instant ts = Instant.parse("2024-01-01T00:00:00.123456Z");
      SnapshotCursor cursor = new SnapshotCursor(1, TENANT_ID, ts, "doc-id");

      // -- ACT --
      String encoded = codec.encode(cursor);
      SnapshotCursor decoded = codec.decode(encoded, TENANT_ID);

      // -- ASSERT --
      assertThat(decoded).isEqualTo(cursor);
    }
  }

  @Nested
  @DisplayName("Opacity")
  class Opacity {

    @Test
    @DisplayName("the encoded cursor is URL-safe, unpadded base64")
    void given_cursor_should_be_url_safe() {
      // -- ACT --
      String encoded = codec.encode(new SnapshotCursor(1, TENANT_ID, Instant.now(), "doc-id"));

      // -- ASSERT --
      assertThat(encoded).doesNotContain("+", "/", "=");
    }
  }

  @Nested
  @DisplayName("Rejections")
  class Rejections {

    @Test
    @DisplayName("not base64 is rejected")
    void given_invalid_base64_should_reject() {
      assertThatThrownBy(() -> codec.decode("!!!not-base64!!!", TENANT_ID))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("base64 of non-JSON is rejected")
    void given_non_json_payload_should_reject() {
      assertThatThrownBy(() -> codec.decode(base64Of("this is not json"), TENANT_ID))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("an unsupported cursor version is rejected")
    void given_unsupported_version_should_reject() {
      String json =
          "{\"v\":2,\"tenant\":\""
              + TENANT_ID
              + "\",\"ts\":\"2024-01-01T00:00:00Z\",\"id\":\"doc-id\"}";
      assertThatThrownBy(() -> codec.decode(base64Of(json), TENANT_ID))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("a missing version is rejected")
    void given_missing_version_should_reject() {
      String json =
          "{\"tenant\":\"" + TENANT_ID + "\",\"ts\":\"2024-01-01T00:00:00Z\",\"id\":\"doc-id\"}";
      assertThatThrownBy(() -> codec.decode(base64Of(json), TENANT_ID))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("a cursor issued for another tenant is rejected")
    void given_foreign_tenant_should_reject() {
      String encoded = codec.encode(new SnapshotCursor(1, "tenant-b", Instant.now(), "doc-id"));
      assertThatThrownBy(() -> codec.decode(encoded, TENANT_ID))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("a blank id is rejected")
    void given_blank_id_should_reject() {
      String json =
          "{\"v\":1,\"tenant\":\"" + TENANT_ID + "\",\"ts\":\"2024-01-01T00:00:00Z\",\"id\":\"\"}";
      assertThatThrownBy(() -> codec.decode(base64Of(json), TENANT_ID))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("a null timestamp is rejected")
    void given_null_timestamp_should_reject() {
      String json = "{\"v\":1,\"tenant\":\"" + TENANT_ID + "\",\"ts\":null,\"id\":\"doc-id\"}";
      assertThatThrownBy(() -> codec.decode(base64Of(json), TENANT_ID))
          .isInstanceOf(BadRequestException.class);
    }
  }
}
