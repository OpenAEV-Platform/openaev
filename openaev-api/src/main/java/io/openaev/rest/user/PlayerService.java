package io.openaev.rest.user;

import static io.openaev.database.criteria.GenericCriteria.countQuery;
import static io.openaev.helper.DatabaseHelper.updateRelation;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.rest.user.PlayerQueryHelper.execution;
import static io.openaev.rest.user.PlayerQueryHelper.select;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;
import static io.openaev.utils.pagination.SortUtilsCriteriaBuilder.toSortCriteriaBuilder;
import static java.time.Instant.now;

import io.openaev.database.model.Tag;
import io.openaev.database.model.Team;
import io.openaev.database.model.User;
import io.openaev.database.repository.OrganizationRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.user.form.player.PlayerInput;
import io.openaev.rest.user.form.player.PlayerOutput;
import io.openaev.service.UserService;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import jakarta.validation.Valid;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;

@Service
@RequiredArgsConstructor
public class PlayerService {

  private final TagRepository tagRepository;
  private final TeamRepository teamRepository;
  private final OrganizationRepository organizationRepository;
  @PersistenceContext private EntityManager entityManager;

  private final UserRepository userRepository;
  private final UserService userService;

  public Page<PlayerOutput> playerPagination(@NotNull SearchPaginationInput searchPaginationInput) {
    TriFunction<Specification<User>, Specification<User>, Pageable, Page<PlayerOutput>>
        playersFunction;
    User currentUser = userService.currentUser();
    playersFunction = this::paginate;
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

  public User upsertPlayer(@Valid @RequestBody PlayerInput input) {
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
            .equals(new HashSet<>(input.getTeamIds()))
        || !userDatabase.getTags().stream()
            .map(Tag::getId)
            .collect(Collectors.toSet())
            .equals(new HashSet<>(input.getTagIds()));
  }
}
