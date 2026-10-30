package io.openaev.service.account;

import static io.openaev.service.account.ReservedServiceAccount.CATALOG_CONNECTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.openaev.api.groups.dto.TenantGroupCreateInput;
import io.openaev.config.SessionManager;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.model.Token;
import io.openaev.database.model.User;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.*;
import io.openaev.service.tenants.TenantUserService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Catalog connector service account")
class CatalogConnectorPrivilegeServiceTest {

  private static final String TENANT_ID = "tenant-123";
  private static final String ACCOUNT_EMAIL = CATALOG_CONNECTOR.emailPattern().formatted(TENANT_ID);
  private static final String ROLE_ID =
      AbstractPrivilegeService.getUUIDFromName(CATALOG_CONNECTOR.access().roleId(), TENANT_ID);
  private static final String GROUP_ID =
      AbstractPrivilegeService.getUUIDFromName(CATALOG_CONNECTOR.access().groupId(), TENANT_ID);

  @Mock private TenantRoleService tenantRoleService;
  @Mock private TenantGroupService tenantGroupService;
  @Mock private UserService userService;
  @Mock private TenantUserService tenantUserService;
  @Mock private SessionManager sessionManager;

  @InjectMocks private CatalogConnectorPrivilegeService privilegeService;

  private Role role;
  private Group group;
  private User account;

  @BeforeEach
  void setUp() {
    role = new Role();
    role.setId(ROLE_ID);
    group = new Group();
    group.setId(GROUP_ID);
    account = new User();
    account.setEmail(ACCOUNT_EMAIL);
  }

  private void givenExistingRoleAndGroup() {
    when(tenantRoleService.findByIdAndTenant(ROLE_ID, TENANT_ID)).thenReturn(Optional.of(role));
    when(tenantRoleService.updateRoleInternal(
            anyString(), anyString(), anyString(), anySet(), anyString()))
        .thenReturn(role);
    when(tenantGroupService.findByIdAndTenant(GROUP_ID, TENANT_ID)).thenReturn(Optional.of(group));
    when(tenantGroupService.updateInternalGroupWithRoles(any(), any(), any())).thenReturn(group);
  }

  private static Token token(String value) {
    Token token = new Token();
    token.setValue(value);
    return token;
  }

  @Nested
  @DisplayName("Token of the tenant's account")
  class GetOrCreateToken {

    @Test
    @DisplayName("Creates the account on the tenant's first deployment and returns its token")
    void given_noAccount_should_createItAndReturnItsToken() {
      // Arrange
      givenExistingRoleAndGroup();
      when(userService.findByEmailIgnoreCase(ACCOUNT_EMAIL)).thenReturn(Optional.empty());
      when(userService.createInternalUser(
              anyString(), anyString(), any(), anyBoolean(), anyString()))
          .thenReturn(account);
      ArgumentCaptor<String> createdToken = ArgumentCaptor.forClass(String.class);

      // Act
      String result = privilegeService.getOrCreateToken(TENANT_ID);

      // Assert
      verify(userService)
          .createInternalUser(
              eq(ACCOUNT_EMAIL),
              eq("Catalog connectors"),
              isNull(),
              eq(false),
              createdToken.capture());
      assertThat(result).isEqualTo(createdToken.getValue());
      assertThat(account.getUnscopedGroups()).containsExactly(group);
      verify(tenantUserService).attachToTenant(any(), eq(TENANT_ID));
      verify(userService).saveUser(account);
    }

    @Test
    @DisplayName("Takes the creation lock before looking the account up")
    void given_concurrentFirstDeployments_should_lockBeforeLookingTheAccountUp() {
      // Arrange
      givenExistingRoleAndGroup();
      account.setTokens(new ArrayList<>(List.of(token("existing-token"))));
      when(userService.findByEmailIgnoreCase(ACCOUNT_EMAIL)).thenReturn(Optional.of(account));

      // Act
      privilegeService.getOrCreateToken(TENANT_ID);

      // Assert
      InOrder inOrder = inOrder(userService, tenantRoleService);
      inOrder.verify(userService).lockAccountCreation(ACCOUNT_EMAIL);
      inOrder.verify(tenantRoleService).findByIdAndTenant(ROLE_ID, TENANT_ID);
      inOrder.verify(userService).findByEmailIgnoreCase(ACCOUNT_EMAIL);
    }

    @Test
    @DisplayName("Returns the token of an existing account without changing it")
    void given_accountWithToken_should_returnItsToken() {
      // Arrange
      givenExistingRoleAndGroup();
      account.setTokens(new ArrayList<>(List.of(token("existing-token"))));
      when(userService.findByEmailIgnoreCase(ACCOUNT_EMAIL)).thenReturn(Optional.of(account));

      // Act
      String result = privilegeService.getOrCreateToken(TENANT_ID);

      // Assert
      assertThat(result).isEqualTo("existing-token");
      verify(userService, never()).createInternalUser(any(), any(), any(), anyBoolean(), any());
      verify(userService, never()).createUserToken(any(), any());
      verify(userService, never()).saveUser(any());
    }

    @Test
    @DisplayName("Issues and returns a token for an existing account that has none")
    void given_accountWithoutToken_should_issueAndReturnToken() {
      // Arrange
      givenExistingRoleAndGroup();
      when(userService.findByEmailIgnoreCase(ACCOUNT_EMAIL)).thenReturn(Optional.of(account));
      when(userService.createUserToken(eq(account), anyString())).thenReturn(token("new-token"));

      // Act
      String result = privilegeService.getOrCreateToken(TENANT_ID);

      // Assert
      assertThat(result).isEqualTo("new-token");
      verify(userService, never()).createInternalUser(any(), any(), any(), anyBoolean(), any());
      verify(tenantUserService).attachToTenant(any(), eq(TENANT_ID));
      verify(userService).saveUser(account);
    }

