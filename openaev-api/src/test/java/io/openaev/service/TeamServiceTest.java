package io.openaev.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.openaev.database.model.Organization;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Team;
import io.openaev.database.model.User;
import io.openaev.database.raw.RawTeam;
import io.openaev.database.repository.TeamRepository;
import io.openaev.rest.team.output.TeamOutput;
import io.openaev.utils.fixtures.OrganizationFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Selection;
import java.util.*;
import java.util.stream.Stream;
import org.hibernate.query.criteria.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
@DisplayName("TeamService Tests")
class TeamServiceTest {

  @Mock private EntityManager entityManager;

  @Mock private TeamRepository teamRepository;

  @InjectMocks private TeamService teamService;

  @BeforeEach
  void setUp() {
    reset(entityManager, teamRepository);
  }

  // ========================================================================
  // getTeams Tests
  // ========================================================================
  @Nested
  @DisplayName("getTeams")
  class GetTeamsTests {

    @Captor private ArgumentCaptor<List<String>> teamIdsCaptor;

    private static Stream<Arguments> testCases() {
      String id1 = UUID.randomUUID().toString();
      String id2 = UUID.randomUUID().toString();
      String id3 = UUID.randomUUID().toString();

      return Stream.of(
          Arguments.of(
              "should return teams sorted alphabetically",
              List.of(id1, id2, id3),
              createRawTeams(id1, "Gamma", id2, "Alpha", id3, "Beta"),
              List.of("Alpha", "Beta", "Gamma")),
          Arguments.of(
              "should return empty list when no IDs provided",
              Collections.emptyList(),
              Collections.emptyList(),
              Collections.emptyList()),
          Arguments.of(
              "should return single team",
              List.of(id1),
              createRawTeams(id1, "Alpha"),
              List.of("Alpha")),
          Arguments.of(
              "should return empty list when no teams found",
              List.of(id1, id2),
              Collections.emptyList(),
              Collections.emptyList()));
    }

    private static List<RawTeam> createRawTeams(String... idNamePairs) {
      List<RawTeam> rawTeams = new ArrayList<>();
      for (int i = 0; i < idNamePairs.length; i += 2) {
        RawTeam rawTeam = mock(RawTeam.class);
        when(rawTeam.getTeam_id()).thenReturn(idNamePairs[i]);
        when(rawTeam.getTeam_name()).thenReturn(idNamePairs[i + 1]);
        rawTeams.add(rawTeam);
      }
      return rawTeams;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testCases")
    void shouldReturnTeamOutputs(
        String testName,
        List<String> inputIds,
        List<RawTeam> rawTeams,
        List<String> expectedNames) {
      // Prepare
      when(teamRepository.rawTeamByIds(inputIds)).thenReturn(rawTeams);

      // Act
      List<TeamOutput> result = teamService.getTeams(inputIds);

      // Assert
      verify(teamRepository).rawTeamByIds(teamIdsCaptor.capture());
      assertEquals(inputIds, teamIdsCaptor.getValue());
      assertNotNull(result);
      assertEquals(expectedNames.size(), result.size());
      for (int i = 0; i < expectedNames.size(); i++) {
        assertEquals(expectedNames.get(i), result.get(i).getName());
      }
      verifyNoMoreInteractions(teamRepository);
    }
  }

  // ========================================================================
  // copyContextualTeam Tests
  // ========================================================================
  @Nested
  @DisplayName("copyContextualTeam")
  class CopyContextualTeamTests {

    private Team createFullMockTeam(
        String name,
        String description,
        Boolean contextual,
        Organization org,
        Set<Tag> tags,
        List<User> users) {
      Team team = createLessPartialMockTeam(name, description, org, tags, users);
      when(team.getContextual()).thenReturn(contextual);
      return team;
    }

    private Team createLessPartialMockTeam(
        String name, String description, Organization org, Set<Tag> tags, List<User> users) {
      Team team = createPartialMockTeam(name, description, tags);
      when(team.getOrganization()).thenReturn(org);
      when(team.getUsers()).thenReturn(users);
      return team;
    }

    private Team createPartialMockTeam(String name, String description, Set<Tag> tags) {
      Team team = mock(Team.class);
      when(team.getName()).thenReturn(name);
      when(team.getDescription()).thenReturn(description);
      when(team.getTags()).thenReturn(tags);
      return team;
    }

