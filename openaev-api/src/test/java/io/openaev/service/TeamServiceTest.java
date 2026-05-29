package io.openaev.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Organization;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Team;
import io.openaev.database.model.User;
import io.openaev.database.repository.TeamRepository;
import io.openaev.rest.team.output.TeamOutput;
import io.openaev.utils.fixtures.OrganizationFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.*;
import java.util.stream.Stream;
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
    void given_fullTeam_should_copyAllFields() {
      // Arrange
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
    void given_teamWithEmptyCollections_should_copySuccessfully() {
      // Arrange
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
    void given_teamWithNullSimpleFields_should_copySuccessfully() {
      // Arrange
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
    void given_nullTags_should_throwNullPointerException() {
      // Arrange
      Team teamToCopy = createPartialMockTeam("Team", "Desc", null);

      // Act & Assert
      assertThrows(NullPointerException.class, () -> teamService.copyContextualTeam(teamToCopy));
    }

    @Test
    @DisplayName("should throw NullPointerException when users is null")
    void given_nullUsers_should_throwNullPointerException() {
      // Arrange
      Team teamToCopy =
          createLessPartialMockTeam("Team", "Desc", null, Collections.emptySet(), null);

      // Act & Assert
      assertThrows(NullPointerException.class, () -> teamService.copyContextualTeam(teamToCopy));
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
    void given_teamIds_should_returnTeams(
        String name, List<String> inputIds, List<Team> expected, int expectedSize) {
      // Arrange
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

  // ========================================================================
  // findByIds Tests
  // ========================================================================
  @Nested
  @DisplayName("findByIds")
  class FindByIdsTests {

    @Mock private Query nativeQuery;

    @Test
    @DisplayName("should return empty list immediately when teamIds is empty")
    void given_emptyList_should_returnEmptyWithoutQueryingDatabase() {
      // Act
      List<TeamOutput> result = teamService.findByIds(Collections.emptyList());

      // Assert
      assertNotNull(result);
      assertTrue(result.isEmpty());
      verifyNoInteractions(entityManager);
    }

    @Test
    @DisplayName("should execute native query when teamIds is non-empty")
    void given_validIds_should_executeNativeQuery() {
      // Arrange
      try (MockedStatic<TenantContext> tc = mockStatic(TenantContext.class)) {
        tc.when(TenantContext::getCurrentTenant).thenReturn(null);
        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(Collections.emptyList());

        // Act
        List<TeamOutput> result = teamService.findByIds(List.of("id-1", "id-2"));

        // Assert
        assertNotNull(result);
        verify(entityManager).createNativeQuery(anyString());
        verify(nativeQuery).setParameter("ids", List.of("id-1", "id-2"));
      }
    }

    @Test
    @DisplayName("should set tenantId parameter when tenant is present")
    void given_tenantContext_should_setTenantParameter() {
      // Arrange
      try (MockedStatic<TenantContext> tc = mockStatic(TenantContext.class)) {
        tc.when(TenantContext::getCurrentTenant).thenReturn("tenant-1");
        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(Collections.emptyList());

        // Act
        teamService.findByIds(List.of("id-1"));

        // Assert
        verify(nativeQuery).setParameter("tenantId", "tenant-1");
      }
    }
  }

  // ========================================================================
  // findByExerciseId Tests
  // ========================================================================
  @Nested
  @DisplayName("findByExerciseId")
  class FindByExerciseIdTests {

    @Mock private Query nativeQuery;

    @Test
    @DisplayName("should execute native query with exerciseId parameter")
    void given_exerciseId_should_executeNativeQuery() {
      // Arrange
      try (MockedStatic<TenantContext> tc = mockStatic(TenantContext.class)) {
        tc.when(TenantContext::getCurrentTenant).thenReturn(null);
        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(Collections.emptyList());

        // Act
        List<TeamOutput> result = teamService.findByExerciseId("exercise-1");

        // Assert
        assertNotNull(result);
        verify(nativeQuery).setParameter("exerciseId", "exercise-1");
      }
    }

    @Test
    @DisplayName("should set tenantId parameter when tenant is present")
    void given_tenantContext_should_setTenantParameter() {
      // Arrange
      try (MockedStatic<TenantContext> tc = mockStatic(TenantContext.class)) {
        tc.when(TenantContext::getCurrentTenant).thenReturn("tenant-1");
        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(Collections.emptyList());

        // Act
        teamService.findByExerciseId("exercise-1");

        // Assert
        verify(nativeQuery).setParameter("tenantId", "tenant-1");
      }
    }
  }

  // ========================================================================
  // findByScenarioId Tests
  // ========================================================================
  @Nested
  @DisplayName("findByScenarioId")
  class FindByScenarioIdTests {

    @Mock private Query nativeQuery;

    @Test
    @DisplayName("should execute native query with scenarioId parameter")
    void given_scenarioId_should_executeNativeQuery() {
      // Arrange
      try (MockedStatic<TenantContext> tc = mockStatic(TenantContext.class)) {
        tc.when(TenantContext::getCurrentTenant).thenReturn(null);
        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(Collections.emptyList());

        // Act
        List<TeamOutput> result = teamService.findByScenarioId("scenario-1");

        // Assert
        assertNotNull(result);
        verify(nativeQuery).setParameter("scenarioId", "scenario-1");
      }
    }

    @Test
    @DisplayName("should set tenantId parameter when tenant is present")
    void given_tenantContext_should_setTenantParameter() {
      // Arrange
      try (MockedStatic<TenantContext> tc = mockStatic(TenantContext.class)) {
        tc.when(TenantContext::getCurrentTenant).thenReturn("tenant-1");
        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(Collections.emptyList());

        // Act
        teamService.findByScenarioId("scenario-1");

        // Assert
        verify(nativeQuery).setParameter("tenantId", "tenant-1");
      }
    }
  }

  // ========================================================================
  // teamPaginationSimple Tests
  // ========================================================================
  @Nested
  @DisplayName("teamPaginationSimple")
  class TeamPaginationSimpleTests {

    @Mock private Query dataQuery;
    @Mock private Query countQuery;

    private SearchPaginationInput createInput(int page, int size, String textSearch) {
      SearchPaginationInput input = mock(SearchPaginationInput.class);
      when(input.getPage()).thenReturn(page);
      when(input.getSize()).thenReturn(size);
      lenient().when(input.getTextSearch()).thenReturn(textSearch);
      return input;
    }

    @BeforeEach
    void setUpQueries() {
      when(entityManager.createNativeQuery(anyString()))
          .thenReturn(dataQuery)
          .thenReturn(countQuery);
      when(dataQuery.getResultList()).thenReturn(Collections.emptyList());
      when(countQuery.getSingleResult()).thenReturn(0L);
    }

    @Test
    @DisplayName("should return a page result")
    void given_paginationInput_should_returnPage() {
      // Arrange
      try (MockedStatic<TenantContext> tc = mockStatic(TenantContext.class)) {
        tc.when(TenantContext::getCurrentTenant).thenReturn(null);

        // Act
        Page<TeamOutput> result =
            teamService.teamPaginationSimple(createInput(0, 10, null), null);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
        assertEquals(10, result.getSize());
      }
    }

    @Test
    @DisplayName("should set contextual parameter when contextual filter is provided")
    void given_contextualFilter_should_setContextualParameter() {
      // Arrange
      try (MockedStatic<TenantContext> tc = mockStatic(TenantContext.class)) {
        tc.when(TenantContext::getCurrentTenant).thenReturn(null);

        // Act
        teamService.teamPaginationSimple(createInput(0, 10, null), false);

        // Assert
        verify(dataQuery).setParameter("contextual", false);
        verify(countQuery).setParameter("contextual", false);
      }
    }

    @Test
    @DisplayName("should not set contextual parameter when contextual is null")
    void given_nullContextual_should_notSetContextualParameter() {
      // Arrange
      try (MockedStatic<TenantContext> tc = mockStatic(TenantContext.class)) {
        tc.when(TenantContext::getCurrentTenant).thenReturn(null);

        // Act
        teamService.teamPaginationSimple(createInput(0, 10, null), null);

        // Assert
        verify(dataQuery, never()).setParameter(eq("contextual"), any());
      }
    }

    @Test
    @DisplayName("should set pagination parameters correctly")
    void given_pageAndSize_should_setPaginationParameters() {
      // Arrange
      try (MockedStatic<TenantContext> tc = mockStatic(TenantContext.class)) {
        tc.when(TenantContext::getCurrentTenant).thenReturn(null);

        // Act
        teamService.teamPaginationSimple(createInput(2, 15, null), null);

        // Assert
        verify(dataQuery).setParameter("pageSize", 15);
        verify(dataQuery).setParameter("offset", 30L);
      }
    }
  }
}