    @Test
    @DisplayName("Resyncs the role with the catalog connector access at every call")
    void given_existingRole_should_resyncItWithCatalogConnectorAccess() {
      // Arrange
      givenExistingRoleAndGroup();
      account.setTokens(new ArrayList<>(List.of(token("existing-token"))));
      when(userService.findByEmailIgnoreCase(ACCOUNT_EMAIL)).thenReturn(Optional.of(account));

      // Act
      privilegeService.getOrCreateToken(TENANT_ID);

      // Assert
      verify(tenantRoleService)
          .updateRoleInternal(
              ROLE_ID,
              CATALOG_CONNECTOR.access().roleName(),
              CATALOG_CONNECTOR.access().roleDescription(),
              CATALOG_CONNECTOR.access().capabilities(),
              TENANT_ID);
    }

    @Test
    @DisplayName("Creates the role and the group with the catalog connector access")
    void given_noRoleAndNoGroup_should_createThemWithCatalogConnectorAccess() {
      // Arrange
      when(tenantRoleService.findByIdAndTenant(ROLE_ID, TENANT_ID)).thenReturn(Optional.empty());
      when(tenantRoleService.createRoleInternal(any(), any(), any(), any(), any()))
          .thenReturn(role);
      when(tenantGroupService.findByIdAndTenant(GROUP_ID, TENANT_ID)).thenReturn(Optional.empty());
      when(tenantGroupService.createInternalGroupWithRole(any(), any(), any(), any()))
          .thenReturn(group);
      when(userService.findByEmailIgnoreCase(ACCOUNT_EMAIL)).thenReturn(Optional.empty());
      when(userService.createInternalUser(any(), any(), any(), anyBoolean(), any()))
          .thenReturn(account);
      ArgumentCaptor<TenantGroupCreateInput> groupInput =
          ArgumentCaptor.forClass(TenantGroupCreateInput.class);

      // Act
      privilegeService.getOrCreateToken(TENANT_ID);

      // Assert
      verify(tenantRoleService)
          .createRoleInternal(
              ROLE_ID,
              CATALOG_CONNECTOR.access().roleName(),
              CATALOG_CONNECTOR.access().roleDescription(),
              CATALOG_CONNECTOR.access().capabilities(),
              TENANT_ID);
      verify(tenantGroupService)
          .createInternalGroupWithRole(
              eq(GROUP_ID), groupInput.capture(), eq(List.of(role)), eq(TENANT_ID));
      assertThat(groupInput.getValue().getName()).isEqualTo(CATALOG_CONNECTOR.access().groupName());
      assertThat(groupInput.getValue().getDescription())
          .isEqualTo(CATALOG_CONNECTOR.access().groupDescription());
    }
  }

  @Nested
  @DisplayName("Token rotation")
  class RotateTokenForTenant {

    @Test
    @DisplayName(
        "Deletes the existing token, issues a new one, invalidates the sessions and returns it")
    void given_accountWithToken_should_replaceTokenAndInvalidateSessions() {
      // Arrange
      account.setId("catalog-connector-account-id");
      Token oldToken = token("old-token");
      account.setTokens(new ArrayList<>(List.of(oldToken)));
      when(userService.findByEmailIgnoreCase(ACCOUNT_EMAIL)).thenReturn(Optional.of(account));
      when(userService.createUserToken(eq(account), anyString())).thenReturn(token("new-token"));

      // Act
      String result = privilegeService.rotateTokenForTenant(TENANT_ID);

      // Assert
      assertThat(result).isEqualTo("new-token");
      verify(userService).deleteUserToken(oldToken);
      verify(sessionManager).invalidateUserSession("catalog-connector-account-id");
    }

    @Test
    @DisplayName("Fails when the tenant has no catalog connector account yet")
    void given_noAccount_should_throwNotFound() {
      // Arrange
      when(userService.findByEmailIgnoreCase(ACCOUNT_EMAIL)).thenReturn(Optional.empty());

      // Act & Assert
      assertThatThrownBy(() -> privilegeService.rotateTokenForTenant(TENANT_ID))
          .isInstanceOf(ElementNotFoundException.class);
      verify(userService, never()).createUserToken(any(), any());
    }
  }

  @Nested
  @DisplayName("Reservation")
  class Reservation {

    @BeforeEach
    void setTenant() {
      TenantContext.setCurrentTenant(TENANT_ID);
    }

    @AfterEach
    void clearTenant() {
      TenantContext.clearCurrentTenant();
    }

    @Test
    @DisplayName("Reserves the account email")
    void given_catalogConnectorEmail_should_beReserved() {
      // Act & Assert
      assertThat(ReservedKeyValidator.isReservedUserEmail(ACCOUNT_EMAIL)).isTrue();
    }

    @Test
    @DisplayName("Reserves the account role")
    void given_catalogConnectorRoleId_should_beRejected() {
      // Act & Assert
      assertThatThrownBy(() -> ReservedKeyValidator.validateRoleId(ROLE_ID))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Reserves the account group")
    void given_catalogConnectorGroupId_should_beRejected() {
      // Act & Assert
      assertThatThrownBy(() -> ReservedKeyValidator.validateGroupId(GROUP_ID))
          .isInstanceOf(BadRequestException.class);
    }
  }
}
