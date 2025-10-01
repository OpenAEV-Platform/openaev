package io.openaev.utils.fixtures;

import io.openaev.database.model.Tag;

public class TagFixture {
  public static final String TAG_ID = "id";
  public static final String TAG_NAME = "tag";

  public static Tag getTag() {
    Tag tag = new Tag();
    tag.setId(TAG_ID);
    tag.setName(TAG_NAME);
    tag.setColor("#FFFFFF");
    return tag;
  }

  public static Tag getTagWithText(String text) {
    Tag tag = new Tag();
    tag.setName(text);
    tag.setColor("#FFFFFF");
    return tag;
  }

  public static Tag getTagWithTextAndColour(String text, String colour) {
    Tag tag = new Tag();
    tag.setName(text);
    tag.setColor(colour);
    return tag;
  }

  public static Tag getTag(final String id) {
    Tag tag = new Tag();
    tag.setId(id);
    tag.setName(TAG_NAME);
    tag.setColor("#FFFFFF");
    return tag;
  }
}
