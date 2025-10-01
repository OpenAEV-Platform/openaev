package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Challenge;
import io.openaev.database.model.Document;
import io.openaev.database.model.Tag;
import io.openaev.database.repository.ChallengeRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ChallengeComposer extends ComposerBase<Challenge> {
  @Autowired private ChallengeRepository challengeRepository;

  public class Composer extends InnerComposerBase<Challenge> {
    private final Challenge challenge;
    private final List<TagComposer.Composer> tagComposers = new ArrayList<>();
    private final List<DocumentComposer.Composer> documentComposers = new ArrayList<>();

    public Composer(Challenge challenge) {
      this.challenge = challenge;
    }

    public Composer withTag(TagComposer.Composer tagComposer) {
      tagComposers.add(tagComposer);
      Set<Tag> tempTags = this.challenge.getTags();
      tempTags.add(tagComposer.get());
      this.challenge.setTags(tempTags);
      return this;
    }

    public Composer withDocument(DocumentComposer.Composer documentComposer) {
      documentComposers.add(documentComposer);
      List<Document> tempDocuments = this.challenge.getDocuments();
      tempDocuments.add(documentComposer.get());
      this.challenge.setDocuments(tempDocuments);
      return this;
    }

    public Composer withId(String id) {
      this.challenge.setId(id);
      return this;
    }

    @Override
    public Composer persist() {
      this.tagComposers.forEach(TagComposer.Composer::persist);
      this.documentComposers.forEach(DocumentComposer.Composer::persist);
      challengeRepository.save(challenge);
      return this;
    }

    @Override
    public Composer delete() {
      challengeRepository.delete(challenge);
      this.documentComposers.forEach(DocumentComposer.Composer::delete);
      this.tagComposers.forEach(TagComposer.Composer::delete);
      return null;
    }

    @Override
    public Challenge get() {
      return this.challenge;
    }
  }

  public Composer forChallenge(Challenge challenge) {
    generatedItems.add(challenge);
    return new Composer(challenge);
  }
}