    @Test
    @DisplayName("should copy all fields from source team")
    void shouldCopyAllFields() {
      // Prepare
      Organization org = OrganizationFixture.createOrganization();
      Set<Tag> tags = Set.of(TagFixture.getTag("Tag1"), TagFixture.getTag("Tag2"));
      List<User> users =
          List.of(
              UserFixture.getUser("team1", "team1Name", "team1@filigran.io"),
              UserFixture.getUser("team2", "team2Name", "team2@filigran.io"));
      Team teamToCopy =
          createFullMockTeam("Original Team", "Original Description", true, org, tags, users);

      // Act
      Team result = teamService.copyContextualTeam(teamToCopy);

      // Assert
      assertNotNull(result);
      assertEquals("Original Team", result.getName());
      assertEquals("Original Description", result.getDescription());
      assertEquals(org, result.getOrganization());
      assertTrue(result.getContextual());
      assertNotNull(result.getTags());
      assertNotNull(result.getUsers());
    }

    @Test
    @DisplayName("should copy team with empty collections")
    void shouldCopyWithEmptyCollections() {
      // Prepare
      Team teamToCopy =
          createFullMockTeam(
              "Team", "Desc", false, null, Collections.emptySet(), Collections.emptyList());

      // Act
      Team result = teamService.copyContextualTeam(teamToCopy);

      // Assert
      assertNotNull(result);
      assertEquals("Team", result.getName());
      assertNull(result.getOrganization());
      assertFalse(result.getContextual());
      assertTrue(result.getTags().isEmpty());
      assertTrue(result.getUsers().isEmpty());
    }

    @Test
    @DisplayName("should copy team with null simple fields")
    void shouldCopyWithNullSimpleFields() {
      // Prepare
      Team teamToCopy =
          createFullMockTeam(
              null, null, null, null, Collections.emptySet(), Collections.emptyList());

      // Act
      Team result = teamService.copyContextualTeam(teamToCopy);

      // Assert
      assertNotNull(result);
      assertNull(result.getName());
      assertNull(result.getDescription());
      assertNull(result.getContextual());
    }

    @Test
    @DisplayName("should throw NullPointerException when tags is null")
    void shouldThrowWhenTagsNull() {
      // Prepare
      Team teamToCopy = createPartialMockTeam("Team", "Desc", null);

      // Act & Assert
      assertThrows(NullPointerException.class, () -> teamService.copyContextualTeam(teamToCopy));
    }

    @Test
    @DisplayName("should throw NullPointerException when users is null")
    void shouldThrowWhenUsersNull() {
      // Prepare
      Team teamToCopy =
          createLessPartialMockTeam("Team", "Desc", null, Collections.emptySet(), null);

      // Act & Assert
      assertThrows(NullPointerException.class, () -> teamService.copyContextualTeam(teamToCopy));
    }
  }

  // ========================================================================
  // teamPagination Tests
  // ========================================================================
  @Nested
  @DisplayName("teamPagination")
  class TeamPaginationTests {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private HibernateCriteriaBuilder criteriaBuilder;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private JpaCriteriaQuery<Tuple> criteriaQuery;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private JpaCriteriaQuery<Long> countQuery;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private JpaRoot<Team> teamRoot;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private JpaRoot<Team> countRoot;

    @Mock private TypedQuery<Tuple> typedQuery;

    @Mock private TypedQuery<Long> countTypedQuery;

    @SuppressWarnings("rawtypes")
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private JpaFunction arrayAggFunction;

    @SuppressWarnings("rawtypes")
    @Mock
    private JpaExpression countExpression;

    @Captor private ArgumentCaptor<Integer> offsetCaptor;

