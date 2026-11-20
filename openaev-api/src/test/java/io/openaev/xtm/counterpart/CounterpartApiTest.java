package io.openaev.xtm.counterpart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.service.PermissionService;
import io.openaev.service.UserService;
import io.openaev.xtm.counterpart.CounterpartServiceTest.FakeProvider;
import io.openaev.xtm.counterpart.LocalObjectService.Fact;
import io.openaev.xtm.counterpart.LocalObjectService.LocalObject;
import io.openaev.xtm.counterpart.form.CounterpartLookupInput;
import io.openaev.xtm.counterpart.form.CounterpartOutput;
import io.openaev.xtm.counterpart.form.LocalObjectOutput;
import io.openaev.xtm.counterpart.form.XtmConnectionOutput;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CounterpartApiTest {

  /** A product that knows no kind: the service must skip it. */
  static class SilentProvider implements CounterpartProvider {
    @Override
    public String product() {
      return "opencrq";
    }

    @Override
    public Optional<String> connectedUrl(String tenantId) {
      return Optional.of("https://ocrq/");
    }

    @Override
    public boolean supports(CounterpartKind kind) {
      return false;
    }

    @Override
    public Map<String, Counterpart> resolve(
        String tenantId, CounterpartKind kind, Collection<String> keys) {
      throw new IllegalStateException("never called for an unsupported kind");
    }
  }

  private FakeProvider provider;
  private LocalObjectService localObjects;
  private CounterpartApi api;

  @BeforeEach
  void setUp() {
    provider = new FakeProvider();
    localObjects = mock(LocalObjectService.class);
    api =
        new CounterpartApi(
            new CounterpartService(List.of(provider, new SilentProvider())),
            localObjects,
            mock(UserService.class),
            mock(PermissionService.class));
  }

  @Test
  @DisplayName("connections list every product, with its url when connected, never a credential")
  void connections() {
    List<XtmConnectionOutput> out = api.connections(null, "t");

    assertThat(out).hasSize(2);
    assertThat(out.get(0)).isEqualTo(new XtmConnectionOutput("opencti", true, "https://octi/"));
    assertThat(out.get(1)).isEqualTo(new XtmConnectionOutput("opencrq", true, "https://ocrq/"));

    provider.connected = false;
    assertThat(api.connections(null, "t").get(0))
        .isEqualTo(new XtmConnectionOutput("opencti", false, null));
  }

  @Test
  @DisplayName("every counterpart endpoint answers match, no match and not connected per key")
  void counterpartEndpoints() {
    CounterpartLookupInput input = new CounterpartLookupInput(List.of("T1059", "T9999"));

    List<CounterpartOutput> patterns = api.attackPatterns(null, "t", input);
    assertThat(patterns)
        .containsExactly(
            new CounterpartOutput(
                "T1059",
                "opencti",
                "match",
                "id-1",
                "Attack-Pattern",
                "Command",
                "https://octi/x",
                List.of()),
            new CounterpartOutput(
                "T9999", "opencti", "no_match", null, null, null, null, List.of()));

    assertThat(api.assets(null, "t", new CounterpartLookupInput(List.of("host-1"))))
        .extracting(CounterpartOutput::status)
        .containsExactly("no_match");
    assertThat(api.vulnerabilities(null, "t", new CounterpartLookupInput(List.of("cve-2024-1"))))
        .extracting(CounterpartOutput::key, CounterpartOutput::status)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("CVE-2024-1", "no_match"));
    assertThat(
            api.threats(
                null,
                "t",
                new CounterpartLookupInput(
                    List.of("intrusion-set--11111111-1111-4111-8111-111111111111"))))
        .hasSize(1);
    assertThat(
            api.alerts(
                null, "t", new CounterpartLookupInput(List.of("https://siem.example/alert/1"))))
        .hasSize(1);

    provider.connected = false;
    assertThat(api.attackPatterns(null, "t", input))
        .extracting(CounterpartOutput::status)
        .containsOnly("not_connected");
  }

  @Test
  @DisplayName("an unreachable product answers unavailable, never an error")
  void unavailable() {
    provider.broken = true;

    assertThat(
            api.attackPatterns(null, "t", new CounterpartLookupInput(List.of("T1059")))
                .getFirst()
                .status())
        .isEqualTo("unavailable");
  }

  @Test
  @DisplayName("keys are capped at the batch size before reaching the product")
  void capped() {
    List<String> keys =
        IntStream.range(0, CounterpartService.MAX_KEYS + 5)
            .mapToObj(i -> "T" + (1000 + i))
            .toList();

    List<CounterpartOutput> out = api.attackPatterns(null, "t", new CounterpartLookupInput(keys));

    assertThat(out).hasSize(CounterpartService.MAX_KEYS);
    assertThat(provider.calls.getFirst()).hasSize(CounterpartService.MAX_KEYS);
  }

  @Test
  @DisplayName("every reverse lookup endpoint maps the local objects with their key facts")
  void objectEndpoints() {
    LocalObject withFacts =
        new LocalObject(
            "host-1",
            "a1",
            "Endpoint",
            "Host 1",
            "https://aev.example/a1",
            List.of(new Fact("findings", "3")));
    LocalObject noFacts = new LocalObject("k", "i", "T", "N", "https://aev.example/i", null);
    when(localObjects.find(eq(CounterpartKind.ASSET), anyCollection(), any()))
        .thenReturn(List.of(withFacts, noFacts));
    CounterpartLookupInput input = new CounterpartLookupInput(List.of("host-1", "k"));

    List<LocalObjectOutput> assets = api.localAssets(null, "t", input);
    assertThat(assets)
        .containsExactly(
            new LocalObjectOutput(
                "host-1",
                "a1",
                "Endpoint",
                "Host 1",
                "https://aev.example/a1",
                List.of(new LocalObjectOutput.FactOutput("findings", "3"))),
            new LocalObjectOutput("k", "i", "T", "N", "https://aev.example/i", List.of()));

    assertThat(api.localAttackPatterns(null, "t", input)).isEmpty();
    assertThat(api.localVulnerabilities(null, "t", input)).isEmpty();
    assertThat(api.localThreats(null, "t", input)).isEmpty();
    assertThat(api.localAlerts(null, "t", input)).isEmpty();
    verify(localObjects).find(eq(CounterpartKind.ATTACK_PATTERN), eq(input.keys()), any());
    verify(localObjects).find(eq(CounterpartKind.VULNERABILITY), eq(input.keys()), any());
    verify(localObjects).find(eq(CounterpartKind.THREAT), eq(input.keys()), any());
    verify(localObjects).find(eq(CounterpartKind.ALERT), eq(input.keys()), any());
  }

  @Test
  @DisplayName("an unavailable product keeps its message")
  void exceptionMessage() {
    assertThat(new CounterpartUnavailableException("down")).hasMessage("down");
  }
}
