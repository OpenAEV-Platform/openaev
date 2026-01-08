package io.openaev.runner;

import io.openaev.database.model.Tag;
import io.openaev.database.repository.TagRepository;
import io.openaev.service.TagRuleService;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** InitTagRuleCommandLineRunner will make sure that the default tag and tag rule are created */
@Component
public class InitTagsAndRulesCommandLineRunner implements CommandLineRunner {
  private final TagRuleService tagRuleService;
  private final TagRepository tagRepository;

  public InitTagsAndRulesCommandLineRunner(
      @NotNull final TagRepository tagRepository, @NotNull final TagRuleService tagRuleService) {
    this.tagRepository = tagRepository;
    this.tagRuleService = tagRuleService;
  }

  @Override
  @Transactional
  public void run(String... args) {
    Set<Tag> wellKnownTags = createWellKnownTags();

    createRulesFromTags(wellKnownTags);
  }

  private Set<Tag> createWellKnownTags() {
    Set<Tag> wellKnownTags = new HashSet<>();
    for (Map.Entry<String, String> entry : Tag.WellKnown.entrySet()) {
      wellKnownTags.add(
          this.tagRepository
              .findByName(entry.getKey())
              .orElseGet(
                  () -> {
                    Tag tag = new Tag();
                    tag.setName(entry.getKey());
                    tag.setColor(entry.getValue());
                    return tagRepository.save(tag);
                  }));
    }
    return wellKnownTags;
  }

  private void createRulesFromTags(Set<Tag> tags) {
    for (Tag tag : tags) {
      this.tagRuleService
          .findByTagName(tag.getName())
          .ifPresentOrElse(
              tagRule -> {},
              () -> {
                this.tagRuleService.createTagRule(tag, new ArrayList<>(), true);
              });
    }
  }
}
