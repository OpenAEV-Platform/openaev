package io.openaev.rest.payload;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Command;
import io.openaev.database.model.Organization;
import io.openaev.database.model.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Payload author in JSON")
class PayloadAuthorJsonTest extends IntegrationTest {

  @Test
  @DisplayName("A payload with an author is read back from its JSON, as a chaining step does")
  void given_payloadWithAuthor_should_beReadBackFromJson() throws Exception {
    // Arrange
    User user = new User();
    user.setId("author-user");
    Organization organization = new Organization();
    organization.setId("author-organization");
    Command payload = new Command();
    payload.setAuthorUser(user);
    payload.setAuthorOrganization(organization);
    ObjectMapper mapper =
        new ObjectMapper()
            .findAndRegisterModules()
            .setInjectableValues(
                new InjectableValues.Std().addValue(EntityManager.class, entityManager));

    // Act
    Command read = mapper.readValue(mapper.writeValueAsString(payload), Command.class);

    // Assert
    assertThat(read.getAuthorUser().getId()).isEqualTo("author-user");
    assertThat(read.getAuthorOrganization().getId()).isEqualTo("author-organization");
  }
}
