package io.openaev.datapack.local_fixtures;

import static io.openaev.utils.StringUtils.generateRandomColor;

import io.openaev.datapack.DataPack;
import io.openaev.rest.tag.TagService;
import io.openaev.rest.tag.form.TagCreateInput;
import io.openaev.service.DataPackService;
import org.springframework.stereotype.Component;

@Component
public class TestDataPack extends DataPack {
  public final String tagName = "test_datapack_tag_name";

  private final TagService tagService;

  public TestDataPack(DataPackService dataPackService, TagService tagService) {
    super(dataPackService);
    this.tagService = tagService;
  }

  @Override
  protected void doProcess() {
    // insert a new tag with static name
    TagCreateInput input = new TagCreateInput();
    input.setName(tagName);
    input.setColor(generateRandomColor());
    tagService.upsertTag(input);
  }
}
