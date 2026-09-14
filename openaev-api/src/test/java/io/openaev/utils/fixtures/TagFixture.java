package io.openaev.utils.fixtures;

import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;

public class TagFixture {
  public static final String TAG_ID = "id";
  public static final String TAG_NAME = "tag";

  private static void assignCurrentTenant(Tag tag) {
    tag.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
  }

  public static Tag getTag() {
    Tag tag = new Tag();
    tag.setId(TAG_ID);
    tag.setName(TAG_NAME);
    tag.setColor("#FFFFFF");
    assignCurrentTenant(tag);
    return tag;
  }

  public static Tag getTagNoId() {
    Tag tag = new Tag();
    tag.setName(TAG_NAME);
    tag.setColor("#FFFFFF");
    assignCurrentTenant(tag);
    return tag;
  }

  public static Tag getTagWithText(String text) {
    Tag tag = new Tag();
    tag.setName(text);
    tag.setColor("#FFFFFF");
    assignCurrentTenant(tag);
    return tag;
  }

  public static Tag getTagWithTextAndColour(String text, String colour) {
    Tag tag = new Tag();
    tag.setName(text);
    tag.setColor(colour);
    assignCurrentTenant(tag);
    return tag;
  }

  public static Tag getTag(final String id) {
    Tag tag = new Tag();
    tag.setId(id);
    tag.setName(TAG_NAME);
    tag.setColor("#FFFFFF");
    assignCurrentTenant(tag);
    return tag;
  }
}
