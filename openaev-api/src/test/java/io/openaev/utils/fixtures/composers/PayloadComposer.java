package io.openaev.utils.fixtures.composers;

import io.openaev.api.detection_remediation.dto.PayloadInput;
import io.openaev.database.model.*;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.utils.fixtures.composers.payload_composers.OutputParserComposer;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PayloadComposer extends ComposerBase<Payload> {

  @Autowired PayloadRepository payloadRepository;

  public class Composer extends InnerComposerBase<Payload> {

    private final Payload payload;
    private final List<TagComposer.Composer> tagComposers = new ArrayList<>();
    private Optional<DocumentComposer.Composer> documentComposer = Optional.empty();
    private final List<OutputParserComposer.Composer> outputParserComposers = new ArrayList<>();
    private final List<DetectionRemediationComposer.Composer> detectionRemediationComposers =
        new ArrayList<>();
    private final List<AttackPatternComposer.Composer> attackPatternComposers = new ArrayList<>();
    private final List<DomainComposer.Composer> domainComposers = new ArrayList<>();

    public Composer(Payload payload) {
      this.payload = payload;
    }

    public Composer withTag(TagComposer.Composer tagComposer) {
      tagComposers.add(tagComposer);
      Set<Tag> tempTags = payload.getTags();
      tempTags.add(tagComposer.get());
      payload.setTags(tempTags);
      return this;
    }

    public Composer withFileDrop(DocumentComposer.Composer newDocumentComposer) {
      if (!(payload instanceof FileDrop)) {
        throw new IllegalArgumentException("Payload is not a FileDrop");
      }
      documentComposer = Optional.of(newDocumentComposer);
      ((FileDrop) payload).setFileDropFile(newDocumentComposer.get());
      return this;
    }

    public Composer withDetectionRemediation(
        DetectionRemediationComposer.Composer detectionRemediationComposer) {
      detectionRemediationComposers.add(detectionRemediationComposer);
      DetectionRemediation detectionRemediation = detectionRemediationComposer.get();
      detectionRemediation.setPayload(payload);
      payload.addDetectionRemediation(detectionRemediation);
      return this;
    }

    public Composer withDomain(DomainComposer.Composer domainWrapper) {
      this.domainComposers.add(domainWrapper);
      Set<Domain> tempDomains = payload.getDomains();
      tempDomains.add(domainWrapper.get());
      payload.setDomains(tempDomains);
      return this;
    }

    public Composer withExecutable(DocumentComposer.Composer newDocumentComposer) {
      if (!(payload instanceof Executable)) {
        throw new IllegalArgumentException("Payload is not a Executable");
      }
      documentComposer = Optional.of(newDocumentComposer);
      ((Executable) payload).setExecutableFile(newDocumentComposer.get());
      return this;
    }

    public Composer withAttackPattern(AttackPatternComposer.Composer attackPatternWrapper) {
      attackPatternComposers.add(attackPatternWrapper);
      List<AttackPattern> tempList = new ArrayList<>(payload.getAttackPatterns());
      tempList.add(attackPatternWrapper.get());
      payload.setAttackPatterns(tempList);
      return this;
    }

    public Composer withOutputParser(OutputParserComposer.Composer outputParserComposer) {
      outputParserComposers.add(outputParserComposer);
      Set<OutputParser> outputParsers = payload.getOutputParsers();
      outputParsers.add(outputParserComposer.get());
      this.payload.setOutputParsers(outputParsers);
      return this;
    }

    @Override
    public Composer persist() {
      documentComposer.ifPresent(DocumentComposer.Composer::persist);
      domainComposers.forEach(DomainComposer.Composer::persist);
      tagComposers.forEach(TagComposer.Composer::persist);
      attackPatternComposers.forEach(AttackPatternComposer.Composer::persist);
      payload.setId(null);
      payloadRepository.save(payload);
      return this;
    }

    @Override
    public Composer delete() {
      documentComposer.ifPresent(DocumentComposer.Composer::delete);
      tagComposers.forEach(TagComposer.Composer::delete);
      payloadRepository.delete(payload);
      domainComposers.forEach(DomainComposer.Composer::delete);
      detectionRemediationComposers.forEach(DetectionRemediationComposer.Composer::delete);
      attackPatternComposers.forEach(AttackPatternComposer.Composer::delete);
      return this;
    }

    @Override
    public Payload get() {
      return this.payload;
    }
  }

  public Composer forPayload(Payload payload) {
    this.generatedItems.add(payload);
    return new Composer(payload);
  }

  public PayloadInput forPayloadInput(Payload payload, List<String> attackPatternsIds) {

    PayloadInput input = new PayloadInput();
    input.setType(payload.getType());
    input.setName(payload.getName());
    input.setPlatforms(payload.getPlatforms());
    input.setDescription(payload.getDescription());
    input.setExecutionArch(payload.getExecutionArch());
    input.setArguments(payload.getArguments());
    input.setPrerequisites(payload.getPrerequisites());
    input.setCleanupExecutor(payload.getCleanupExecutor());
    input.setCleanupCommand(payload.getCleanupCommand());
    input.setTagIds(new ArrayList<>());
    input.setDetectionRemediations(new ArrayList<>());
    input.setOutputParsers(new HashSet<>());
    input.setAttackPatternsIds(attackPatternsIds);
    switch (payload) {
      case Command command -> {
        input.setExecutor(command.getExecutor());
        input.setContent(command.getContent());
      }
      case DnsResolution dnsResolution -> input.setHostname(dnsResolution.getHostname());
      case Executable executable -> executable.setExecutableFile(executable.getExecutableFile());
      case FileDrop fileDrop -> fileDrop.setFileDropFile(fileDrop.getFileDropFile());
      default -> {}
    }

    return input;
  }
}
