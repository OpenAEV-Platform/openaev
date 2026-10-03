package io.openaev.api.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE;
import io.openaev.database.model.Command;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Payload;
import io.openaev.database.model.SecurityPlatform.SECURITY_PLATFORM_TYPE;
import io.openaev.database.model.Tenant;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pins how a payload's expected security platforms are stored and read back.
 *
 * <p>The field holds a map from expectation type to the security platform types that expectation is
 * instantiated against. It is mapped natively ({@code @JdbcTypeCode(SqlTypes.JSON)}) rather than
 * with hypersistence {@code JsonType}, because the latter snapshots the loaded value by serializing
 * and deserializing it under the raw runtime class, which loses the inner {@code
 * List<SECURITY_PLATFORM_TYPE>} element type: the snapshot then holds strings where the loaded
 * value holds enums, the entity is considered changed after every load and a plain read flushes an
 * {@code UPDATE} of the payload row (#8236, #6437).
 *
 * <p>That change is a change of mapping, so what needs pinning is that it changed nothing else: the
 * JSON written to the column and the value read back, on a populated map, an empty one and a null.
 * Without this, a later refactor can silently start storing enum ordinals or a wrapped object and
 * every existing row becomes unreadable.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("a payload's expected security platforms round-trip unchanged")
class PayloadExpectedSecurityPlatformsStorageTest extends IntegrationTest {

  @Autowired private ObjectMapper objectMapper;

  @Nested
  @DisplayName("with platforms set")
  class WithPlatforms {

    @Test
    @DisplayName("given a populated map, when read back, then the enums and the stored JSON match")
    void given_a_populated_map_should_round_trip() throws Exception {
      String id = persistPayloadWith(twoExpectations());

      Payload loaded = reload(id);

      // Built again rather than reusing the instance handed to the entity: comparing against that
      // one would be blind to a mapping that mutates the value it was given.
      assertEquals(
          twoExpectations(),
          loaded.getExpectedSecurityPlatforms(),
          "the map must come back with its expectation types and platform types as enums");
      assertEquals(
          objectMapper.readTree("{\"PREVENTION\":[\"EDR\",\"XDR\"],\"DETECTION\":[\"SIEM\"]}"),
          objectMapper.readTree(storedJson(id)),
          "the column must hold expectation names mapped to platform names, nothing else");
    }
  }

  @Nested
  @DisplayName("with the edge values")
  class EdgeValues {

    @Test
    @DisplayName("given an empty map, when read back, then it is still an empty map")
    void given_an_empty_map_should_round_trip() throws Exception {
      String id = persistPayloadWith(new LinkedHashMap<>());

      Payload loaded = reload(id);

      assertTrue(
          loaded.getExpectedSecurityPlatforms().isEmpty(),
          "an empty map must not come back null or populated");
      assertEquals(
          objectMapper.readTree("{}"),
          objectMapper.readTree(storedJson(id)),
          "an empty map must be stored as an empty JSON object");
    }

    @Test
    @DisplayName("given no platforms at all, when read back, then it is still null")
    void given_a_null_map_should_round_trip() {
      String id = persistPayloadWith(null);

      Payload loaded = reload(id);

      assertNull(loaded.getExpectedSecurityPlatforms(), "a null map must come back null");
      assertNull(storedJson(id), "a null map must leave the column null");
    }
  }

  private Map<EXPECTATION_TYPE, List<SECURITY_PLATFORM_TYPE>> twoExpectations() {
    Map<EXPECTATION_TYPE, List<SECURITY_PLATFORM_TYPE>> platforms = new LinkedHashMap<>();
    platforms.put(
        EXPECTATION_TYPE.PREVENTION,
        new ArrayList<>(List.of(SECURITY_PLATFORM_TYPE.EDR, SECURITY_PLATFORM_TYPE.XDR)));
    platforms.put(
        EXPECTATION_TYPE.DETECTION, new ArrayList<>(List.of(SECURITY_PLATFORM_TYPE.SIEM)));
    return platforms;
  }

  private String persistPayloadWith(Map<EXPECTATION_TYPE, List<SECURITY_PLATFORM_TYPE>> platforms) {
    Command payload =
        new Command(UUID.randomUUID().toString(), Command.COMMAND_TYPE, "expected-platforms");
    payload.setContent("echo round-trip");
    payload.setExecutor("PowerShell");
    payload.setPlatforms(new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Windows});
    payload.setSource(Payload.PAYLOAD_SOURCE.MANUAL);
    payload.setStatus(Payload.PAYLOAD_STATUS.VERIFIED);
    payload.setTenant(entityManager.getReference(Tenant.class, Tenant.DEFAULT_TENANT_UUID));
    payload.setExpectedSecurityPlatforms(platforms);
    entityManager.persist(payload);
    entityManager.flush();
    return payload.getId();
  }

  private Payload reload(String id) {
    entityManager.flush();
    entityManager.clear();
    return entityManager.find(Payload.class, id);
  }

  /**
   * The column as PostgreSQL holds it, so the assertion does not go through the mapping it tests.
   */
  private String storedJson(String id) {
    entityManager.flush();
    Object raw =
        entityManager
            .createNativeQuery(
                "SELECT payload_expected_security_platforms::text FROM payloads"
                    + " WHERE payload_id = ?1")
            .setParameter(1, id)
            .getSingleResult();
    return raw == null ? null : raw.toString();
  }
}
