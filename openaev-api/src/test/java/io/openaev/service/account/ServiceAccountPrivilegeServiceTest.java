package io.openaev.service.account;

import static io.openaev.service.account.Constants.*;
import static io.openaev.service.account.ServiceAccountPrivilegeService.SERVICE_EMAIL_PATTERN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.model.Token;
import io.openaev.database.model.User;
import io.openaev.service.*;
import io.openaev.service.tenants.TenantUserService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Service Account Privilege Service tests")
public class ServiceAccountPrivilegeServiceTest {

  @Mock private TenantRoleService tenantRoleService;
  @Mock private TenantGroupService tenantGroupService;
  @Mock private UserService userService;
  @Mock private TenantUserService tenantUserService;

  @InjectMocks private ServiceAccountPrivilegeService privilegeService;

  private static final String TENANT_ID = "tenant-123";
  private static final String SERVICE_EMAIL = SERVICE_EMAIL_PATTERN.formatted(TENANT_ID);

  private Role mockRole;
  private Group mockGroup;
  private User mockUser;

  @BeforeEach
  void setUp() {

    mockRole = new Role();
    mockRole.setId(AbstractPrivilegeService.getUUIDFromName(SERVICE_ROLE_ID, TENANT_ID));
    mockRole.setName(SERVICE_ROLE_NAME);

    mockGroup = new Group();
    mockGroup.setId(AbstractPrivilegeService.getUUIDFromName(SERVICE_GROUP_ID, TENANT_ID));
    mockGroup.setName(SERVICE_GROUP_NAME);

    mockUser = new User();
    mockUser.setEmail(SERVICE_EMAIL);
  }

  // region ensurePrivilegedUserExists — user lifecycle

  @Test
  @DisplayName("Should create new user when no existing user found")
  void shouldCreateNewUserWhenNoExistingUserFound() {
    // prepare
    when(tenantRoleService.findByIdAndTenant(anyString(), anyString()))
        .thenReturn(Optional.of(mockRole));
    when(tenantRoleService.updateRoleInternal(
            anyString(), anyString(), anyString(), anySet(), anyString()))
        .thenReturn(mockRole);
    when(tenantGroupService.findByIdAndTenant(anyString(), anyString()))
        .thenReturn(Optional.of(mockGroup));
    when(tenantGroupService.updateInternalGroupWithRoles(any(), any(), any()))
        .thenReturn(mockGroup);
    when(userService.findByEmailIgnoreCase(SERVICE_EMAIL)).thenReturn(Optional.empty());
    when(userService.createInternalUser(anyString(), anyString(), any(), anyBoolean(), anyString()))
        .thenReturn(mockUser);

    // act
    privilegeService.ensurePrivilegedUserExists(TENANT_ID);

    // assert
    verify(userService)
        .createInternalUser(eq(SERVICE_EMAIL), eq("service"), isNull(), eq(false), anyString());
    verify(tenantUserService).attachToTenant(any(), eq(TENANT_ID));
    verify(userService).saveUser(mockUser);
  }

  @Test
  @DisplayName("Should reuse existing user when user exists but has no token")
  void shouldReuseExistingUserWhenUserExistsButHasNoToken() {
    // prepare
    mockUser.setTokens(null);

    when(tenantRoleService.findByIdAndTenant(anyString(), anyString()))
        .thenReturn(Optional.of(mockRole));
    when(tenantRoleService.updateRoleInternal(
            anyString(), anyString(), anyString(), anySet(), anyString()))
        .thenReturn(mockRole);
    when(tenantGroupService.findByIdAndTenant(anyString(), anyString()))
        .thenReturn(Optional.of(mockGroup));
    when(tenantGroupService.updateInternalGroupWithRoles(any(), any(), any()))
        .thenReturn(mockGroup);
    when(userService.findByEmailIgnoreCase(SERVICE_EMAIL)).thenReturn(Optional.of(mockUser));
    when(userService.userHasToken(any())).thenReturn(false);

    // act
    privilegeService.ensurePrivilegedUserExists(TENANT_ID);

    // assert
    verify(userService, never()).createInternalUser(any(), any(), any(), anyBoolean(), any());
    verify(userService).createUserToken(eq(mockUser));
    verify(tenantUserService).attachToTenant(any(), eq(TENANT_ID));
    verify(userService).saveUser(mockUser);
  }

