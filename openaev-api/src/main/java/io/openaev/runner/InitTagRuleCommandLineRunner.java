package io.openaev.runner;

import static io.openaev.database.model.User.*;

import io.openaev.database.model.Tag;
import io.openaev.database.model.TagRule;
import io.openaev.database.repository.TagRepository;
import io.openaev.database.repository.TagRuleRepository;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** InitTagRuleCommandLineRunner will make sure that the default tag and tag rule are created */
@Component
public class InitTagRuleCommandLineRunner implements CommandLineRunner {

  private static final String OPEN_CTI_TAG_NAME = "opencti";

  private final TagRuleRepository tagRuleRepository;
  private final TagRepository tagRepository;

  public InitTagRuleCommandLineRunner(
      @NotNull final TagRuleRepository tagRuleRepository,
      @NotNull final TagRepository tagRepository) {
    this.tagRuleRepository = tagRuleRepository;
    this.tagRepository = tagRepository;
  }

  @Override
  @Transactional
  public void run(String... args) {
    Tag openCtiTag =
        this.tagRepository
            .findByName(OPEN_CTI_TAG_NAME)
            .orElseGet(() -> tagRepository.save(createOpenCtiTag()));

    List<TagRule> openCtiTagRules =
        this.tagRuleRepository.findByTagNames(List.of(OPEN_CTI_TAG_NAME));
    if (openCtiTagRules.isEmpty()) {
      tagRuleRepository.save(createOpenCtiTagRule(openCtiTag));
    }
  }

  private Tag createOpenCtiTag() {
    Tag tag = new Tag();
    tag.setColor("#0fbcff");
    tag.setName(OPEN_CTI_TAG_NAME);
    return tag;
  }

  private TagRule createOpenCtiTagRule(Tag openCtiTag) {
    TagRule tagRule = new TagRule();
    tagRule.setTag(openCtiTag);
    return tagRule;
  }
}
