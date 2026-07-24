package io.openaev.rest.user;

import static io.openaev.database.criteria.GenericCriteria.countQuery;
import static io.openaev.database.specification.UserSpecification.inTenant;
import static io.openaev.helper.DatabaseHelper.updateRelation;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.rest.user.PlayerQueryHelper.execution;
import static io.openaev.rest.user.PlayerQueryHelper.select;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;
import static io.openaev.utils.pagination.SearchUtilsJpa.computeSearchJpa;
import static io.openaev.utils.pagination.SortUtilsCriteriaBuilder.toSortCriteriaBuilder;
import static java.time.Instant.now;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Team;
import io.openaev.database.model.User;
import io.openaev.database.repository.*;
import io.openaev.database.specification.SpecificationUtils;
import io.openaev.rest.exception.BadRequestException;
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
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.function.TriFunction;
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
  @PersistenceContext private EntityManager entityManager;

  private final UserRepository userRepository;
  private final UserService userService;
  private final BulkDeleteExecutor bulkDeleteExecutor;

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
  public List<String> bulkDeletePlayers(@NotNull final PlayerBulkProcessingInput input) {
    if ((CollectionUtils.isEmpty(input.getUserIdsToProcess())
            && input.getSearchPaginationInput() == null)
        || (!CollectionUtils.isEmpty(input.getUserIdsToProcess())
            && input.getSearchPaginationInput() != null)) {
      throw new BadRequestException(
          "Either user_ids_to_process or search_pagination_input must be provided, and not both at the same time");
    }
    String currentUserId = userService.currentUser().getId();
    List<String> userIdsToDelete =
        bulkDeleteExecutor.resolveInTransaction(
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
              // Users are a platform-level (dual-scope) entity: scope explicitly to the current
              // tenant, like the players list search does.
              specification = specification.and(inTenant(TenantContext.getCurrentTenant()));
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
        "players", userIdsToDelete, chunk -> chunk.forEach(userService::delete));
  }

  public Page<PlayerOutput> playerPagination(@NotNull SearchPaginationInput searchPaginationInput) {
    Specification<User> tenantSpec = inTenant(TenantContext.getCurrentTenant());
    TriFunction<Specification<User>, Specification<User>, Pageable, Page<PlayerOutput>>
        playersFunction;
    playersFunction =
        (specification, specificationCount, pageable) ->
            this.paginate(
                tenantSpec.and(specification), tenantSpec.and(specificationCount), pageable);
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

  public User createPlayer(@Valid @RequestBody PlayerInput input) {
    ReservedKeyValidator.validateUserEmailPattern(input.getEmail());
    var existingUser = userRepository.findByEmailIgnoreCase(input.getEmail());
    if (existingUser.isPresent()) {
      String userId = existingUser.get().getId();
      tenantUserService.attachToTenant(userId, TenantContext.getCurrentTenant());
      // Reload user after @Modifying queries cleared the persistence context
      return userRepository.findById(userId).orElseThrow();
    }
    User user = new User();
    user.setUpdateAttributes(input);
    user.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    user.setOrganization(
        updateRelation(input.getOrganizationId(), user.getOrganization(), organizationRepository));
    User savedUser = userRepository.save(user);
    userService.createUserToken(savedUser);
    tenantUserService.attachToTenant(savedUser.getId(), TenantContext.getCurrentTenant());
    return savedUser;
  }

  public User upsertPlayer(@Valid @RequestBody PlayerInput input) {
    ReservedKeyValidator.validateUserEmailPattern(input.getEmail());
    Optional<User> user = userRepository.findByEmailIgnoreCase(input.getEmail());
    if (user.isPresent()) {
      if (!requireUpdate(user.get(), input)) {
        return user.get();
      }
      User existingUser = user.get();
      existingUser.setUpdateAttributes(input);
      existingUser.setUpdatedAt(now());
      Iterable<String> tags =
          Stream.concat(
                  existingUser.getTags().stream().map(Tag::getId).toList().stream(),
                  input.getTagIds().stream())
              .distinct()
              .toList();
      existingUser.setTags(iterableToSet(tagRepository.findAllById(tags)));
      Iterable<String> teams =
          Stream.concat(
                  existingUser.getTeams().stream().map(Team::getId).toList().stream(),
                  input.getTeamIds().stream())
              .distinct()
              .toList();
      existingUser.setTeams(fromIterable(teamRepository.findAllById(teams)));
      if (StringUtils.hasText(input.getOrganizationId())) {
        existingUser.setOrganization(
            updateRelation(
                input.getOrganizationId(), existingUser.getOrganization(), organizationRepository));
      }
      tenantUserService.attachToTenant(existingUser.getId(), TenantContext.getCurrentTenant());
      return userRepository.save(existingUser);
    } else {
      User newUser = new User();
      newUser.setUpdateAttributes(input);
      newUser.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
      newUser.setOrganization(
          updateRelation(
              input.getOrganizationId(), newUser.getOrganization(), organizationRepository));
      newUser.setTeams(fromIterable(teamRepository.findAllById(input.getTeamIds())));
      User savedUser = userRepository.save(newUser);
      userService.createUserToken(savedUser);
      tenantUserService.attachToTenant(savedUser.getId(), TenantContext.getCurrentTenant());
      return savedUser;
    }
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
