package io.openaev.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.openaev.config.cache.TenantMembershipCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;
import org.hibernate.Session;
import org.hibernate.jdbc.Work;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class TenantInterceptorTest {

  private final TenantMembershipCacheManager tenantMembershipCacheManager =
      mock(TenantMembershipCacheManager.class);
  private final EntityManager entityManager = mock(EntityManager.class);
  private final TenantInterceptor interceptor;

  TenantInterceptorTest() throws Exception {
    Session session = mock(Session.class);
    when(entityManager.unwrap(Session.class)).thenReturn(session);
    Connection connection = mock(Connection.class);
    PreparedStatement stmt = mock(PreparedStatement.class);
    when(connection.prepareStatement(any())).thenReturn(stmt);
    doAnswer(
            invocation -> {
              Work work = invocation.getArgument(0);
              work.execute(connection);
              return null;
            })
        .when(session)
        .doWork(any());
    interceptor = new TenantInterceptor(tenantMembershipCacheManager, entityManager);
  }

  private final MockHttpServletRequest request = new MockHttpServletRequest();
  private final MockHttpServletResponse response = new MockHttpServletResponse();

  @AfterEach
  void tearDown() {
    TenantContext.clearCurrentTenant();
  }

  @Test
  void given_tenant_id_in_path_prehandle_should_set_context_and_after_completion_should_clear() {
    // -- ARRANGE --
    String tenantId = "abc-123";
    request.setAttribute(
        HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("tenantId", tenantId));

    // -- ACT --
    boolean result = interceptor.preHandle(request, response, new Object());

    // -- ASSERT --
    assertThat(result).isTrue();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(tenantId);

    // -- ACT --
    interceptor.afterCompletion(request, response, new Object(), null);

    // -- ASSERT --
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }

  @Test
  void given_no_tenant_id_in_path_prehandle_should_fallback_to_default_tenant() {
    // -- ACT -- no path variables at all
    boolean resultNoVars = interceptor.preHandle(request, response, new Object());

    // -- ASSERT --
    assertThat(resultNoVars).isTrue();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);

    // -- ACT -- path variables present but no tenantId key
    request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("otherId", "xyz"));
    boolean resultNoTenantKey = interceptor.preHandle(request, response, new Object());

    // -- ASSERT --
    assertThat(resultNoTenantKey).isTrue();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }
}
