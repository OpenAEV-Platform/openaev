package io.openaev.rest.user;

import static io.openaev.database.criteria.GenericCriteria.countQuery;
import static io.openaev.database.specification.UserSpecification.byId;
import static io.openaev.database.specification.UserSpecification.inTenant;
import static io.openaev.database.specification.UserSpecification.organizationsInTenant;
import static io.openaev.helper.DatabaseHelper.resolveTenantOrganization;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.rest.user.PlayerQueryHelper.execution;
import static io.openaev.rest.user.PlayerQueryHelper.select;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;
import static io.openaev.utils.pagination.SearchUtilsJpa.computeSearchJpa;
import static io.openaev.utils.pagination.SortUtilsCriteriaBuilder.toSortCriteriaBuilder;
import static java.time.Instant.now;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.users.dto.UserMapper;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Team;
import io.openaev.database.model.User;
import io.openaev.database.repository.*;
import io.openaev.database.specification.SpecificationUtils;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.user.form.player.PlayerBulkProcessingInput;
import io.openaev.rest.user.form.player.PlayerInput;
import io.openaev.rest.user.form.player.PlayerOutput;
import io.openaev.service.UserService;
import io.openaev.service.account.ReservedKeyValidator;
import io.openaev.service.tenants.TenantUserService;
import io.openaev.service.utils.BulkDeleteExecutor;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.function.TriFunction;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;

@Service
@RequiredArgsConstructor
public class PlayerService {

  private final TagRepository tagRepository;
  private final TeamRepository teamRepository;
  private final OrganizationRepository organizationRepository;
  private final TenantUserService tenantUserService;
  private final TenantWriteScopeResolver writeScopeResolver;
  @PersistenceContext private EntityManager entityManager;

  private final UserRepository userRepository;
  private final UserService userService;
  private final BulkDeleteExecutor bulkDeleteExecutor;
  private final ObjectMapper mapper;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * Bulk delete of players, either from an explicit list of ids or from a search input (select
   * all). The current user, admin users and reserved service accounts are always excluded from the
   * deletion scope.
   *
   * <p>Not transactional as a whole: the deletion scope is resolved in a short transaction, then
   * players are deleted in small independent chunks (with deadlock retry) tracked as a massive
   * operation, so per-entity stream events are suppressed in favor of aggregated progress events.
   *
   * @param input the bulk processing input
   * @return the ids of the deleted players
   */
  public List<String> bulkDeletePlayers(
      final TxCtx ctx, @NotNull final PlayerBulkProcessingInput input) {
    if ((CollectionUtils.isEmpty(input.getUserIdsToProcess())
            && input.getSearchPaginationInput() == null)
        || (!CollectionUtils.isEmpty(input.getUserIdsToProcess())
            && input.getSearchPaginationInput() != null)) {
      throw new BadRequestException(
          "Either user_ids_to_process or search_pagination_input must be provided, and not both at the same time");
    }
    String currentUserId = userService.currentUser().getId();
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    List<String> userIdsToDelete =
        bulkDeleteExecutor.resolveInTransaction(
            ctx,
            () -> {
              Specification<User> specification;
              if (input.getSearchPaginationInput() != null) {
                // Same specification chain as the list search (filter group + text search), so the
                // deletion scope matches exactly what the user sees in the list.
                specification =
                    FilterUtilsJpa.<User>computeFilterGroupJpa(
                            input.getSearchPaginationInput().getFilterGroup())
                        .and(computeSearchJpa(input.getSearchPaginationInput().getTextSearch()));
              } else {
                specification = SpecificationUtils.hasIdIn(input.getUserIdsToProcess());
              }
              // Users are a platform-level (dual-scope) entity: scope explicitly to the tenant the
              // request writes in, the one the players are removed from.
              specification =
                  specification.and(inTenant(tenantId)).and(organizationsInTenant(tenantId));
              if (!CollectionUtils.isEmpty(input.getUserIdsToIgnore())) {
                List<String> idsToIgnore = input.getUserIdsToIgnore();
                specification =
                    specification.and((root, query, cb) -> cb.not(root.get("id").in(idsToIgnore)));
              }
              return userRepository.findAll(specification).stream()
                  // Never delete yourself, admin accounts or reserved service accounts through a
                  // bulk players deletion (the single delete has per-resource RBAC checks
                  // instead).
                  .filter(user -> !user.getId().equals(currentUserId))
                  .filter(user -> !user.isAdmin())
                  .filter(user -> !ReservedKeyValidator.isReservedUserEmail(user.getEmail()))
                  .map(User::getId)
                  .toList();
            });
    return bulkDeleteExecutor.deleteInChunks(
        ctx,
        "players",
        userIdsToDelete,
        chunk -> chunk.forEach(id -> deletePlayerOfTenant(ctx, id)));
  }