    @Captor private ArgumentCaptor<Integer> maxResultsCaptor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUpMocks() {
      // EntityManager setup
      when(entityManager.getCriteriaBuilder()).thenReturn(criteriaBuilder);
      when(entityManager.createQuery(criteriaQuery)).thenReturn(typedQuery);
      when(entityManager.createQuery(countQuery)).thenReturn(countTypedQuery);

      // CriteriaBuilder setup
      when(criteriaBuilder.createTupleQuery()).thenReturn(criteriaQuery);
      when(criteriaBuilder.createQuery(Long.class)).thenReturn(countQuery);
      when(criteriaBuilder.function(anyString(), any(Class.class), any(Expression[].class)))
          .thenReturn(arrayAggFunction);
      when(criteriaBuilder.count(any(Expression.class))).thenReturn(countExpression);
      when(criteriaBuilder.asc(any(Expression.class))).thenReturn(mock(JpaOrder.class));
      when(criteriaBuilder.desc(any(Expression.class))).thenReturn(mock(JpaOrder.class));

      // CriteriaQuery setup
      when(criteriaQuery.from(Team.class)).thenReturn(teamRoot);
      when(criteriaQuery.multiselect(any(Selection[].class))).thenReturn(criteriaQuery);
      when(criteriaQuery.multiselect(anyList())).thenReturn(criteriaQuery);
      when(criteriaQuery.groupBy(any(Expression[].class))).thenReturn(criteriaQuery);
      when(criteriaQuery.groupBy(anyList())).thenReturn(criteriaQuery);
      when(criteriaQuery.orderBy(anyList())).thenReturn(criteriaQuery);
      when(criteriaQuery.distinct(anyBoolean())).thenReturn(criteriaQuery);

      // Count query setup
      when(countQuery.from(Team.class)).thenReturn(countRoot);
      when(countQuery.select(any(JpaExpression.class))).thenReturn(countQuery);

      // TypedQuery setup
      when(typedQuery.setFirstResult(anyInt())).thenReturn(typedQuery);
      when(typedQuery.setMaxResults(anyInt())).thenReturn(typedQuery);
      when(typedQuery.getResultList()).thenReturn(Collections.emptyList());
    }

    private SearchPaginationInput createPaginationInput(int page, int size) {
      SearchPaginationInput input = mock(SearchPaginationInput.class);
      when(input.getPage()).thenReturn(page);
      when(input.getSize()).thenReturn(size);
      when(input.getSorts()).thenReturn(Collections.emptyList());
      when(input.getTextSearch()).thenReturn(null);
      return input;
    }

    private Specification<Team> createSpecification(Predicate predicate) {
      Specification<Team> spec = mock(Specification.class);
      Specification<Team> combined = mock(Specification.class);
      when(spec.and(any())).thenReturn(combined);
      when(combined.toPredicate(any(), any(), any())).thenReturn(predicate);
      return spec;
    }

    private static Stream<Arguments> paginationTestCases() {
      return Stream.of(
          Arguments.of("first page", 0, 10, 0, 10),
          Arguments.of("second page", 1, 10, 10, 10),
          Arguments.of("custom page size", 2, 15, 30, 15),
          Arguments.of("large page number", 100, 20, 2000, 20));
    }

    @ParameterizedTest(name = "should paginate correctly for {0}")
    @MethodSource("paginationTestCases")
    void shouldPaginateCorrectly(
        String name, int page, int size, int expectedOffset, int expectedMax) {
      // Prepare
      when(countTypedQuery.getSingleResult()).thenReturn(100L);

      // Act
      Page<TeamOutput> result =
          teamService.teamPagination(createPaginationInput(page, size), createSpecification(null));

      // Assert
      verify(typedQuery).setFirstResult(offsetCaptor.capture());
      verify(typedQuery).setMaxResults(maxResultsCaptor.capture());
      assertEquals(expectedOffset, offsetCaptor.getValue());
      assertEquals(expectedMax, maxResultsCaptor.getValue());
      assertNotNull(result);
    }

    @Test
    @DisplayName("should apply where clause when predicate is not null")
    void shouldApplyWhereClause() {
      // Prepare
      Predicate predicate = mock(Predicate.class);
      when(countTypedQuery.getSingleResult()).thenReturn(50L);

      // Act
      teamService.teamPagination(createPaginationInput(0, 10), createSpecification(predicate));

      // Assert
      verify(criteriaQuery).where(predicate);
    }

    @Test
    @DisplayName("should not apply where clause when predicate is null")
    void shouldNotApplyWhereClause() {
      // Prepare
      when(countTypedQuery.getSingleResult()).thenReturn(0L);

      // Act
      teamService.teamPagination(createPaginationInput(0, 10), createSpecification(null));

      // Assert
      verify(criteriaQuery, never()).where(any(Predicate.class));
    }

    @Test
    @DisplayName("should return correct total count")
    void shouldReturnCorrectTotalCount() {
      // Prepare
      when(countTypedQuery.getSingleResult()).thenReturn(150L);

      // Act
      Page<TeamOutput> result =
          teamService.teamPagination(createPaginationInput(0, 10), createSpecification(null));

      // Assert
      assertEquals(150L, result.getTotalElements());
    }
  }

