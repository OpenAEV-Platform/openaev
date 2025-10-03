package io.openaev.rest.user;

import io.openaev.database.model.User;
import io.openaev.database.repository.OrganizationRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.user.form.player.PlayerInput;
import io.openaev.service.UserService;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
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
  @Mock private UserService userService;
  @InjectMocks private PlayerService playerService;

  @Test
  public void test_upsertPlayer() {
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
  public void test2_upsertPlayer() {
    PlayerInput playerInput = new PlayerInput();
    User user = new User();
    user.setFirstname("newUser");
    user.setEmail("newUser@newUser.com");

    playerInput.setFirstname("newUser");
    playerInput.setEmail("newUser@newUser.com");
    playerInput.setTagIds(List.of("tag1", "tag2"));
    playerInput.setOrganizationId("organizationId");

    Mockito.when(userRepository.findByEmailIgnoreCase("newUser@newUser.com"))
        .thenReturn(Optional.of(user));
    playerService.upsertPlayer(playerInput);
    Mockito.verify(userRepository, Mockito.times(1)).save(Mockito.any());
  }
}