  public Page<PlayerOutput> playerPagination(@NotNull SearchPaginationInput searchPaginationInput) {
    // Reserved service/connector accounts (service-*@openaev.invalid,
    // connector-*@openaev.invalid - see ReservedKeyValidator) are system users:
    // they are not players and can never be added to a team, so they must not
    // be listed in player search results (Players page, team players picker...).
    Specification<User> excludeReservedAccounts =
        (root, query, cb) -> cb.notLike(cb.lower(root.get("email")), "%@openaev.invalid");
    Specification<User> tenantSpec =
        inTenant(TenantContext.getCurrentTenant()).and(excludeReservedAccounts);
    TriFunction<Specification<User>, Specification<User>, Pageable, Page<PlayerOutput>>
        playersFunction;
    // After the filters, which may join the organizations of the user (see organizationsInTenant).
    Specification<User> organizationsOfTenant =
        organizationsInTenant(TenantContext.getCurrentTenant());
    playersFunction =
        (specification, specificationCount, pageable) ->
            this.paginate(
                tenantSpec.and(specification).and(organizationsOfTenant),
                tenantSpec.and(specificationCount).and(organizationsOfTenant),
                pageable);
    return buildPaginationCriteriaBuilder(playersFunction, searchPaginationInput, User.class);
  }

  // -- PRIVATE --

  private Page<PlayerOutput> paginate(
      Specification<User> specification,
      Specification<User> specificationCount,
      Pageable pageable) {
    CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();

    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<User> userRoot = cq.from(User.class);
    select(cb, cq, userRoot);

    // -- Specification --
    if (specification != null) {
      Predicate predicate = specification.toPredicate(userRoot, cq, cb);
      if (predicate != null) {
        cq.where(predicate);
      }
    }

    // -- Sorting --
    List<Order> orders = toSortCriteriaBuilder(cb, userRoot, pageable.getSort());
    cq.orderBy(orders);

    // Type Query
    TypedQuery<Tuple> query = entityManager.createQuery(cq);

    // -- Pagination --
    query.setFirstResult((int) pageable.getOffset());
    query.setMaxResults(pageable.getPageSize());

    // -- EXECUTION --
    List<PlayerOutput> players = execution(query);

    // -- Count Query --
    Long total = countQuery(cb, this.entityManager, User.class, specificationCount);

    return new PageImpl<>(players, pageable, total);
  }

  public User createPlayer(TxCtx ctx, @Valid @RequestBody PlayerInput input) {
    ReservedKeyValidator.validateUserEmailPattern(input.getEmail());
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    BaseEvent.attributeRequestEventsTo(tenantId, User.class);
    var existingUser = userRepository.findByEmailIgnoreCase(input.getEmail());
    if (existingUser.isPresent()) {
      String userId = existingUser.get().getId();
      tenantUserService.attachToTenant(userId, tenantId);
      // Attaching the user ran a query that cleared the persistence context: reload it.
      return forTenant(userRepository.findById(userId).orElseThrow(), tenantId);
    }
    User user = new User();
    user.setUpdateAttributes(input);
    user.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    User savedUser = userRepository.save(user);
    assignOrganization(savedUser, input.getOrganizationId(), tenantId);
    userService.createUserToken(savedUser);
    tenantUserService.attachToTenant(savedUser.getId(), tenantId);
    return forTenant(savedUser, tenantId);
  }

