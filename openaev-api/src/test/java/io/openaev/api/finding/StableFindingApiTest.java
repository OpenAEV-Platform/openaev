package io.openaev.api.finding;

import static io.openaev.api.finding.StableFindingApi.STABLE_FINDING_URI;
import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.swagger.v3.oas.annotations.Operation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@DisplayName("Stable finding API contract")
class StableFindingApiTest {

  @Nested
  @DisplayName("Routes")
  class Routes {

    @Test
    @DisplayName("Exposes plain and tenant-prefixed base paths")
    void given_controller_should_exposeBothTenantRoutes() {
      // Arrange / Act
      RequestMapping mapping = StableFindingApi.class.getAnnotation(RequestMapping.class);

      // Assert
      assertThat(mapping.value())
          .containsExactlyInAnyOrder(STABLE_FINDING_URI, TENANT_PREFIX + "/stable-findings");
    }

    @Test
    @DisplayName("Exposes stable detail, summary, search, facet, occurrence, and location paths")
    void given_controller_should_exposeRequiredReadPaths() throws NoSuchMethodException {
      // Arrange / Act
      Method search =
          StableFindingApi.class.getMethod(
              "search",
              io.openaev.context.TxCtx.class,
              io.openaev.utils.pagination.SearchPaginationInput.class);
      Method occurrences =
          StableFindingApi.class.getMethod(
              "searchOccurrences",
              io.openaev.context.TxCtx.class,
              String.class,
              io.openaev.utils.pagination.SearchPaginationInput.class);
      Method facetCounts =
          StableFindingApi.class.getMethod(
              "facetCounts",
              io.openaev.context.TxCtx.class,
              io.openaev.utils.pagination.SearchPaginationInput.class);
      Method detail =
          StableFindingApi.class.getMethod(
              "findById", io.openaev.context.TxCtx.class, String.class);
      Method summary =
          StableFindingApi.class.getMethod("summary", io.openaev.context.TxCtx.class, String.class);
      Method locations =
          StableFindingApi.class.getMethod(
              "locations", io.openaev.context.TxCtx.class, String.class);

      // Assert
      assertThat(search.getAnnotation(PostMapping.class).value()).containsExactly("/search");
      assertThat(facetCounts.getAnnotation(PostMapping.class).value())
          .containsExactly("/facet-counts");
      assertThat(occurrences.getAnnotation(PostMapping.class).value())
          .containsExactly("/{id}/occurrences/search");
      assertThat(detail.getAnnotation(GetMapping.class).value()).containsExactly("/{id}");
      assertThat(summary.getAnnotation(GetMapping.class).value()).containsExactly("/{id}/summary");
      assertThat(locations.getAnnotation(GetMapping.class).value())
          .containsExactly("/{id}/locations");
    }
  }

  @Nested
  @DisplayName("Cross-cutting controls")
  class CrossCuttingControls {

    @Test
    @DisplayName("Protects and documents every endpoint")
    void given_endpoints_should_haveRequiredAnnotations() {
      // Arrange
      List<Method> endpoints =
          Arrays.stream(StableFindingApi.class.getDeclaredMethods())
              .filter(
                  method ->
                      method.isAnnotationPresent(GetMapping.class)
                          || method.isAnnotationPresent(PostMapping.class))
              .toList();

      // Act / Assert
      assertThat(endpoints).hasSize(6);
      assertThat(endpoints)
          .allSatisfy(
              endpoint -> {
                assertThat(endpoint.isAnnotationPresent(AccessControl.class)).isTrue();
                assertThat(endpoint.isAnnotationPresent(LogExecutionTime.class)).isTrue();
                assertThat(endpoint.isAnnotationPresent(Operation.class)).isTrue();
              });
    }
  }
}
