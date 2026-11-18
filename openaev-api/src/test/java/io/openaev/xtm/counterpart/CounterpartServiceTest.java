package io.openaev.xtm.counterpart;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.xtm.counterpart.CounterpartService.Resolution;
import io.openaev.xtm.counterpart.CounterpartService.Status;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CounterpartServiceTest {

  /** A product double: knows T1059 only, counts its calls, can be switched off or broken. */
  static class FakeProvider implements CounterpartProvider {
    boolean connected = true;
    boolean broken = false;
    final List<Collection<String>> calls = new ArrayList<>();

    @Override
    public String product() {
      return "opencti";
    }

    @Override
    public Optional<String> connectedUrl(String tenantId) {
      return connected ? Optional.of("https://octi/") : Optional.empty();
    }

    @Override
    public boolean supports(CounterpartKind kind) {
      return true;
    }

    @Override
    public Map<String, Counterpart> resolve(
        String tenantId, CounterpartKind kind, Collection<String> keys)
        throws CounterpartUnavailableException {
      calls.add(List.copyOf(keys));
      if (broken) {
        throw new CounterpartUnavailableException("down");
      }
      return keys.contains("T1059")
          ? Map.of(
              "T1059",
              new Counterpart(
                  "T1059", "opencti", "id-1", "Attack-Pattern", "Command", "https://octi/x"))
          : Map.of();
    }
  }

  @Test
  @DisplayName("a match carries the deep link, a miss is no_match, keys are normalised")
  void matchAndMiss() {
    FakeProvider provider = new FakeProvider();
    CounterpartService service = new CounterpartService(List.of(provider));

    List<Resolution> out =
        service.resolve("t", CounterpartKind.ATTACK_PATTERN, List.of(" t1059 ", "T9999", ""));

    assertThat(out).hasSize(2);
    assertThat(out.get(0).status()).isEqualTo(Status.MATCH);
    assertThat(out.get(0).counterpart().url()).isEqualTo("https://octi/x");
    assertThat(out.get(1).status()).isEqualTo(Status.NO_MATCH);
    assertThat(provider.calls).containsExactly(List.of("T1059", "T9999"));
  }

  @Test
  @DisplayName("a second lookup of the same keys is served from the cache, misses included")
  void cached() {
    FakeProvider provider = new FakeProvider();
    CounterpartService service = new CounterpartService(List.of(provider));

    service.resolve("t", CounterpartKind.ATTACK_PATTERN, List.of("T1059", "T9999"));
    List<Resolution> again =
        service.resolve("t", CounterpartKind.ATTACK_PATTERN, List.of("T1059", "T9999"));

    assertThat(provider.calls).hasSize(1);
    assertThat(again).extracting(Resolution::status).containsExactly(Status.MATCH, Status.NO_MATCH);
  }

  @Test
  @DisplayName("a tenant without the product answers not_connected and calls nothing")
  void notConnected() {
    FakeProvider provider = new FakeProvider();
    provider.connected = false;
    CounterpartService service = new CounterpartService(List.of(provider));

    List<Resolution> out = service.resolve("t", CounterpartKind.VULNERABILITY, List.of("CVE-1"));

    assertThat(out).extracting(Resolution::status).containsExactly(Status.NOT_CONNECTED);
    assertThat(provider.calls).isEmpty();
    assertThat(service.connections("t").get(0).connected()).isFalse();
  }

  @Test
  @DisplayName("an unreachable product answers unavailable and is retried on the next lookup")
  void unavailableIsNotCached() {
    FakeProvider provider = new FakeProvider();
    provider.broken = true;
    CounterpartService service = new CounterpartService(List.of(provider));

    List<Resolution> out = service.resolve("t", CounterpartKind.ATTACK_PATTERN, List.of("T1059"));
    assertThat(out).extracting(Resolution::status).containsExactly(Status.UNAVAILABLE);

    provider.broken = false;
    List<Resolution> retried =
        service.resolve("t", CounterpartKind.ATTACK_PATTERN, List.of("T1059"));
    assertThat(retried).extracting(Resolution::status).containsExactly(Status.MATCH);
    assertThat(provider.calls).hasSize(2);
  }

  @Test
  @DisplayName("the kind decides the normalisation of a key")
  void normalisation() {
    assertThat(CounterpartKind.VULNERABILITY.normalise("cve-2024-1")).isEqualTo("CVE-2024-1");
    assertThat(CounterpartKind.ASSET.normalise(" Host-01.Corp ")).isEqualTo("host-01.corp");
    assertThat(CounterpartKind.THREAT.normalise("report--AbC")).isEqualTo("report--AbC");
    assertThat(CounterpartKind.ATTACK_PATTERN.normalise("  ")).isNull();
  }
}
