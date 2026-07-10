package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.TenantRepository;
import io.openaev.service.utils.ReadPropertiesHelper;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;

@ExtendWith(MockitoExtension.class)
class UserMappingServiceUnitTest {

  @Mock private GroupRepository groupRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private ReadPropertiesHelper readPropertiesHelper;

  @Test
  @DisplayName("When oidc user, extract roles accordingly")
  void whenOidcUser_extractRoles() {
    UserMappingService userMappingService =
        new UserMappingService(groupRepository, tenantRepository, readPropertiesHelper);
    when(readPropertiesHelper.getProviderPropertyAsList(
            "oidc", UserMappingService.ROLES_PATH_SUFFIX))
        .thenReturn(List.of("roles"));
    String role = "Administrator";
    OAuth2User user =
        new OAuth2User() {
          @Override
          public Map<String, Object> getAttributes() {
            return Map.of("roles", List.of(role));
          }

          @Override
          public Collection<? extends GrantedAuthority> getAuthorities() {
            return List.of();
          }

          @Override
          public String getName() {
            return "";
          }
        };

    List<String> roles = userMappingService.extractRolesFromUser(user, "oidc");

    assertThat(roles).isEqualTo(List.of(role));
  }

  @Test
  @DisplayName("When oidc user, extract groups accordingly")
  void whenOidcUser_extractGroups() {
    UserMappingService userMappingService =
        new UserMappingService(groupRepository, tenantRepository, readPropertiesHelper);
    when(readPropertiesHelper.getProviderPropertyAsList(
            "oidc", UserMappingService.GROUPS_PATH_SUFFIX))
        .thenReturn(List.of("groups"));
    String group = "Filigran";
    OAuth2User user =
        new OAuth2User() {
          @Override
          public Map<String, Object> getAttributes() {
            return Map.of("groups", List.of(group));
          }

          @Override
          public Collection<? extends GrantedAuthority> getAuthorities() {
            return List.of();
          }

          @Override
          public String getName() {
            return "";
          }
        };

    List<String> groups = userMappingService.extractGroupsFromUser(user, "oidc");

    assertThat(groups).isEqualTo(List.of(group));
  }

  @Test
  @DisplayName("When saml user, extract roles accordingly")
  void whenSamlUser_extractRoles() {
    UserMappingService userMappingService =
        new UserMappingService(groupRepository, tenantRepository, readPropertiesHelper);
    when(readPropertiesHelper.getProviderPropertyAsList(
            "saml", UserMappingService.ROLES_PATH_SUFFIX))
        .thenReturn(List.of("roles"));
    String role = "Administrator";
    Saml2AuthenticatedPrincipal user =
        new Saml2AuthenticatedPrincipal() {
          @Override
          public String getName() {
            return "";
          }

          @Override
          public Map<String, List<Object>> getAttributes() {
            return Map.of("roles", List.of(role));
          }
        };

    List<String> roles = userMappingService.extractRolesFromUser(user, "saml");

    assertThat(roles).isEqualTo(List.of(role));
  }

  @Test
  @DisplayName("When saml user, extract groups accordingly")
  void whenSamlUser_extractGroups() {
    UserMappingService userMappingService =
        new UserMappingService(groupRepository, tenantRepository, readPropertiesHelper);
    when(readPropertiesHelper.getProviderPropertyAsList(
            "saml", UserMappingService.GROUPS_PATH_SUFFIX))
        .thenReturn(List.of("groups"));
    String group = "Filigran";
    Saml2AuthenticatedPrincipal user =
        new Saml2AuthenticatedPrincipal() {
          @Override
          public String getName() {
            return "";
          }

          @Override
          public Map<String, List<Object>> getAttributes() {
            return Map.of("groups", List.of(group));
          }
        };

    List<String> groups = userMappingService.extractGroupsFromUser(user, "saml");

    assertThat(groups).isEqualTo(List.of(group));
  }

  @Test
  @DisplayName("When not implemented user, throw exception")
  void whenNotImplementedUser_throwException() {
    UserMappingService userMappingService =
        new UserMappingService(groupRepository, tenantRepository, readPropertiesHelper);
    when(readPropertiesHelper.getProviderPropertyAsList(
            "oidc", UserMappingService.GROUPS_PATH_SUFFIX))
        .thenReturn(List.of("groups"));
    AuthenticatedPrincipal user =
        new AuthenticatedPrincipal() {
          @Override
          public String getName() {
            return "";
          }
        };

    assertThrows(
        NotImplementedException.class,
        () -> userMappingService.extractGroupsFromUser(user, "oidc"));
  }
}
