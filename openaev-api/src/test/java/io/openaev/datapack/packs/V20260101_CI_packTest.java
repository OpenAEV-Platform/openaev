package io.openaev.datapack.packs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.database.raw.RawPlayer;
import io.openaev.database.repository.*;
import io.openaev.rest.payload.service.PayloadCreationService;
import io.openaev.rest.user.PlayerService;
import io.openaev.service.DataPackService;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("CI DataPack process tests")
@Transactional
public class V20260101_CI_packTest extends IntegrationTest {

  private static final Set<String> CI_PLAYER_EMAILS =
      Set.of(
          "alice.martin@ci.local",
          "bob.dupont@ci.local",
          "carol.schmidt@ci.local",
          "david.leroy@ci.local",
          "eve.moreau@ci.local");

  @Autowired private DataPackService dataPackService;
  @Autowired private PlayerService playerService;
  @Autowired private ChannelRepository channelRepository;
  @Autowired private ArticleRepository articleRepository;
  @Autowired private ChallengeRepository challengeRepository;
  @Autowired private ChallengeFlagRepository challengeFlagRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private PayloadCreationService payloadCreationService;
  @Autowired private EntityManager entityManager;
  @Autowired private PayloadRepository payloadRepository;

  private V20260101_CI_pack buildDataPack() {
    return new V20260101_CI_pack(
        dataPackService,
        playerService,
        channelRepository,
        articleRepository,
        challengeRepository,
        challengeFlagRepository,
        payloadCreationService,
        payloadRepository);
  }

  @Test
  @DisplayName("Should process CI DataPack and create all seed data")
  void should_createAllSeedData_when_processed() {
    V20260101_CI_pack datapack = buildDataPack();

    datapack.process(new Tenant(TenantContext.getCurrentTenant()));

    entityManager.flush();
    entityManager.clear();

    verifyPlayersCreated();
    verifyArticlesCreated();
    verifyChallengesCreated();
    verifyPayloadsCreated();
    verifyDataPackMarkedAsProcessed();
  }

  @Test
  @DisplayName("Should not re-process CI DataPack if already processed")
  void should_notReprocess_when_alreadyProcessed() {
    V20260101_CI_pack datapack = buildDataPack();

    datapack.process(new Tenant(TenantContext.getCurrentTenant()));
    datapack.process(new Tenant(TenantContext.getCurrentTenant()));

    entityManager.flush();
    entityManager.clear();

    var playerEmails =
        userRepository.rawAllPlayers().stream().map(RawPlayer::getUser_email).toList();
    assertThat(playerEmails).containsAll(CI_PLAYER_EMAILS);
    assertEquals(3, articleRepository.count());
    assertEquals(4, challengeRepository.count());
  }

  private void verifyPlayersCreated() {
    List<RawPlayer> players = userRepository.rawAllPlayers();
    var playerEmails = players.stream().map(RawPlayer::getUser_email).toList();
    assertThat(playerEmails).containsAll(CI_PLAYER_EMAILS);
  }

  private void verifyArticlesCreated() {
    var articles = StreamSupport.stream(articleRepository.findAll().spliterator(), false).toList();
    assertEquals(1, channelRepository.count());
    assertEquals(3, articleRepository.count());
    assertThat(articles)
        .anyMatch(a -> "Threat Intelligence Basics".equals(a.getName()))
        .anyMatch(a -> "Incident Response Playbook".equals(a.getName()))
        .anyMatch(a -> "Phishing Awareness".equals(a.getName()));
  }

  private void verifyChallengesCreated() {
    var challenges =
        StreamSupport.stream(challengeRepository.findAll().spliterator(), false).toList();
    assertEquals(4, challenges.size());
    assertThat(challenges)
        .anyMatch(c -> "Find the C2".equals(c.getName()))
        .anyMatch(c -> "Decode the Payload".equals(c.getName()))
        .anyMatch(c -> "Log Analysis".equals(c.getName()))
        .anyMatch(c -> "Privilege Escalation".equals(c.getName()));
    challenges.forEach(
        c ->
            assertEquals(
                1,
                c.getFlags().size(),
                "Challenge '%s' should have exactly 1 flag".formatted(c.getName())));
  }

  private void verifyPayloadsCreated() {
    var payloads = payloadRepository.findAll();
    long count = StreamSupport.stream(payloads.spliterator(), false).count();
    assertEquals(4, count);
    assertThat(payloads)
        .anyMatch(p -> "CI — Whoami".equals(p.getName()))
        .anyMatch(p -> "CI — System Info".equals(p.getName()))
        .anyMatch(p -> "CI — Network Scan".equals(p.getName()))
        .anyMatch(p -> "CI — DNS Exfil Sim".equals(p.getName()));
  }

  private void verifyDataPackMarkedAsProcessed() {
    assertTrue(
        dataPackService
            .findByIdAndTenant(
                V20260101_CI_pack.class.getCanonicalName(),
                new Tenant(TenantContext.getCurrentTenant()))
            .isPresent());
  }
}