  @Test
  @DisplayName("Should do nothing when existing user already has a token")
  void shouldDoNothingWhenExistingUserAlreadyHasToken() {
    // prepare
    mockUser.setTokens(new ArrayList<>(List.of(new Token())));

    when(tenantRoleService.findByIdAndTenant(anyString(), anyString()))
        .thenReturn(Optional.of(mockRole));
    when(tenantRoleService.updateRoleInternal(
            anyString(), anyString(), anyString(), anySet(), anyString()))
        .thenReturn(mockRole);
    when(tenantGroupService.findByIdAndTenant(anyString(), anyString()))
        .thenReturn(Optional.of(mockGroup));
    when(tenantGroupService.updateInternalGroupWithRoles(any(), any(), any()))
        .thenReturn(mockGroup);
    when(userService.findByEmailIgnoreCase(SERVICE_EMAIL)).thenReturn(Optional.of(mockUser));
    when(userService.userHasToken(any())).thenReturn(true);

    // act
    privilegeService.ensurePrivilegedUserExists(TENANT_ID);

    // assert
    verify(userService, never()).createInternalUser(any(), any(), any(), anyBoolean(), any());
    verify(userService, never()).createUserToken(any());
    verify(userService, never()).saveUser(any());
    verify(tenantUserService, never()).attachToTenant(any(), any());
  }

  // endregion

  // region createWellKnownGroupWithRole