  public User upsertPlayer(TxCtx ctx, @Valid @RequestBody PlayerInput input) {
    ReservedKeyValidator.validateUserEmailPattern(input.getEmail());
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    BaseEvent.attributeRequestEventsTo(tenantId, User.class);
    Optional<User> user = userRepository.findByEmailIgnoreCase(input.getEmail());
    if (user.isPresent()) {
      if (!requireUpdate(user.get(), input)) {
        return forTenant(user.get(), tenantId);
      }
      User existingUser = user.get();
      // The player is platform-level, so it may hold tags and teams of tenants this request does
      // not see. They are added one link at a time: replacing the collections would delete the
      // links the request cannot see. They go first, and the user is refreshed before its profile
      // changes, so the update event that the flush publishes carries the links just added: the
      // stream serializes that very instance afterwards.
      tagRepository
          .findAllById(input.getTagIds())
          .forEach(tag -> userRepository.addTag(existingUser.getId(), tag.getId()));
      teamRepository
          .findAllById(input.getTeamIds())
          .forEach(team -> userRepository.addTeam(existingUser.getId(), team.getId()));
      entityManager.refresh(existingUser);
      existingUser.setUpdateAttributes(input);
      existingUser.setUpdatedAt(now());
      if (StringUtils.hasText(input.getOrganizationId())) {
        assignOrganization(existingUser, input.getOrganizationId(), tenantId);
      }
      userRepository.save(existingUser);
      tenantUserService.attachToTenant(existingUser.getId(), tenantId);
      // Attaching the user ran a query that flushed the changes above and cleared the persistence
      // context, so the entity held here is detached: reload it to answer with what it now holds.
      return forTenant(userRepository.findById(existingUser.getId()).orElseThrow(), tenantId);
    } else {
      User newUser = new User();
      newUser.setUpdateAttributes(input);
      newUser.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
      newUser.setTeams(fromIterable(teamRepository.findAllById(input.getTeamIds())));
      User savedUser = userRepository.save(newUser);
      assignOrganization(savedUser, input.getOrganizationId(), tenantId);
      userService.createUserToken(savedUser);
      tenantUserService.attachToTenant(savedUser.getId(), tenantId);
      return forTenant(savedUser, tenantId);
    }
  }

  /**
   * The player a write addresses: a member of the tenant the request writes in, so that a player of
   * other tenants only is not found.
   *
   * @throws ElementNotFoundException when the player does not exist or is not in that tenant
   */
  public User findPlayerOfWriteTenant(TxCtx ctx, String userId) {
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    return userRepository
        .findOne(inTenant(tenantId).and(byId(userId)))
        .orElseThrow(ElementNotFoundException::new);
  }

  /**
   * Deletes a player from the tenant the request writes in. Removed: the team memberships ({@code
   * users_teams}), the simulation and scenario team users ({@code exercises_teams_users}, {@code
   * scenarios_teams_users}), the tag links ({@code users_tags}) and the organization ({@code
   * users_organizations}) the player holds in that tenant, then its membership and the groups that
   * membership granted. Kept: the history of the simulations, the communications the player
   * received ({@code communications_users}) and the comchecks it is asked to answer ({@code
   * comchecks_statuses}). A player left with no membership is deleted, as on a single-tenant
   * installation, and its history goes with the account; one that still belongs to another tenant
   * keeps its account, the links it holds there and its history.
   *
   * <p>A user event reaches the stream consumers of every tenant, so a removal is only published
   * for the account that is gone from every tenant.
   */
  public void deletePlayerOfTenant(TxCtx ctx, String userId) {
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    BaseEvent.attributeRequestEventsTo(tenantId, User.class);
    // The deletes of one player from its tenants queue on its row, so the check on the memberships
    // below reads what the previous one committed. The lock also stops a membership from being
    // added to the player until this transaction ends, so the account is not deleted under it.
    userRepository.lockRowAgainstNewLinks(userId);
    // The endpoint loads the player, with its groups, before this lock. The refresh reads the
    // groups as the last committed delete holds them, so revoking the groups of this tenant
    // rewrites the list from the committed groups.
    entityManager.refresh(userService.user(userId));
    userRepository.removeTeamsOfTenant(userId, tenantId);
    userRepository.removeSimulationTeamUsersOfTenant(userId, tenantId);
    userRepository.removeScenarioTeamUsersOfTenant(userId, tenantId);
    userRepository.removeTagsOfTenant(userId, tenantId);
    userRepository.removeOrganizationsOfTenant(userId, tenantId);
    tenantUserService.detach(ctx, userId);
    if (userRepository.findTenantIdsByUserId(userId).isEmpty()) {
      BaseEvent removal =
          new BaseEvent(ModelBaseListener.DATA_DELETE, userService.user(userId), mapper);
      userService.delete(userId);
      eventPublisher.publishEvent(removal);
    }
  }

