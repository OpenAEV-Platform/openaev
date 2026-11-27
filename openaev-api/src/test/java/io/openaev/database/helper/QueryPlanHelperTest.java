package io.openaev.database.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("QueryPlanHelper integration tests")
class QueryPlanHelperTest extends IntegrationTest {

  @Autowired private QueryPlanHelper queryPlanHelper;

  private String planCacheMode() {
    return (String)
        entityManager
            .createNativeQuery("SELECT current_setting('plan_cache_mode')")
            .getSingleResult();
  }

  @Test
  @DisplayName("forceCustomPlan forces a custom plan for the rest of the transaction")
  void given_activeTransaction_should_forceCustomPlan() {
    // Arrange
    assertThat(planCacheMode()).isEqualTo("auto");

    // Act
    queryPlanHelper.forceCustomPlan();

    // Assert
    assertThat(planCacheMode()).isEqualTo("force_custom_plan");
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("forceCustomPlan refuses to run outside a transaction")
  void given_noTransaction_should_refuse() {
    // Act & Assert
    assertThatThrownBy(() -> queryPlanHelper.forceCustomPlan())
        .isInstanceOf(IllegalStateException.class);
  }
}
