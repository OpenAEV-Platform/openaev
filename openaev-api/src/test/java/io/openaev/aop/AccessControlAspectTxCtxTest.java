package io.openaev.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.context.AmbientTenantBridge;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.service.PermissionService;
import io.openaev.service.UserService;
import java.lang.reflect.Method;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The aspect hands the handler's own {@link TxCtx} to {@code PermissionService#checkedResource},
 * which loads an inject's parent in the request's scope with it. Every integration test that sends
 * an inject of another tenant also meets the handler's own scope guard, which refuses the same row,
 * so only a test of the aspect alone fails when this hand-over breaks.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccessControlAspect hands the handler's TxCtx to the permission check")
class AccessControlAspectTxCtxTest {

  private static final String INJECT_ID = "inject-1";

  @Mock private PermissionService permissionService;
  @Mock private UserService userService;
  @Mock private EnterpriseEditionService enterpriseEditionService;
  @Mock private LicenseCacheManager licenseCacheManager;
  @Mock private AmbientTenantBridge ambientTenantBridge;

  private static class Handlers {

    @AccessControl(
        resourceId = "#injectId",
        actionPerformed = Action.READ,
        resourceType = ResourceType.INJECT)
    public void withTxCtx(TxCtx ctx, String injectId) {}

    @AccessControl(
        resourceId = "#injectId",
        actionPerformed = Action.READ,
        resourceType = ResourceType.INJECT)
    public void withoutTxCtx(String injectId) {}
  }

  /** Runs the advice on {@code handler}, a method of {@link Handlers}, called with {@code args}. */
  private void adviseHandlerCall(String handler, String[] parameterNames, Object... args)
      throws Exception {
    Method method =
        java.util.Arrays.stream(Handlers.class.getDeclaredMethods())
            .filter(candidate -> candidate.getName().equals(handler))
            .findFirst()
            .orElseThrow();
    MethodSignature signature = mock(MethodSignature.class);
    when(signature.getParameterNames()).thenReturn(parameterNames);
    when(signature.getMethod()).thenReturn(method);
    JoinPoint joinPoint = mock(JoinPoint.class);
    when(joinPoint.getSignature()).thenReturn(signature);
    when(joinPoint.getArgs()).thenReturn(args);
    when(userService.currentUser()).thenReturn(mock(User.class));
    when(permissionService.checkedResource(any(), any(), any(), any(), any()))
        .thenReturn(mock(PermissionService.CheckedResource.class));
    when(permissionService.hasPermission(any(), any(), any())).thenReturn(true);

    new AccessControlAspect(
            permissionService,
            userService,
            enterpriseEditionService,
            licenseCacheManager,
            ambientTenantBridge)
        .methodRBACVerification(joinPoint, method.getAnnotation(AccessControl.class));
  }

  @Nested
  @DisplayName("The TxCtx of the permission check")
  class PermissionCheckTxCtx {

    @Test
    @DisplayName("given a handler declaring a TxCtx, should pass that very TxCtx")
    void given_handlerDeclaringTxCtx_should_passThatTxCtx() throws Exception {
      // Arrange
      TxCtx ctx = TxCtx.forTenant(TenantContext.getCurrentTenant());

      // Act
      adviseHandlerCall("withTxCtx", new String[] {"ctx", "injectId"}, ctx, INJECT_ID);

      // Assert
      ArgumentCaptor<TxCtx> passed = ArgumentCaptor.forClass(TxCtx.class);
      verify(permissionService)
          .checkedResource(
              passed.capture(),
              eq(TenantContext.getCurrentTenant()),
              eq(INJECT_ID),
              eq(ResourceType.INJECT),
              eq(Action.READ));
      assertThat(passed.getValue()).isSameAs(ctx);
    }

    @Test
    @DisplayName("given a handler declaring no TxCtx, should pass a missing one")
    void given_handlerDeclaringNoTxCtx_should_passMissingTxCtx() throws Exception {
      // Act
      adviseHandlerCall("withoutTxCtx", new String[] {"injectId"}, INJECT_ID);

      // Assert
      ArgumentCaptor<TxCtx> passed = ArgumentCaptor.forClass(TxCtx.class);
      verify(permissionService)
          .checkedResource(
              passed.capture(), any(), eq(INJECT_ID), eq(ResourceType.INJECT), eq(Action.READ));
      assertThat(passed.getValue()).isEqualTo(TxCtx.missing());
    }
  }
}