  /**
   * Updates the profile of a player in the tenant the request writes in: the organization is
   * assigned or cleared in that tenant only, the ones the player holds in other tenants stay. The
   * tags of the tenants out of the request's scope are left alone too.
   *
   * @return the saved player, as the response shows it
   */
  public User updatePlayer(TxCtx ctx, User user, PlayerInput input) {
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    BaseEvent.attributeRequestEventsTo(tenantId, User.class);
    user.setFirstname(input.getFirstname());
    user.setLastname(input.getLastname());
    user.setPhone(input.getPhone());
    user.setPhone2(input.getPhone2());
    user.setPgpKey(input.getPgpKey());
    user.setCountry(input.getCountry());
    userService.replaceTags(user, iterableToSet(tagRepository.findAllById(input.getTagIds())));
    assignOrganization(user, input.getOrganizationId(), tenantId);
    return forTenant(userRepository.save(user), tenantId);
  }

  /**
   * The user as the response shows it: its organization is the one of the tenant the write went to,
   * not of the ambient tenant, which is the default one when the request names another in {@code
   * X-Tenant-Ids}. A copy, because the entity itself is the one the stream serializes afterwards
   * for the consumers of every tenant.
   */
  private User forTenant(User user, String tenantId) {
    return UserMapper.toResponseView(user, tenantId);
  }

  /**
   * Sets the organization of the user in the given tenant, an empty id clearing it. The ones the
   * user holds in other tenants are left untouched.
   */
  private void assignOrganization(
      @NotNull final User user, final String organizationId, final String tenantId) {
    userRepository.assignOrganization(
        user,
        tenantId,
        resolveTenantOrganization(organizationId, tenantId, organizationRepository));
  }

  private boolean requireUpdate(
      @NotNull final User userDatabase, @NotNull final PlayerInput input) {

    return !Objects.equals(userDatabase.getFirstname(), input.getFirstname())
        || !Objects.equals(userDatabase.getLastname(), input.getLastname())
        || !Objects.equals(
            userDatabase.getEmail(),
            org.apache.commons.lang3.StringUtils.lowerCase(input.getEmail()))
        || !Objects.equals(userDatabase.getCountry(), input.getCountry())
        || !Objects.equals(userDatabase.getPhone(), input.getPhone())
        || !Objects.equals(userDatabase.getPhone2(), input.getPhone2())
        || !Objects.equals(userDatabase.getPgpKey(), input.getPgpKey())
        || !Objects.equals(
            userDatabase.getOrganization() == null ? null : userDatabase.getOrganization().getId(),
            input.getOrganizationId())
        || !userDatabase.getTeams().stream()
            .map(Team::getId)
            .collect(Collectors.toSet())
            .containsAll(input.getTeamIds())
        || !userDatabase.getTags().stream()
            .map(Tag::getId)
            .collect(Collectors.toSet())
            .containsAll(input.getTagIds());
  }
}
