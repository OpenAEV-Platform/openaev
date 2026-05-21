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
import io.openaev.service.RoleService;
import io.openaev.service.TenantGroupService;
import io.openaev.service.UserService;
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

  @Mock private RoleService roleService;
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
    mockRole.setName(SERVICE_ROLE_NAME);

    mockGroup = new Group();
    mockGroup.setName(SERVICE_GROUP_NAME);

    mockUser = new User();
    mockUser.setEmail(SERVICE_EMAIL);
  }

  @Test
  @DisplayName("Should create new user when no existing user found")
  void shouldCreateNewUserWhenNoExistingUserFound() {
    // prepare
    when(roleService.findAll(TENANT_ID)).thenReturn(List.of(mockRole));
    when(tenantGroupService.findAllByTenantId(TENANT_ID)).thenReturn(List.of(mockGroup));
    when(tenantGroupService.updateGroupInfoWithRoles(any(), any(), any())).thenReturn(mockGroup);
    when(userService.findByEmailIgnoreCase(SERVICE_EMAIL)).thenReturn(Optional.empty());
    when(userService.createInternalUser(anyString(), anyString(), any(), anyBoolean(), anyString()))
        .thenReturn(mockUser);

    // act
    privilegeService.ensurePrivilegedUserExists(TENANT_ID);

    // assert
    verify(userService)
        .createInternalUser(eq(SERVICE_EMAIL), eq("discrete"), isNull(), eq(false), anyString());
    verify(tenantUserService).attachToTenant(any(), eq(TENANT_ID));
    verify(userService).saveUser(mockUser);
  }

  @Test
  @DisplayName("Should reuse existing user when user exists but has no token")
  void shouldReuseExistingUserWhenUserExistsButHasNoToken() {
    // prepare
    mockUser.setTokens(null);

    when(roleService.findAll(TENANT_ID)).thenReturn(List.of(mockRole));
    when(tenantGroupService.findAllByTenantId(TENANT_ID)).thenReturn(List.of(mockGroup));
    when(tenantGroupService.updateGroupInfoWithRoles(any(), any(), any())).thenReturn(mockGroup);
    when(userService.findByEmailIgnoreCase(SERVICE_EMAIL)).thenReturn(Optional.of(mockUser));
    when(userService.createUserToken(any(), anyString())).thenReturn(mock());

    // act
    privilegeService.ensurePrivilegedUserExists(TENANT_ID);

    // assert
    verify(userService, never()).createInternalUser(any(), any(), any(), anyBoolean(), any());
    verify(userService).createUserToken(eq(mockUser), anyString());
    verify(tenantUserService).attachToTenant(any(), eq(TENANT_ID));
    verify(userService).saveUser(mockUser);
    assertThat(mockUser.getTokens()).hasSize(1);
  }

  @Test
  @DisplayName("Should do nothing when existing user already has a token")
  void shouldDoNothingWhenExistingUserAlreadyHasToken() {
    // prepare
    mockUser.setTokens(new ArrayList<>(List.of(new Token())));

    when(roleService.findAll(TENANT_ID)).thenReturn(List.of(mockRole));
    when(tenantGroupService.findAllByTenantId(TENANT_ID)).thenReturn(List.of(mockGroup));
    when(tenantGroupService.updateGroupInfoWithRoles(any(), any(), any())).thenReturn(mockGroup);
    when(userService.findByEmailIgnoreCase(SERVICE_EMAIL)).thenReturn(Optional.of(mockUser));
    when(userService.userHasToken(any())).thenReturn(true);

    // act
    privilegeService.ensurePrivilegedUserExists(TENANT_ID);

    // assert
    verify(userService, never()).createInternalUser(any(), any(), any(), anyBoolean(), any());
    verify(userService, never()).createUserToken(any(), any());
    verify(userService, never()).saveUser(any());
  }

  @Test
  @DisplayName("Should create new group when no existing group found")
  void shouldCreateNewGroupWhenNoExistingGroupFound() {
    // prepare
    when(roleService.findAll(TENANT_ID)).thenReturn(List.of(mockRole));
    when(tenantGroupService.findAllByTenantId(TENANT_ID)).thenReturn(List.of());
    when(tenantGroupService.createGroupWithRole(any(), any(), any())).thenReturn(mockGroup);
    when(userService.findByEmailIgnoreCase(SERVICE_EMAIL)).thenReturn(Optional.empty());
    when(userService.createInternalUser(any(), any(), any(), anyBoolean(), any()))
        .thenReturn(mockUser);

    // act
    privilegeService.ensurePrivilegedUserExists(TENANT_ID);

    // assert
    verify(tenantGroupService).createGroupWithRole(isNull(), any(), any());
    verify(tenantGroupService, never()).updateGroupInfoWithRoles(any(), any(), any());
  }

  @Test
  @DisplayName("Should update existing group when one matching group found")
  void shouldUpdateExistingGroupWhenOneMatchingGroupFound() {
    // prepare
    when(roleService.findAll(TENANT_ID)).thenReturn(List.of(mockRole));
    when(tenantGroupService.findAllByTenantId(TENANT_ID)).thenReturn(List.of(mockGroup));
    when(tenantGroupService.updateGroupInfoWithRoles(any(), any(), any())).thenReturn(mockGroup);
    when(userService.findByEmailIgnoreCase(SERVICE_EMAIL)).thenReturn(Optional.empty());
    when(userService.createInternalUser(any(), any(), any(), anyBoolean(), any()))
        .thenReturn(mockUser);

    // act
    privilegeService.ensurePrivilegedUserExists(TENANT_ID);

    // assert
    verify(tenantGroupService).updateGroupInfoWithRoles(eq(mockGroup), any(), any());
    verify(tenantGroupService, never()).createGroupWithRole(any(), any(), any());
  }

  @Test
  @DisplayName("Should throw when multiple groups with same name found")
  void shouldThrowWhenMultipleGroupsWithSameNameFound() {
    // prepare
    Group duplicateGroup = new Group();
    duplicateGroup.setName(SERVICE_GROUP_NAME);

    when(roleService.findAll(TENANT_ID)).thenReturn(List.of(mockRole));
    when(tenantGroupService.findAllByTenantId(TENANT_ID))
        .thenReturn(List.of(mockGroup, duplicateGroup));

    // act & assert
    assertThatThrownBy(() -> privilegeService.ensurePrivilegedUserExists(TENANT_ID))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("Invalid group");
  }

  // endregion

  // region createWellKnownRole

  @Test
  @DisplayName("Should create new role when no existing role found")
  void shouldCreateNewRoleWhenNoExistingRoleFound() {
    // prepare
    when(roleService.findAll(TENANT_ID)).thenReturn(List.of());
    when(roleService.createRole(any(), any(), any(), any())).thenReturn(mockRole);
    when(tenantGroupService.findAllByTenantId(TENANT_ID)).thenReturn(List.of(mockGroup));
    when(tenantGroupService.updateGroupInfoWithRoles(any(), any(), any())).thenReturn(mockGroup);
    when(userService.findByEmailIgnoreCase(SERVICE_EMAIL)).thenReturn(Optional.empty());
    when(userService.createInternalUser(any(), any(), any(), anyBoolean(), any()))
        .thenReturn(mockUser);

    // act
    privilegeService.ensurePrivilegedUserExists(TENANT_ID);

    // assert
    verify(roleService)
        .createRole(
            isNull(),
            eq(SERVICE_ROLE_NAME),
            eq(SERVICE_ROLE_DESCRIPTION),
            eq(SERVICE_ROLE_CAPABILITIES));
  }

  @Test
  @DisplayName("Should reuse existing role when matching role found")
  void shouldReuseExistingRoleWhenMatchingRoleFound() {
    // prepare
    when(roleService.findAll(TENANT_ID)).thenReturn(List.of(mockRole));
    when(tenantGroupService.findAllByTenantId(TENANT_ID)).thenReturn(List.of(mockGroup));
    when(tenantGroupService.updateGroupInfoWithRoles(any(), any(), any())).thenReturn(mockGroup);
    when(userService.findByEmailIgnoreCase(SERVICE_EMAIL)).thenReturn(Optional.empty());
    when(userService.createInternalUser(any(), any(), any(), anyBoolean(), any()))
        .thenReturn(mockUser);

    // act
    privilegeService.ensurePrivilegedUserExists(TENANT_ID);

    // assert
    verify(roleService, never()).createRole(any(), any(), any(), any());
  }

  @Test
  @DisplayName("Should return user when service account exists for tenant")
  void shouldReturnUserWhenServiceAccountExistsForTenant() {
    // prepare
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
}
