package io.openaev.telemetry.metric_collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.ee.EnterpriseEditionService;
import io.openaev.ee.License;
import io.openaev.service.UserService;
import io.openaev.service.tenants.TenantService;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GlobalMetricCollectorTest {

  @Mock private MetricRegistry metricRegistry;
  @Mock private UserService userService;
  @Mock private EnterpriseEditionService enterpriseEditionService;
  @Mock private TenantService tenantService;

  @InjectMocks private GlobalMetricCollector globalMetricCollector;

  @Captor private ArgumentCaptor<Supplier<Long>> supplierCaptor;

  @Test
  void given_users_should_returnCorrectCount() {
    // Arrange
    when(userService.globalCount()).thenReturn(42L);

    // Act
    globalMetricCollector.init();

    // Assert
    verify(metricRegistry)
        .registerGauge(eq("total_users_count"), eq("Number of users"), supplierCaptor.capture());
    assertThat(supplierCaptor.getValue().get()).isEqualTo(42L);
  }

  @Test
  @SuppressWarnings("unchecked")
  void given_enterpriseEditionActive_should_returnOne() {
    // Arrange
    License license = new License();
    license.setLicenseValidated(true);
    when(enterpriseEditionService.getEnterpriseEditionInfo()).thenReturn(license);

    // Act
    globalMetricCollector.init();

    // Assert
    ArgumentCaptor<Supplier<Long>> eeCaptor = ArgumentCaptor.forClass(Supplier.class);
    verify(metricRegistry)
        .registerGauge(
            eq("is_enterprise_edition"),
            eq("enterprise Edition is activated"),
            eeCaptor.capture(),
            eq("boolean"));
    assertThat(eeCaptor.getValue().get()).isEqualTo(1L);
  }

  @Test
  void given_activeTenants_should_returnCorrectCount() {
    // Arrange
    when(tenantService.countActiveTenants()).thenReturn(5L);

    // Act
    globalMetricCollector.init();

    // Assert
    verify(metricRegistry)
        .registerGauge(
            eq("active_tenants_count"),
            eq("Number of active instance tenants"),
            supplierCaptor.capture());
    assertThat(supplierCaptor.getValue().get()).isEqualTo(5L);
  }
}
