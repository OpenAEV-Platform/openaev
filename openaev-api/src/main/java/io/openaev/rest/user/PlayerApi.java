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
import io.openaev.rest.user.form.player.PlayerBulkProcessingInput;
import io.openaev.rest.user.form.player.PlayerInput;
import io.openaev.rest.user.form.player.PlayerOutput;
import io.openaev.service.UserService;
import io.openaev.service.account.ReservedKeyValidator;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PlayerApi extends RestBehavior {

  public static final String PLAYER_URI = "/api/players";
  private static final String TENANT_PLAYER_URI = TENANT_PREFIX + "/players";

  @Resource private SessionManager sessionManager;

  private final CommunicationRepository communicationRepository;
  private final OrganizationRepository organizationRepository;
  private final UserRepository userRepository;
  private final TagRepository tagRepository;
  private final UserService userService;
  private final PlayerService playerService;

  @GetMapping({PLAYER_URI, TENANT_PLAYER_URI})
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.PLAYER)
  @Transactional(rollbackFor = Exception.class)
  public Iterable<RawPlayer> players() {
    List<RawPlayer> players;
    User currentUser = userService.currentUser();
    players = fromIterable(userRepository.rawAllPlayers());
    return players;
  }

  @LogExecutionTime
  @PostMapping({PLAYER_URI + "/search", TENANT_PLAYER_URI + "/search"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.PLAYER)
  public Page<PlayerOutput> players(
      @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return this.playerService.playerPagination(searchPaginationInput);
  }

  @PostMapping({PLAYER_URI, TENANT_PLAYER_URI})
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.PLAYER)
  @Transactional(rollbackFor = Exception.class)
  public User createPlayer(@Valid @RequestBody PlayerInput input) {
    return playerService.createPlayer(input);
  }

  @PostMapping({PLAYER_URI + "/upsert", TENANT_PLAYER_URI + "/upsert"})
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.PLAYER)
  @Transactional(rollbackFor = Exception.class)
  public User upsertPlayer(@Valid @RequestBody PlayerInput input) {
    return playerService.upsertPlayer(input);
  }

  @PutMapping({PLAYER_URI + "/{userId}", TENANT_PLAYER_URI + "/{userId}"})
  @Transactional
  @AccessControl(
      resourceId = "#userId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.PLAYER)
  public User updatePlayer(@PathVariable String userId, @Valid @RequestBody PlayerInput input) {
    ReservedKeyValidator.validateUserEmailPattern(input.getEmail());
    User user = userRepository.findById(userId).orElseThrow(ElementNotFoundException::new);
    user.setUpdateAttributes(input);
    user.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    user.setOrganization(
        updateRelation(input.getOrganizationId(), user.getOrganization(), organizationRepository));
    return userRepository.save(user);
  }

  @DeleteMapping({PLAYER_URI + "/{userId}", TENANT_PLAYER_URI + "/{userId}"})
  @Transactional
  @AccessControl(
      resourceId = "#userId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.PLAYER)
  public void deletePlayer(@PathVariable String userId) {
    userService.delete(userId);
  }

  @LogExecutionTime
  @DeleteMapping({PLAYER_URI, TENANT_PLAYER_URI})
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(actionPerformed = Action.DELETE, resourceType = ResourceType.PLAYER)
  public List<String> bulkDeletePlayers(@RequestBody @Valid final PlayerBulkProcessingInput input) {
    return playerService.bulkDeletePlayers(input);
  }

  // -- OPTIONS (for the shared filter autocomplete: id + display name) --

  private static final int OPTIONS_LIMIT = 50;

  @GetMapping({PLAYER_URI + "/options", TENANT_PLAYER_URI + "/options"})
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.PLAYER)
  public List<FilterUtilsJpa.Option> optionsByName(
      @RequestParam(required = false) final String searchText) {
    String search = searchText == null ? "" : searchText.toLowerCase();
    return fromIterable(userRepository.findAll()).stream()
        .filter(
            user ->
                search.isEmpty()
                    || user.getNameOrEmail().toLowerCase().contains(search)
                    || user.getEmail().toLowerCase().contains(search))
        .sorted(Comparator.comparing(User::getNameOrEmail, String.CASE_INSENSITIVE_ORDER))
        .limit(OPTIONS_LIMIT)
        .map(user -> new FilterUtilsJpa.Option(user.getId(), user.getNameOrEmail()))
        .toList();
  }

  @PostMapping({PLAYER_URI + "/options", TENANT_PLAYER_URI + "/options"})
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.PLAYER)
  public List<FilterUtilsJpa.Option> optionsById(@RequestBody final List<String> ids) {
    return fromIterable(userRepository.findAllById(ids)).stream()
        .map(user -> new FilterUtilsJpa.Option(user.getId(), user.getNameOrEmail()))
        .toList();
  }
}
