package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Challenge;
import io.openaev.database.model.Document;
import io.openaev.database.model.Executable;
import io.openaev.database.model.Tag;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.service.FileService;
import io.openaev.utils.fixtures.files.BaseFile;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class DocumentComposer extends ComposerBase<Document> {

  @Autowired private DocumentRepository documentRepository;
  @Autowired private FileService fileService;

  public class Composer extends InnerComposerBase<Document> {

    private final Document document;
    private BaseFile<?> companionFile = null;
    private final List<TagComposer.Composer> tagComposers = new ArrayList<>();
    private final List<ChallengeComposer.Composer> challengeComposers = new ArrayList<>();
    private final List<PayloadComposer.Composer> payloadExecutableComposers = new ArrayList<>();

    public Composer(Document document) {
      this.document = document;
    }

    public Composer withTag(TagComposer.Composer tagComposer) {
      tagComposers.add(tagComposer);
      Set<Tag> tempTags = this.document.getTags();
      tempTags.add(tagComposer.get());
      this.document.setTags(tempTags);
      return this;
    }

    public Composer withChallenge(ChallengeComposer.Composer challengeComposer) {
      challengeComposers.add(challengeComposer);
      Set<Challenge> tempChallenges = this.document.getChallenges();
      tempChallenges.add(challengeComposer.get());
      this.document.setChallenges(tempChallenges);
      return this;
    }

    public Composer withPayloadExecutable(PayloadComposer.Composer payloadExecutableComposer) {
      payloadExecutableComposers.add(payloadExecutableComposer);
      Set<Executable> tempExecutables = this.document.getPayloadsByExecutableFile();
      tempExecutables.add((Executable) payloadExecutableComposer.get());
      this.document.setPayloadsByExecutableFile(tempExecutables);
      return this;
    }

    public Composer withId(String id) {
      this.document.setId(id);
      return this;
    }

    public Composer withInMemoryFile(BaseFile<?> file) {
      document.setTarget(file.getFileName());
      companionFile = file;
      return this;
    }

    @SneakyThrows // the method might throw if the target file does not exist
    @Override
    public Composer persist() {
      this.tagComposers.forEach(TagComposer.Composer::persist);
      this.challengeComposers.forEach(ChallengeComposer.Composer::persist);
      this.payloadExecutableComposers.forEach(PayloadComposer.Composer::persist);
      if (companionFile != null) {
        try (ByteArrayInputStream bais =
            new ByteArrayInputStream(companionFile.getContentBytes())) {
          MultipartFile mmf = new MockMultipartFile(document.getTarget(), bais.readAllBytes());
          fileService.uploadFile(
              document.getTarget(), mmf.getInputStream(), mmf.getSize(), document.getType());
        }
      }
      documentRepository.save(document);
      return this;
    }

    @Override
    public Composer delete() {
      documentRepository.delete(document);
      this.tagComposers.forEach(TagComposer.Composer::delete);
      this.challengeComposers.forEach(ChallengeComposer.Composer::delete);
      this.payloadExecutableComposers.forEach(PayloadComposer.Composer::delete);
      return this;
    }

    @Override
    public Document get() {
      return this.document;
    }
  }

  public Composer forDocument(Document document) {
    generatedItems.add(document);
    return new Composer(document);
  }
}
