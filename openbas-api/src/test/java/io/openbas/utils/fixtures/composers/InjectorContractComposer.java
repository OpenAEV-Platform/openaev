package io.openbas.utils.fixtures.composers;

import static io.openbas.injectors.challenge.ChallengeContract.CHALLENGE_PUBLISH;
import static io.openbas.injectors.channel.ChannelContract.CHANNEL_PUBLISH;
import static io.openbas.injectors.email.EmailContract.EMAIL_DEFAULT;
import static io.openbas.injectors.email.EmailContract.EMAIL_GLOBAL;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openbas.database.model.AttackPattern;
import io.openbas.database.model.Cve;
import io.openbas.database.model.Injector;
import io.openbas.database.model.InjectorContract;
import io.openbas.database.repository.InjectorContractRepository;
import io.openbas.database.repository.InjectorRepository;
import io.openbas.injectors.challenge.model.ChallengeContent;
import io.openbas.injectors.channel.model.ChannelContent;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class InjectorContractComposer extends ComposerBase<InjectorContract> {
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private ObjectMapper objectMapper;

  public class Composer extends InnerComposerBase<InjectorContract> {
    private final List<String> WELL_KNOWN_CONTRACT_IDS =
        List.of(CHALLENGE_PUBLISH, CHANNEL_PUBLISH, EMAIL_DEFAULT, EMAIL_GLOBAL);

    private final InjectorContract injectorContract;
    private final List<AttackPatternComposer.Composer> attackPatternComposer = new ArrayList<>();
    private final List<CveComposer.Composer> vulnerabilityComposer = new ArrayList<>();
    private Optional<PayloadComposer.Composer> payloadComposer = Optional.empty();
    private final List<ChallengeComposer.Composer> challengeComposers = new ArrayList<>();
    private final List<ArticleComposer.Composer> articleComposers = new ArrayList<>();

    public Composer(InjectorContract injectorContract) {
      this.injectorContract = injectorContract;
    }

    public Composer withPayload(PayloadComposer.Composer payloadComposer) {
      if (!this.articleComposers.isEmpty() || !this.challengeComposers.isEmpty()) {
        throw new IllegalStateException("Inject already has a type");
      }
      this.payloadComposer = Optional.of(payloadComposer);
      this.injectorContract.setPayload(payloadComposer.get());
      return this;
    }

    public Composer withAttackPattern(AttackPatternComposer.Composer attackPatternComposer) {
      this.attackPatternComposer.add(attackPatternComposer);
      List<AttackPattern> tempAttackPattern = this.injectorContract.getAttackPatterns();
      tempAttackPattern.add(attackPatternComposer.get());
      this.injectorContract.setAttackPatterns(tempAttackPattern);
      return this;
    }

    public Composer withVulnerability(CveComposer.Composer cveComposer) {
      this.vulnerabilityComposer.add(cveComposer);
      Set<Cve> tempVulnerability = this.injectorContract.getVulnerabilities();
      tempVulnerability.add(cveComposer.get());
      this.injectorContract.setVulnerabilities(tempVulnerability);
      return this;
    }

    public Composer withChallenge(ChallengeComposer.Composer challengeComposer) {
      if (this.payloadComposer.isPresent() || !this.articleComposers.isEmpty()) {
        throw new IllegalStateException("Inject already has a type");
      }
      // hack the wrapped object to match the well-known CHALLENGE_PUBLISH characteristics
      InjectorContract challengeInjectorContract =
          injectorContractRepository.findById(CHALLENGE_PUBLISH).orElseThrow();
      this.injectorContract.setId(challengeInjectorContract.getId());
      this.injectorContract.setContent(challengeInjectorContract.getContent());
      this.injectorContract.setConvertedContent(challengeInjectorContract.getConvertedContent());
      this.injectorContract.setInjector(challengeInjectorContract.getInjector());
      this.injectorContract.setPlatforms(challengeInjectorContract.getPlatforms());
      this.injectorContract.setUpdatedAt(challengeInjectorContract.getUpdatedAt());
      this.injectorContract.setCreatedAt(challengeInjectorContract.getCreatedAt());

      this.challengeComposers.add(challengeComposer);
      return this;
    }

    public Composer withArticle(ArticleComposer.Composer articleComposer) {
      if (this.payloadComposer.isPresent() || !this.challengeComposers.isEmpty()) {
        throw new IllegalStateException("Inject already has a type");
      }

      InjectorContract articleInjectorContract =
          injectorContractRepository.findById(CHANNEL_PUBLISH).orElseThrow();
      this.injectorContract.setId(articleInjectorContract.getId());
      this.injectorContract.setContent(articleInjectorContract.getContent());
      this.injectorContract.setConvertedContent(articleInjectorContract.getConvertedContent());
      this.injectorContract.setInjector(articleInjectorContract.getInjector());
      this.injectorContract.setPlatforms(articleInjectorContract.getPlatforms());
      this.injectorContract.setUpdatedAt(articleInjectorContract.getUpdatedAt());
      this.injectorContract.setCreatedAt(articleInjectorContract.getCreatedAt());

      this.articleComposers.add(articleComposer);
      return this;
    }

    public Composer withInjector(Injector injector) {
      this.injectorContract.setInjector(injector);
      return this;
    }

    public ObjectNode getInjectContent() {
      if (payloadComposer.isPresent()) {
        return objectMapper.createObjectNode();
      }

      if (!challengeComposers.isEmpty()) {
        // Challenges may not be persisted at this point, hence may not have IDs
        ChallengeContent cc = new ChallengeContent();
        cc.setChallenges(
            challengeComposers.stream().map(composer -> composer.get().getId()).toList());
        return objectMapper.valueToTree(cc);
      }

      if (!articleComposers.isEmpty()) {
        ChannelContent cc = new ChannelContent();
        cc.setArticles(articleComposers.stream().map(composer -> composer.get().getId()).toList());
        return objectMapper.valueToTree(cc);
      }

      return objectMapper.createObjectNode();
    }

    @Override
    public Composer persist() {
      payloadComposer.ifPresent(PayloadComposer.Composer::persist);
      challengeComposers.forEach(ChallengeComposer.Composer::persist);
      articleComposers.forEach(ArticleComposer.Composer::persist);
      attackPatternComposer.forEach(AttackPatternComposer.Composer::persist);
      vulnerabilityComposer.forEach(CveComposer.Composer::persist);
      if (!WELL_KNOWN_CONTRACT_IDS.contains(injectorContract.getId())) {
        entityManager.persist(injectorContract.getInjector());
        injectorRepository.save(injectorContract.getInjector());
        // for some reason hibernate refuses to save the entity with the repository
        entityManager.persist(injectorContract);
        injectorContractRepository.save(injectorContract);
      }
      return this;
    }

    @Override
    public Composer delete() {
      payloadComposer.ifPresent(PayloadComposer.Composer::delete);
      challengeComposers.forEach(ChallengeComposer.Composer::delete);
      articleComposers.forEach(ArticleComposer.Composer::delete);
      attackPatternComposer.forEach(AttackPatternComposer.Composer::delete);
      vulnerabilityComposer.forEach(CveComposer.Composer::delete);
      if (!WELL_KNOWN_CONTRACT_IDS.contains(injectorContract.getId())) {
        injectorContractRepository.delete(injectorContract);
      }
      return this;
    }

    @Override
    public InjectorContract get() {
      return this.injectorContract;
    }
  }

  public Composer forInjectorContract(InjectorContract injectorContract) {
    this.generatedItems.add(injectorContract);
    return new Composer(injectorContract);
  }
}
