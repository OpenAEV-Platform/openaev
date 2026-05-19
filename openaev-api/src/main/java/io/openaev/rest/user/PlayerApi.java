package io.openaev.rest.user;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.helper.DatabaseHelper.updateRelation;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.config.SessionManager;
import io.openaev.database.model.*;
import io.openaev.database.raw.RawPlayer;
import io.openaev.database.repository.*;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.user.form.player.PlayerInput;
import io.openaev.rest.user.form.player.PlayerOutput;
import io.openaev.service.UserService;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PlayerApi extends RestBehavior {

  public static final String PLAYER_URI = "/api/players";
  private static final String TENANT_PLAYER_URI = TENANT_PREFIX + "/players";
  private static final String PLAYER_COMMUNICATION_URI = "/api/player/{userId}/communications";
  private static final String TENANT_PLAYER_COMMUNICATION_URI =
      TENANT_PREFIX + "/player/{userId}/communications";

  @Resource private SessionManager sessionManager;

  private final CommunicationRepository communicationRepository;
  private final OrganizationRepository organizationRepository;
  private final UserRepository userRepository;
  private final TagRepository tagRepository;
  private final UserService userService;
  private final PlayerService playerService;

  @GetMapping({PLAYER_URI, TENANT_PLAYER_URI})
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.PLAYER)
  @Transactional(rollbackOn = Exception.class)
  public Iterable<RawPlayer> players() {
    List<RawPlayer> players;
    User currentUser = userService.currentUser();
    players = fromIterable(userRepository.rawAllPlayers());
    return players;
  }

  @LogExecutionTime
  @PostMapping({PLAYER_URI + "/search", TENANT_PLAYER_URI + "/search"})
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.PLAYER)
  public Page<PlayerOutput> players(
      @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return this.playerService.playerPagination(searchPaginationInput);
  }

  @GetMapping({PLAYER_COMMUNICATION_URI, TENANT_PLAYER_COMMUNICATION_URI})
  @AccessControl(skipRBAC = true)
  public Iterable<Communication> playerCommunications(@PathVariable String userId) {
    return communicationRepository.findByUser(userId);
  }

  @PostMapping({PLAYER_URI, TENANT_PLAYER_URI})
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.PLAYER)
  @Transactional(rollbackOn = Exception.class)
  public User createPlayer(@Valid @RequestBody PlayerInput input) {
    return playerService.createPlayer(input);
  }

  @PostMapping({PLAYER_URI + "/upsert", TENANT_PLAYER_URI + "/upsert"})
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.PLAYER)
  @Transactional(rollbackOn = Exception.class)
  public User upsertPlayer(@Valid @RequestBody PlayerInput input) {
    return playerService.upsertPlayer(input);
  }

  @PutMapping({PLAYER_URI + "/{userId}", TENANT_PLAYER_URI + "/{userId}"})
  @AccessControl(
      resourceId = "#userId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.PLAYER)
  public User updatePlayer(@PathVariable String userId, @Valid @RequestBody PlayerInput input) {
    User user = userRepository.findById(userId).orElseThrow(ElementNotFoundException::new);
    user.setUpdateAttributes(input);
    user.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    user.setOrganization(
        updateRelation(input.getOrganizationId(), user.getOrganization(), organizationRepository));
    return userRepository.save(user);
  }

  @DeleteMapping({PLAYER_URI + "/{userId}", TENANT_PLAYER_URI + "/{userId}"})
  @AccessControl(
      resourceId = "#userId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.PLAYER)
  public void deletePlayer(@PathVariable String userId) {
    sessionManager.invalidateUserSession(userId);
    userRepository.deleteById(userId);
  }
}
