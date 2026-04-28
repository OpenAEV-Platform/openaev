package io.openaev.telemetry.metric_collectors;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.ScopeRuleSelectedMode;
import io.openaev.database.model.ScopeRuleSource;
import io.openaev.database.model.ScopeRuleValueType;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScopeMetricCollector")
class ScopeMetricCollectorTest {

  @Mock private Meter meter;
  @Mock private LongCounterBuilder counterBuilder;
  @Mock private LongCounter scopeCreatedCounter;
  @Mock private LongCounter scopeEntryAddedCounter;

  @InjectMocks private ScopeMetricCollector scopeMetricCollector;

  @Captor private ArgumentCaptor<Attributes> attributesCaptor;

  @BeforeEach
  void setUp() {
    when(meter.counterBuilder(anyString())).thenReturn(counterBuilder);
    when(counterBuilder.setDescription(anyString())).thenReturn(counterBuilder);
    when(counterBuilder.setUnit(anyString())).thenReturn(counterBuilder);
    when(counterBuilder.build()).thenReturn(scopeCreatedCounter, scopeEntryAddedCounter);
    scopeMetricCollector.init();
  }

  @Test
  void given_whitelistMode_when_trackScopeCreated_should_publishModeAndEntryCountAttributes() {
    // Arrange
    long entryCount = 3L;

    // Act
    scopeMetricCollector.trackScopeCreated(ScopeRuleSelectedMode.WHITELIST, entryCount);

    // Assert
    verify(scopeCreatedCounter).add(eq(1L), attributesCaptor.capture());
    Attributes attributes = attributesCaptor.getValue();
    assertThat(attributes.get(stringKey("mode"))).isEqualTo("whitelist");
    assertThat(attributes.get(longKey("entry_count"))).isEqualTo(entryCount);
  }

  @Test
  void given_blacklistMode_when_trackScopeCreated_should_publishModeAndEntryCountAttributes() {
    // Arrange
    long entryCount = 2L;

    // Act
    scopeMetricCollector.trackScopeCreated(ScopeRuleSelectedMode.BLACKLIST, entryCount);

    // Assert
    verify(scopeCreatedCounter).add(eq(1L), attributesCaptor.capture());
    Attributes attributes = attributesCaptor.getValue();
    assertThat(attributes.get(stringKey("mode"))).isEqualTo("blacklist");
    assertThat(attributes.get(longKey("entry_count"))).isEqualTo(entryCount);
  }

  @ParameterizedTest(name = "type={0} should map to {1}")
  @CsvSource({
    "IP,ip",
    "IP_SUBNET,cidr",
    "DOMAIN,hostname",
    "ASSET_ID,asset",
    "ASSET_GROUP_ID,asset_group"
  })
  void given_scopeEntryType_when_trackScopeEntryAdded_should_publishMappedType(
      ScopeRuleValueType valueType, String expectedType) {
    // Arrange

    // Act
    scopeMetricCollector.trackScopeEntryAdded(valueType, ScopeRuleSource.MANUAL);

    // Assert
    verify(scopeEntryAddedCounter).add(eq(1L), attributesCaptor.capture());
    Attributes attributes = attributesCaptor.getValue();
    assertThat(attributes.get(stringKey("type"))).isEqualTo(expectedType);
    assertThat(attributes.get(stringKey("method"))).isEqualTo("manual");
  }

  @Test
  void given_csvSource_when_trackScopeEntryAdded_should_publishCsvMethod() {
    // Arrange

    // Act
    scopeMetricCollector.trackScopeEntryAdded(ScopeRuleValueType.IP, ScopeRuleSource.CSV);

    // Assert
    verify(scopeEntryAddedCounter).add(eq(1L), attributesCaptor.capture());
    Attributes attributes = attributesCaptor.getValue();
    assertThat(attributes.get(stringKey("method"))).isEqualTo("csv");
  }
}