  @Test
  @DisplayName("Should create new group when no existing group found")
  void shouldCreateNewGroupWhenNoExistingGroupFound() {
    // prepare
    when(tenantRoleService.findByIdAndTenant(anyString(), anyString()))
        .thenReturn(Optional.of(mockRole));
    when(tenantRoleService.updateRoleInternal(
            anyString(), anyString(), anyString(), anySet(), anyString()))
        .thenReturn(mockRole);
    when(tenantGroupService.findByIdAndTenant(anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(tenantGroupService.createInternalGroupWithRole(any(), any(), any(), any()))
        .thenReturn(mockGroup);
    when(userService.findByEmailIgnoreCase(SERVICE_EMAIL)).thenReturn(Optional.empty());
    when(userService.createInternalUser(any(), any(), any(), anyBoolean(), any()))
        .thenReturn(mockUser);

    // act
    privilegeService.ensurePrivilegedUserExists(TENANT_ID);

    // assert
    verify(tenantGroupService)
        .createInternalGroupWithRole(anyString(), any(), any(), eq(TENANT_ID));
    verify(tenantGroupService, never()).updateInternalGroupWithRoles(any(), any(), any());
  }

  @Test
  @DisplayName("Should update existing group when one matching group found")
  void shouldUpdateExistingGroupWhenOneMatchingGroupFound() {
    // prepare
    when(tenantRoleService.findByIdAndTenant(anyString(), anyString()))
        .thenReturn(Optional.of(mockRole));
    when(tenantRoleService.updateRoleInternal(
            anyString(), anyString(), anyString(), anySet(), anyString()))
        .thenReturn(mockRole);
    when(tenantGroupService.findByIdAndTenant(anyString(), anyString()))
        .thenReturn(Optional.of(mockGroup));
    when(tenantGroupService.updateInternalGroupWithRoles(any(), any(), any()))
        .thenReturn(mockGroup);
    when(userService.findByEmailIgnoreCase(SERVICE_EMAIL)).thenReturn(Optional.empty());
    when(userService.createInternalUser(any(), any(), any(), anyBoolean(), any()))
        .thenReturn(mockUser);

    // act
    privilegeService.ensurePrivilegedUserExists(TENANT_ID);

    // assert
    verify(tenantGroupService).updateInternalGroupWithRoles(eq(mockGroup), any(), any());
    verify(tenantGroupService, never()).createInternalGroupWithRole(any(), any(), any(), any());
  }

  // endregion

  // region createWellKnownRole

  @Test
  @DisplayName("Should create new role when no existing role found")
  void shouldCreateNewRoleWhenNoExistingRoleFound() {
    // prepare
    when(tenantRoleService.findByIdAndTenant(anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(tenantRoleService.createRoleInternal(any(), any(), any(), any(), any()))
        .thenReturn(mockRole);
    when(tenantGroupService.findByIdAndTenant(anyString(), anyString()))
        .thenReturn(Optional.of(mockGroup));
    when(tenantGroupService.updateInternalGroupWithRoles(any(), any(), any()))
        .thenReturn(mockGroup);
    when(userService.findByEmailIgnoreCase(SERVICE_EMAIL)).thenReturn(Optional.empty());
    when(userService.createInternalUser(any(), any(), any(), anyBoolean(), any()))
        .thenReturn(mockUser);

    // act
    privilegeService.ensurePrivilegedUserExists(TENANT_ID);

    // assert
    verify(tenantRoleService)
        .createRoleInternal(
            anyString(),
            eq(SERVICE_ROLE_NAME),
            eq(SERVICE_ROLE_DESCRIPTION),
            eq(SERVICE_ROLE_CAPABILITIES),
            eq(TENANT_ID));
    verify(tenantRoleService, never()).updateRoleInternal(any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("Should reuse existing role when matching role found")
  void shouldReuseExistingRoleWhenMatchingRoleFound() {
    // prepare
    when(tenantRoleService.findByIdAndTenant(anyString(), anyString()))
        .thenReturn(Optional.of(mockRole));
    when(tenantRoleService.updateRoleInternal(
            anyString(), anyString(), anyString(), anySet(), anyString()))
        .thenReturn(mockRole);
    when(tenantGroupService.findByIdAndTenant(anyString(), anyString()))
        .thenReturn(Optional.of(mockGroup));
    when(tenantGroupService.updateInternalGroupWithRoles(any(), any(), any()))
        .thenReturn(mockGroup);
    when(userService.findByEmailIgnoreCase(SERVICE_EMAIL)).thenReturn(Optional.empty());
    when(userService.createInternalUser(any(), any(), any(), anyBoolean(), any()))
        .thenReturn(mockUser);

    // act
    privilegeService.ensurePrivilegedUserExists(TENANT_ID);

    // assert
    verify(tenantRoleService, never()).createRoleInternal(any(), any(), any(), any(), any());
    verify(tenantRoleService)
        .updateRoleInternal(
            eq(mockRole.getId()),
            eq(SERVICE_ROLE_NAME),
            eq(SERVICE_ROLE_DESCRIPTION),
            eq(SERVICE_ROLE_CAPABILITIES),
            eq(TENANT_ID));
  }

  // endregion

  // region getUserServiceAccountByTenant

  @Test
  @DisplayName("Should return user when service account exists for tenant")
  void shouldReturnUserWhenServiceAccountExistsForTenant() {
    // prepare
    Token token = new Token();
    token.setValue("test-token-value");
    mockUser.setTokens(new ArrayList<>(List.of(token)));
    when(userService.findByEmailIgnoreCase(SERVICE_EMAIL)).thenReturn(Optional.of(mockUser));

    // act
    Optional<User> result = privilegeService.getUserServiceAccountByTenant(TENANT_ID);

    // assert
    assertThat(result).isPresent();
    assertThat(result.get().getEmail()).isEqualTo(SERVICE_EMAIL);
  }

  @Test
  @DisplayName("Should return empty when no service account exists for tenant")
  void shouldReturnEmptyWhenNoServiceAccountExistsForTenant() {
    // prepare
    when(userService.findByEmailIgnoreCase(SERVICE_EMAIL)).thenReturn(Optional.empty());

    // act
    Optional<User> result = privilegeService.getUserServiceAccountByTenant(TENANT_ID);

    // assert
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should return empty when service account user has no token")
  void shouldReturnEmptyWhenServiceAccountUserHasNoToken() {
    // prepare
    mockUser.setTokens(new ArrayList<>());
    when(userService.findByEmailIgnoreCase(SERVICE_EMAIL)).thenReturn(Optional.of(mockUser));

    // act
    Optional<User> result = privilegeService.getUserServiceAccountByTenant(TENANT_ID);

    // assert
    assertThat(result.get().getTokens()).isEmpty();
  }

  // region getTokenUserServiceAccountByTenant

  @Test
  @DisplayName("Should return token value when service account has a single token")
  void shouldReturnTokenValueWhenServiceAccountHasSingleToken() {
    // prepare
    Token token = new Token();
    token.setValue("test-token-value");
    mockUser.setTokens(new ArrayList<>(List.of(token)));
    when(userService.findByEmailIgnoreCase(SERVICE_EMAIL)).thenReturn(Optional.of(mockUser));

    // act
    String result = privilegeService.getTokenUserServiceAccountByTenant(TENANT_ID);

    // assert
    assertThat(result).isEqualTo("test-token-value");
  }

  @Test
  @DisplayName("Should throw UnsupportedOperationException when no service account exists")
  void shouldThrowWhenNoServiceAccountExistsForToken() {
    // prepare
    when(userService.findByEmailIgnoreCase(SERVICE_EMAIL)).thenReturn(Optional.empty());

    // act & assert
    assertThatThrownBy(() -> privilegeService.getTokenUserServiceAccountByTenant(TENANT_ID))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("Token not found");
  }

  @Test
  @DisplayName("Should throw UnsupportedOperationException when service account has no token")
  void shouldThrowWhenServiceAccountHasNoToken() {
    // prepare
    mockUser.setTokens(new ArrayList<>());
    when(userService.findByEmailIgnoreCase(SERVICE_EMAIL)).thenReturn(Optional.of(mockUser));

    // act & assert
    assertThatThrownBy(() -> privilegeService.getTokenUserServiceAccountByTenant(TENANT_ID))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("Token not found");
  }

  @Test
  @DisplayName(
      "Should throw UnsupportedOperationException when service account has multiple tokens")
  void shouldThrowWhenServiceAccountHasMultipleTokens() {
    // prepare
    mockUser.setTokens(new ArrayList<>(List.of(new Token(), new Token())));
    when(userService.findByEmailIgnoreCase(SERVICE_EMAIL)).thenReturn(Optional.of(mockUser));

    // act & assert
    assertThatThrownBy(() -> privilegeService.getTokenUserServiceAccountByTenant(TENANT_ID))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("Token not found");
  }

  // endregion
}
