package io.openaev.database.model;

import static io.openaev.utils.fixtures.UserFixture.getUser;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.database.audit.BaseEvent;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.GrantFixture;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.GrantComposer;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code Group.users} is LAZY. The stream serializes an entity again after the commit, on an async
 * thread without a session ({@code StreamService#listenDatabaseUpdate}); it only works because the
 * {@link BaseEvent} built during the flush already loaded the members. These tests serialize the
 * event's instance once it is detached, as the stream does.
 */
@Transactional
@RecordApplicationEvents
@WithMockUser(isAdmin = true)
@DisplayName("Group members (LAZY) read outside a session")
class GroupUsersLazyFetchTest extends IntegrationTest {

  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private GrantComposer grantComposer;
  @Autowired private ObjectMapper mapper;
  @Autowired private ApplicationEvents applicationEvents;

  private BaseEvent lastEventOf(String entityId) {
    return applicationEvents.stream(BaseEvent.class)
        .filter(event -> entityId.equals(event.getInstance().getId()))
        .reduce((first, second) -> second)
        .orElseThrow();
  }

  private static Iterable<String> ids(JsonNode array) {
    return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
  }

  @Test
  @DisplayName("given_groupUpdated_should_streamItsMembersOnceDetached")
  void given_groupUpdated_should_streamItsMembersOnceDetached() {
    // -- ARRANGE --
    TenantGroupComposer.Composer groupComposer =
        tenantGroupComposer.forGroup(TenantGroupFixture.getGroup());
    String groupId = groupComposer.persist().get().getId();
    String memberId =
        userComposer
            .forUser(getUser("Streamed", "Member", UUID.randomUUID() + "@test.invalid"))
            .withGroup(groupComposer)
            .persist()
            .get()
            .getId();
    entityManager.flush();
    entityManager.clear();

    // -- ACT --
    Group loaded = entityManager.find(Group.class, groupId);
    loaded.setName("Renamed streamed group");
    entityManager.flush();
    entityManager.clear();
    JsonNode streamed = mapper.valueToTree(lastEventOf(groupId).getInstance());

    // -- ASSERT --
    assertThat(ids(streamed.get("group_users"))).containsExactly(memberId);
  }

  @Test
  @DisplayName("given_simulationWithAPlannerGroupUpdated_should_flushAndStreamItsPlanners")
  void given_simulationWithAPlannerGroupUpdated_should_flushAndStreamItsPlanners() {
    // -- ARRANGE --
    Exercise simulation =
        exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()).persist().get();
    TenantGroupComposer.Composer plannerGroup =
        tenantGroupComposer
            .forGroup(TenantGroupFixture.getGroup())
            .withGrant(
                grantComposer.forGrant(
                    GrantFixture.getGrantForSimulation(simulation, Grant.GRANT_TYPE.PLANNER)));
    String plannerId =
        userComposer
            .forUser(getUser("Planner", "Member", UUID.randomUUID() + "@test.invalid"))
            .withGroup(plannerGroup)
            .persist()
            .get()
            .getId();
    entityManager.flush();
    entityManager.clear();

    // -- ACT -- the update event serializes exercise_planners, loading group members mid-flush
    Exercise loaded = entityManager.find(Exercise.class, simulation.getId());
    loaded.setName("Renamed simulation");
    entityManager.flush();
    entityManager.clear();
    JsonNode streamed = mapper.valueToTree(lastEventOf(simulation.getId()).getInstance());

    // -- ASSERT --
    assertThat(ids(streamed.get("exercise_planners"))).containsExactly(plannerId);
  }
}