  // ========================================================================
  // find Tests
  // ========================================================================
  @Nested
  @DisplayName("find")
  class FindTests {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private HibernateCriteriaBuilder criteriaBuilder;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private JpaCriteriaQuery<Tuple> criteriaQuery;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private JpaRoot<Team> teamRoot;

    @Mock private TypedQuery<Tuple> typedQuery;

    @SuppressWarnings("rawtypes")
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private JpaFunction arrayAggFunction;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUpMocks() {
      when(entityManager.getCriteriaBuilder()).thenReturn(criteriaBuilder);
      when(entityManager.createQuery(criteriaQuery)).thenReturn(typedQuery);

      when(criteriaBuilder.createTupleQuery()).thenReturn(criteriaQuery);
      when(criteriaBuilder.function(anyString(), any(Class.class), any(Expression[].class)))
          .thenReturn(arrayAggFunction);

      when(criteriaQuery.from(Team.class)).thenReturn(teamRoot);
      when(criteriaQuery.multiselect(any(Selection[].class))).thenReturn(criteriaQuery);
      when(criteriaQuery.multiselect(anyList())).thenReturn(criteriaQuery);
      when(criteriaQuery.distinct(anyBoolean())).thenReturn(criteriaQuery);
      when(criteriaQuery.groupBy(any(Expression[].class))).thenReturn(criteriaQuery);
      when(criteriaQuery.groupBy(anyList())).thenReturn(criteriaQuery);

      when(typedQuery.getResultList()).thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("should find teams with specification")
    void shouldFindWithSpecification() {
      // Prepare
      Specification<Team> spec = mock(Specification.class);
      when(spec.toPredicate(any(), any(), any())).thenReturn(mock(Predicate.class));

      // Act
      List<TeamOutput> result = teamService.find(spec);

      // Assert
      verify(entityManager).getCriteriaBuilder();
      assertNotNull(result);
    }

    @Test
    @DisplayName("should find teams with null specification")
    void shouldFindWithNullSpecification() {
      // Act
      List<TeamOutput> result = teamService.find(null);

      // Assert
      verify(entityManager).getCriteriaBuilder();
      assertNotNull(result);
    }

    @Test
    @DisplayName("should not apply where clause when predicate is null")
    void shouldNotApplyWhereClause() {
      // Prepare
      Specification<Team> spec = mock(Specification.class);
      when(spec.toPredicate(any(), any(), any())).thenReturn(null);

      // Act
      List<TeamOutput> result = teamService.find(spec);

      // Assert
      assertNotNull(result);
      verify(criteriaQuery, never()).where(any(Predicate.class));
    }
  }

  // ========================================================================
  // getTeamsByIds Tests
  // ========================================================================
  @Nested
  @DisplayName("getTeamsByIds")
  class GetTeamsByIdsTests {

    @Captor private ArgumentCaptor<List<String>> teamIdsCaptor;

    private static Stream<Arguments> testCases() {
      String id1 = UUID.randomUUID().toString();
      String id2 = UUID.randomUUID().toString();
      String id3 = UUID.randomUUID().toString();
      Team team1 = mock(Team.class);
      Team team2 = mock(Team.class);

      return Stream.of(
          Arguments.of("multiple IDs", List.of(id1, id2), List.of(team1, team2), 2),
          Arguments.of("empty list", Collections.emptyList(), Collections.emptyList(), 0),
          Arguments.of("non-existent IDs", List.of(id1, id2), Collections.emptyList(), 0),
          Arguments.of("partial match", List.of(id1, id2, id3), List.of(team1), 1),
          Arguments.of("single ID", List.of(id1), List.of(team1), 1));
    }

    @ParameterizedTest(name = "should handle {0}")
    @MethodSource("testCases")
    void shouldReturnTeams(
        String name, List<String> inputIds, List<Team> expected, int expectedSize) {
      // Prepare
      when(teamRepository.findAllById(inputIds)).thenReturn(expected);

      // Act
      List<Team> result = teamService.getTeamsByIds(inputIds);

      // Assert
      verify(teamRepository).findAllById(teamIdsCaptor.capture());
      assertEquals(inputIds, teamIdsCaptor.getValue());
      assertNotNull(result);
      assertEquals(expectedSize, result.size());
      assertEquals(expected, result);
      verifyNoMoreInteractions(teamRepository);
    }
  }
}
