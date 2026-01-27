package io.openaev.rest.user;

import static io.openaev.utils.fixtures.TagFixture.getTag;

import io.openaev.database.model.Organization;
import io.openaev.database.model.User;
import io.openaev.database.repository.OrganizationRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.user.form.player.PlayerInput;
import jakarta.persistence.EntityManager;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PlayerServiceTest {
  @Mock private TagRepository tagRepository;
  @Mock private TeamRepository teamRepository;
  @Mock private OrganizationRepository organizationRepository;
  @Mock private EntityManager entityManager;
  @Mock private UserRepository userRepository;
  @InjectMocks private PlayerService playerService;

  @Test
  public void test_upsertPlayer_noUpdateNeeded() {
    PlayerInput playerInput = new PlayerInput();
    User user = new User();
    user.setFirstname("newUser");
    user.setEmail("newUser@newUser.com");

    playerInput.setFirstname("newUser");
    playerInput.setEmail("newUser@newUser.com");

    Mockito.when(userRepository.findByEmailIgnoreCase("newUser@newUser.com"))
        .thenReturn(Optional.of(user));
    playerService.upsertPlayer(playerInput);
    Mockito.verify(userRepository, Mockito.never()).save(Mockito.any());
  }

  @Test
  public void test_upsertPlayer_firstnameDifferent_shouldUpdate() {
    PlayerInput playerInput = new PlayerInput();
    User user = new User();
    user.setFirstname("newUser");
    user.setEmail("newUser@newUser.com");

    playerInput.setFirstname("newPlayer");
    playerInput.setEmail("newUser@newUser.com");

    Mockito.when(userRepository.findByEmailIgnoreCase("newUser@newUser.com"))
        .thenReturn(Optional.of(user));
    playerService.upsertPlayer(playerInput);
    Mockito.verify(userRepository, Mockito.times(1)).save(Mockito.any());
  }

  @Test
  public void test_upsertPlayer_tagsDifferent_shouldUpdate() {
    PlayerInput playerInput = new PlayerInput();
    User user = new User();
    user.setEmail("newUser@newUser.com");

    playerInput.setEmail("newUser@newUser.com");
    playerInput.setTagIds(List.of("tag1", "tag2"));

    Mockito.when(userRepository.findByEmailIgnoreCase("newUser@newUser.com"))
        .thenReturn(Optional.of(user));

    playerService.upsertPlayer(playerInput);
    Mockito.verify(userRepository, Mockito.times(1)).save(Mockito.any());
  }

  @Test
  public void test_upsertPlayer_tagsDifferent_shouldNotUpdate() {
    PlayerInput playerInput = new PlayerInput();
    User user = new User();
    user.setEmail("newUser@newUser.com");
    user.setTags(new HashSet<>(Set.of(getTag("tag1"), getTag("tag2"))));

    playerInput.setEmail("newUser@newUser.com");
    playerInput.setTagIds(List.of("tag1", "tag2"));

    Mockito.when(userRepository.findByEmailIgnoreCase("newUser@newUser.com"))
        .thenReturn(Optional.of(user));

    playerService.upsertPlayer(playerInput);
    Mockito.verify(userRepository, Mockito.never()).save(Mockito.any());
  }

  @Test
  public void test_upsertPlayer_organizationDifferent_shouldUpdate() {
    PlayerInput playerInput = new PlayerInput();
    User user = new User();
    Organization organization = new Organization();

    user.setEmail("newUser@newUser.com");
    user.setOrganization(organization);

    playerInput.setEmail("newUser@newUser.com");
    playerInput.setOrganizationId("newOrg");

    Mockito.when(userRepository.findByEmailIgnoreCase("newUser@newUser.com"))
        .thenReturn(Optional.of(user));

    playerService.upsertPlayer(playerInput);
    Mockito.verify(userRepository, Mockito.times(1)).save(Mockito.any());
  }
}
