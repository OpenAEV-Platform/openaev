package io.openaev.rest.user;

import static io.openaev.helper.DatabaseHelper.updateRelation;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;

import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.RBAC;
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

  @Resource private SessionManager sessionManager;

  private final CommunicationRepository communicationRepository;
  private final OrganizationRepository organizationRepository;
  private final UserRepository userRepository;
  private final TagRepository tagRepository;
  private final UserService userService;
  private final TeamRepository teamRepository;
  private final PlayerService playerService;

  @GetMapping(PLAYER_URI)
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLAYER)
  @Transactional(rollbackOn = Exception.class)
  public Iterable<RawPlayer> players() {
    List<RawPlayer> players;
    User currentUser = userService.currentUser();
    players = fromIterable(userRepository.rawAllPlayers());
    return players;
  }

  @LogExecutionTime
  @PostMapping(PLAYER_URI + "/search")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.PLAYER)
  public Page<PlayerOutput> players(
      @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return this.playerService.playerPagination(searchPaginationInput);
  }

  @GetMapping("/api/player/{userId}/communications")
  @RBAC(skipRBAC = true)
  public Iterable<Communication> playerCommunications(@PathVariable String userId) {
    return communicationRepository.findByUser(userId);
  }

  @PostMapping(PLAYER_URI)
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.PLAYER)
  @Transactional(rollbackOn = Exception.class)
  public User createPlayer(@Valid @RequestBody PlayerInput input) {
    User user = new User();
    user.setUpdateAttributes(input);
    user.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    user.setOrganization(
        updateRelation(input.getOrganizationId(), user.getOrganization(), organizationRepository));
    User savedUser = userRepository.save(user);
    userService.createUserToken(savedUser);
    return savedUser;
  }

  @PostMapping(PLAYER_URI + "/upsert")
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.PLAYER)
  @Transactional(rollbackOn = Exception.class)
  public User upsertPlayer(@Valid @RequestBody PlayerInput input) {
    return playerService.upsertPlayer(input);
  }

  @PutMapping(PLAYER_URI + "/{userId}")
  @RBAC(resourceId = "#userId", actionPerformed = Action.WRITE, resourceType = ResourceType.PLAYER)
  public User updatePlayer(@PathVariable String userId, @Valid @RequestBody PlayerInput input) {
    User user = userRepository.findById(userId).orElseThrow(ElementNotFoundException::new);
    user.setUpdateAttributes(input);
    user.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    user.setOrganization(
        updateRelation(input.getOrganizationId(), user.getOrganization(), organizationRepository));
    return userRepository.save(user);
  }

  @DeleteMapping(PLAYER_URI + "/{userId}")
  @RBAC(resourceId = "#userId", actionPerformed = Action.DELETE, resourceType = ResourceType.PLAYER)
  public void deletePlayer(@PathVariable String userId) {
    sessionManager.invalidateUserSession(userId);
    userRepository.deleteById(userId);
  }
}
