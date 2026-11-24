package io.openaev.xtm.counterpart;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Counterparts read with a service credential show the link alone, per user")
class CounterpartServiceSegregationTest {

  private static final String TENANT = "tenant-a";
  private static final String KEY = "cve-2026-0001";

  /** A provider answering a named counterpart with facts, counting its calls. */
  private static final class FakeProvider implements CounterpartProvider {
    private final boolean asCaller;
    final AtomicInteger calls = new AtomicInteger();

    FakeProvider(boolean asCaller) {
      this.asCaller = asCaller;
    }

    @Override
    public String product() {
      return "opencrq";
    }

    @Override
    public Optional<String> connectedUrl(String tenantId) {
      return Optional.of("https://crq.example/");
    }

    @Override
    public boolean supports(CounterpartKind kind) {
      return true;
    }

    @Override
    public boolean readsAsCaller() {
      return asCaller;
    }

    @Override
    public Map<String, Counterpart> resolve(
        String tenantId, CounterpartKind kind, Collection<String> keys) {
      calls.incrementAndGet();
      Map<String, Counterpart> found = new HashMap<>();
      for (String key : keys) {
        found.put(
            key,
            new Counterpart(
                key,
                "opencrq",
                "remote-id",
                "risk_scenario",
                "Ransomware on the billing cluster",
                "https://crq.example/risk/remote-id",
                List.of(new Counterpart.Fact("annual_loss", "2400000"))));
      }
      return found;
    }
  }

  private static Counterpart only(List<CounterpartService.Resolution> resolutions) {
    assertThat(resolutions).hasSize(1);
    assertThat(resolutions.getFirst().status()).isEqualTo(CounterpartService.Status.MATCH);
    return resolutions.getFirst().counterpart();
  }

  @Test
  @DisplayName("a service-credential lookup gives the link only: no remote name, type or fact")
  void serviceCredentialGivesTheLinkOnly() {
    CounterpartService service = new CounterpartService(List.of(new FakeProvider(false)));

    Counterpart counterpart =
        only(
            service.resolve(
                TENANT, "restricted-user", CounterpartKind.VULNERABILITY, List.of(KEY)));

    assertThat(counterpart.url()).isEqualTo("https://crq.example/risk/remote-id");
    assertThat(counterpart.name()).isEqualTo(counterpart.key());
    assertThat(counterpart.name()).doesNotContain("Ransomware");
    assertThat(counterpart.entityType()).isEqualTo("vulnerability");
    assertThat(counterpart.facts()).isEmpty();
  }

  @Test
  @DisplayName("a lookup made as the caller keeps what the caller may read")
  void callerLookupKeepsTheFacts() {
    CounterpartService service = new CounterpartService(List.of(new FakeProvider(true)));

    Counterpart counterpart =
        only(service.resolve(TENANT, "member", CounterpartKind.VULNERABILITY, List.of(KEY)));

    assertThat(counterpart.name()).isEqualTo("Ransomware on the billing cluster");
    assertThat(counterpart.facts()).hasSize(1);
  }

  @Test
  @DisplayName("the cache is per user: another user's lookup is never served from it")
  void cacheIsPerUser() {
    FakeProvider provider = new FakeProvider(true);
    CounterpartService service = new CounterpartService(List.of(provider));

    service.resolve(TENANT, "member", CounterpartKind.VULNERABILITY, List.of(KEY));
    service.resolve(TENANT, "member", CounterpartKind.VULNERABILITY, List.of(KEY));
    assertThat(provider.calls.get()).isEqualTo(1);

    service.resolve(TENANT, "other-user", CounterpartKind.VULNERABILITY, List.of(KEY));
    assertThat(provider.calls.get()).isEqualTo(2);
  }
}
