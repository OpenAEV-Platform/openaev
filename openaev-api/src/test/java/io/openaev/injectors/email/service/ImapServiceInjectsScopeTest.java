package io.openaev.injectors.email.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Inject;
import io.openaev.database.model.User;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Pins that the IMAP router keeps recording replies once {@code injects} is v2-active. An inbound
 * email carries no tenant: the poller resolves the inject named in its footer across every tenant,
 * then records the communication and touches the inject in that inject's tenant. Without a scope on
 * the poller thread the resolve fails closed, finds nothing, and every reply is dropped silently.
 *
 * <p>Real stack: two tenants each holding their own inject and participant, both replies parsed in
 * one call on a thread with no ambient tenant, as on the {@code @Scheduled} poller. The outcome is
 * read back with JDBC, which the statement inspector never rewrites.
 *
 * <p>NOT {@code @Transactional}: the router opens its own transactions, and {@code
 * TenantScopedTransaction#execute} refuses an active one. Cleanup is auto-committed JDBC.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=injects")
@WithMockUser(isAdmin = true)
@DisplayName("IMAP reply routing with injects v2-active")
class ImapServiceInjectsScopeTest extends IntegrationTest {

  @Autowired private ImapService imapService;
  @Autowired private InjectComposer injectComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private TenantSeed tenantA;
  private TenantSeed tenantB;
  private final Instant seededAt = Instant.now().minus(1, ChronoUnit.DAYS);

  /** One tenant's inject and the participant who replies to it. */
  private record TenantSeed(String id, String injectId, User participant) {}

  @BeforeEach
  void seedTwoTenantsEachWithAnInjectAndAParticipant() {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = seedTenant();
    tenantB = seedTenant();
  }

  private TenantSeed seedTenant() {
    String tenantId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        tenantId,
        "imap-injects-scope-" + tenantId);
    TenantContext.setCurrentTenant(tenantId);
    try {
      Inject inject = injectComposer.forInject(InjectFixture.getDefaultInject()).persist().get();
      User participant =
          userComposer
              .forUser(
                  UserFixture.getUser(
                      "Imap", "Participant", UUID.randomUUID() + "@unittests.invalid"))
              .persist()
              .get();
      jdbc.update(
          "INSERT INTO users_tenants (user_id, tenant_id) VALUES (?, ?)",
          participant.getId(),
          tenantId);
      jdbc.update(
          "UPDATE injects SET inject_updated_at = ? WHERE inject_id = ?",
          Timestamp.from(seededAt),
          inject.getId());
      return new TenantSeed(tenantId, inject.getId(), participant);
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  @AfterEach
  void cleanup() {
    for (TenantSeed tenant : List.of(tenantA, tenantB)) {
      jdbc.update(
          "DELETE FROM communications_users WHERE communication_id IN (SELECT communication_id"
              + " FROM communications WHERE communication_inject = ?)",
          tenant.injectId());
      jdbc.update("DELETE FROM communications WHERE communication_inject = ?", tenant.injectId());
      jdbc.update("DELETE FROM users_tenants WHERE user_id = ?", tenant.participant().getId());
      jdbc.update("DELETE FROM users WHERE user_id = ?", tenant.participant().getId());
      jdbc.update("DELETE FROM injects WHERE tenant_id = ?", tenant.id());
      jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenant.id());
    }
  }

  private static MimeMessage replyTo(TenantSeed tenant) throws Exception {
    String messageId = "<" + UUID.randomUUID() + "@unittests.invalid>";
    Date receivedAt = new Date();
    MimeMessage message =
        new MimeMessage(Session.getInstance(new Properties())) {
          @Override
          public String getMessageID() {
            return messageId;
          }

          @Override
          public Date getReceivedDate() {
            return receivedAt;
          }
        };
    message.setFrom(new InternetAddress(tenant.participant().getEmail()));
    message.setRecipients(Message.RecipientType.TO, "mailbox@unittests.invalid");
    message.setSubject("Re: inject");
    message.setText("Acknowledged.\n[inject_id=" + tenant.injectId() + "]");
    message.setSentDate(receivedAt);
    return message;
  }

  private void parseOnThePollerThread(Message... messages) {
    // The poller thread carries no ambient tenant; fixtures leave one on the test thread.
    TenantContext.clearCurrentTenant();
    assertFalse(TenantContext.hasCurrentTenant());
    ImapService target = AopTestUtils.getUltimateTargetObject(imapService);
    ReflectionTestUtils.invokeMethod(target, "parseMessages", messages, false);
  }

  private List<String> communicationUsersOf(TenantSeed tenant) {
    return jdbc.queryForList(
        "SELECT cu.user_id FROM communications c"
            + " JOIN communications_users cu ON cu.communication_id = c.communication_id"
            + " WHERE c.communication_inject = ?",
        String.class,
        tenant.injectId());
  }

  private Instant injectUpdatedAt(TenantSeed tenant) {
    return jdbc.queryForObject(
            "SELECT inject_updated_at FROM injects WHERE inject_id = ?",
            Timestamp.class,
            tenant.injectId())
        .toInstant();
  }

  @Nested
  @DisplayName("Reply routed to the inject named in its footer")
  class ReplyRouting {

    @Test
    @DisplayName(
        "given a reply to each tenant's inject, should record each one in its own tenant only")
    void given_replyToEachTenantsInject_should_recordEachOneInItsOwnTenantOnly() throws Exception {
      // Arrange
      MimeMessage replyToA = replyTo(tenantA);
      MimeMessage replyToB = replyTo(tenantB);

      // Act
      parseOnThePollerThread(replyToA, replyToB);

      // Assert
      assertEquals(
          List.of(tenantA.participant().getId()),
          communicationUsersOf(tenantA),
          "tenant A's inject must hold exactly one communication, linked to tenant A's participant");
      assertEquals(
          List.of(tenantB.participant().getId()),
          communicationUsersOf(tenantB),
          "tenant B's inject must hold exactly one communication, linked to tenant B's participant");
    }

    @Test
    @DisplayName("given a reply to tenant B's inject, should touch tenant B's inject only")
    void given_replyToTenantBsInject_should_touchTenantBsInjectOnly() throws Exception {
      // Arrange
      MimeMessage replyToB = replyTo(tenantB);

      // Act
      parseOnThePollerThread(replyToB);

      // Assert
      assertTrue(
          injectUpdatedAt(tenantB).isAfter(seededAt.plusSeconds(1)),
          "tenant B's inject must be touched, so its simulation view refreshes");
      assertEquals(
          seededAt.truncatedTo(ChronoUnit.MILLIS),
          injectUpdatedAt(tenantA).truncatedTo(ChronoUnit.MILLIS),
          "tenant A's inject must be left alone");
      assertTrue(communicationUsersOf(tenantA).isEmpty(), "tenant A must record nothing");
    }

    @Test
    @DisplayName("given the same reply delivered twice, should record it once")
    void given_sameReplyDeliveredTwice_should_recordItOnce() throws Exception {
      // Arrange
      MimeMessage replyToB = replyTo(tenantB);

      // Act
      parseOnThePollerThread(replyToB);
      parseOnThePollerThread(replyToB);

      // Assert
      assertEquals(List.of(tenantB.participant().getId()), communicationUsersOf(tenantB));
    }
  }
}
