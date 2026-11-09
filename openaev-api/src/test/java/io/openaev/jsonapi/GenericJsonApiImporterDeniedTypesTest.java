package io.openaev.jsonapi;

import static io.openaev.jsonapi.GenericJsonApiIUtils.resolveType;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openaev.database.model.Grant;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Token;
import io.openaev.database.model.User;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("The types no JSON:API import builds")
class GenericJsonApiImporterDeniedTypesTest {

  @Test
  @DisplayName("are exactly the identity and authorization entities")
  void given_deniedTypes_should_beTheIdentityAndAuthorizationEntities() {
    assertEquals(
        Set.of(
            resolveType(User.class),
            resolveType(Token.class),
            resolveType(Grant.class),
            resolveType(Group.class),
            resolveType(Role.class),
            resolveType(Tenant.class)),
        GenericJsonApiImporter.DENIED_TYPES);
  }
}
