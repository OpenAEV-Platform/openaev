package io.openaev.rest.stream;

import static io.openaev.database.audit.ModelBaseListener.DATA_UPDATE;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.config.DefaultOpenAEVPrincipal;
import io.openaev.config.OpenAEVPrincipal;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.Grant;
import io.openaev.database.model.Inject;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import io.openaev.database.repository.InjectRepository;
import io.openaev.utils.fixtures.GrantFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.TenantRoleFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.GrantComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.fixtures.composers.TenantRoleComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.FluxSink;

/**
 * Pins that the stream's per-event read check keeps delivering inject events once {@code injects}
 * is v2-active. The check runs on the {@code streamExecutor} thread, where no {@code TxCtx} sets
 * the v2 scope, so any read of {@code injects} it made would fail closed and drop the event with no
 * error. It makes none: an inject event is checked against its parent resource, captured on the
 * event, and that check reads grants, groups and users only.
 *
 * <p>Real stack: a real inject update in tenant B, committed, published by the entity listener and
 * delivered by the real {@link StreamApi} bean on its own executor to a non-admin consumer who
 * reads the atomic testing through a grant only (no capability, so the grant query actually runs).
 *
 * <p>The second group covers the one path that still reads {@code injects} there: an event whose
 * parent could not be captured falls back to the inject itself ({@link BaseEvent}), and the check
 * then loads the inject to find its parent. No production event is known to take that fallback, so
 * the test builds the event it produces; everything after that is the real stack.
 *
 * <p>NOT {@code @Transactional}: the stream listener fires after commit, and {@link
 * TenantScopedTransaction#execute} refuses an active transaction. Cleanup is auto-committed JDBC.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=injects")
@WithMockUser(isAdmin = true)
@DisplayName("Stream delivery of inject events with injects v2-active")
class StreamApiInjectsScopeTest extends IntegrationTest {

  private static final long DELIVERY_TIMEOUT_MS = 10_000;

  @Autowired private StreamApi streamApi;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private InjectRepository injectRepository;
  @Autowired private InjectComposer injectComposer;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private TenantRoleComposer tenantRoleComposer;
  @Autowired private GrantComposer grantComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private DataSource dataSource;
  @Autowired private ObjectMapper mapper;

  private JdbcTemplate jdbc;
  private String tenantA;
  private String tenantB;
  private String injectId;
  private User grantedUser;
  private Map<String, Object> consumers;
  private final List<String> sessionIds = new java.util.ArrayList<>();

  @BeforeEach
  void seedAnAtomicTestingInTenantBReadableThroughAGrant() {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = UUID.randomUUID().toString();
    tenantB = UUID.randomUUID().toString();
    for (String id : List.of(tenantA, tenantB)) {
      jdbc.update(
          "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
              + " VALUES (?, ?, now(), now())",
          id,
          "stream-injects-scope-" + id);
    }

    TenantContext.setCurrentTenant(tenantB);
    try {
      Inject atomicTesting =
          injectComposer.forInject(InjectFixture.getDefaultInject()).persist().get();
      injectId = atomicTesting.getId();

      Grant observer =
          GrantFixture.getGrant(
              injectId, Grant.GRANT_RESOURCE_TYPE.ATOMIC_TESTING, Grant.GRANT_TYPE.OBSERVER, null);
      // Empty-capability role: the read must come from the grant, so the grant query runs.
      TenantGroupComposer.Composer group =
          tenantGroupComposer
              .forGroup(TenantGroupFixture.getGroup())
              .withRole(tenantRoleComposer.forRole(TenantRoleFixture.getRole(Set.of())))
              .withGrant(grantComposer.forGrant(observer));
      grantedUser =
          userComposer
              .forUser(
                  UserFixture.getUser(
                      "Stream", "Observer", UUID.randomUUID() + "@unittests.invalid"))
              .withGroup(group)
              .persist()
              .get();
    } finally {
      TenantContext.clearCurrentTenant();
    }

    StreamApi target = AopTestUtils.getUltimateTargetObject(streamApi);
    @SuppressWarnings("unchecked")
    Map<String, Object> map =
        (Map<String, Object>) ReflectionTestUtils.getField(target, "consumers");
    consumers = map;
  }

  @AfterEach
  void cleanup() {
    sessionIds.forEach(consumers::remove);
    sessionIds.clear();
    jdbc.update("DELETE FROM users_groups WHERE user_id = ?", grantedUser.getId());
    jdbc.update(
        "DELETE FROM grants WHERE grant_group IN (SELECT group_id FROM groups WHERE tenant_id IN"
            + " (?, ?))",
        tenantA,
        tenantB);
    jdbc.update(
        "DELETE FROM groups_roles WHERE group_id IN (SELECT group_id FROM groups WHERE tenant_id IN"
            + " (?, ?))",
        tenantA,
        tenantB);
    jdbc.update("DELETE FROM groups WHERE tenant_id IN (?, ?)", tenantA, tenantB);
    jdbc.update("DELETE FROM roles WHERE tenant_id IN (?, ?)", tenantA, tenantB);
    jdbc.update("DELETE FROM users WHERE user_id = ?", grantedUser.getId());
    jdbc.update("DELETE FROM injects WHERE tenant_id IN (?, ?)", tenantA, tenantB);
    jdbc.update("DELETE FROM tenants WHERE tenant_id IN (?, ?)", tenantA, tenantB);
  }

  @SuppressWarnings("unchecked")
  private FluxSink<Object> subscribe(String consumerTenantId) throws Exception {
    FluxSink<Object> sink = mock(FluxSink.class);
    OpenAEVPrincipal principal =
        new DefaultOpenAEVPrincipal(grantedUser.getId(), List.of(), false, "en");
    Class<?> consumerType = Class.forName("io.openaev.rest.stream.StreamApi$StreamConsumer");
    Constructor<?> constructor =
        consumerType.getDeclaredConstructor(OpenAEVPrincipal.class, String.class, FluxSink.class);
    constructor.setAccessible(true);
    String sessionId = "stream-injects-scope-" + UUID.randomUUID();
    sessionIds.add(sessionId);
    consumers.put(sessionId, constructor.newInstance(principal, consumerTenantId, sink));
    return sink;
  }

  private void renameTheInjectInTenantB() {
    tenantTx.execute(
        TxCtx.forTenant(tenantB),
        () -> {
          Inject inject = injectRepository.findById(injectId).orElseThrow();
          inject.setTitle("renamed " + UUID.randomUUID());
          injectRepository.save(inject);
        });
  }

  private boolean isUpdateOfTheInject(Object message) {
    return message instanceof ServerSentEvent<?> event
        && StreamApi.EVENT_TYPE_MESSAGE.equals(event.event())
        && event.data() instanceof BaseEvent baseEvent
        && injectId.equals(baseEvent.getInstance().getId());
  }

  @Nested
  @DisplayName("Per-event read check on the stream executor")
  class PerEventReadCheck {

    @Test
    @DisplayName(
        "given a tenant B consumer granted on the atomic testing, should receive its inject update")
    void given_tenantBConsumerGrantedOnTheAtomicTesting_should_receiveTheInjectUpdate()
        throws Exception {
      // Arrange
      FluxSink<Object> tenantBSink = subscribe(tenantB);

      // Act
      renameTheInjectInTenantB();

      // Assert
      verify(tenantBSink, timeout(DELIVERY_TIMEOUT_MS)).next(argThat(m -> isUpdateOfTheInject(m)));
    }

    @Test
    @DisplayName(
        "given the same granted user streaming tenant A, should not receive tenant B's inject update")
    void given_sameGrantedUserStreamingTenantA_should_notReceiveTenantBsInjectUpdate()
        throws Exception {
      // Arrange
      FluxSink<Object> tenantASink = subscribe(tenantA);
      FluxSink<Object> tenantBSink = subscribe(tenantB);

      // Act
      renameTheInjectInTenantB();

      // Assert
      verify(tenantBSink, timeout(DELIVERY_TIMEOUT_MS)).next(argThat(m -> isUpdateOfTheInject(m)));
      verify(tenantASink, after(2_000).never()).next(argThat(m -> isUpdateOfTheInject(m)));
    }
  }

  @Nested
  @DisplayName("Inject event whose parent could not be captured")
  class UncapturedParent {

    /**
     * The event BaseEvent builds when it cannot read the parent: the inject is its own target.
     * Built inside the transaction, as the entity listener builds it during the flush.
     */
    private BaseEvent eventTargetingTheInjectItself() {
      BaseEvent event =
          tenantTx.execute(
              TxCtx.forTenant(tenantB),
              () ->
                  new BaseEvent(
                      DATA_UPDATE, injectRepository.findById(injectId).orElseThrow(), mapper));
      ReflectionTestUtils.setField(event, "permissionResourceId", injectId);
      ReflectionTestUtils.setField(event, "permissionResourceType", ResourceType.INJECT);
      return event;
    }

    @Test
    @DisplayName(
        "given an event targeting the inject itself, should resolve its parent in tenant B and"
            + " deliver to the granted tenant B consumer")
    void given_eventTargetingTheInjectItself_should_resolveItsParentInTenantBAndDeliver()
        throws Exception {
      // Arrange
      FluxSink<Object> tenantASink = subscribe(tenantA);
      FluxSink<Object> tenantBSink = subscribe(tenantB);
      BaseEvent event = eventTargetingTheInjectItself();

      // Act
      streamApi.listenDatabaseUpdate(event);

      // Assert
      verify(tenantBSink, timeout(DELIVERY_TIMEOUT_MS)).next(argThat(m -> isUpdateOfTheInject(m)));
      verify(tenantASink, after(2_000).never()).next(argThat(m -> isUpdateOfTheInject(m)));
    }
  }
}
