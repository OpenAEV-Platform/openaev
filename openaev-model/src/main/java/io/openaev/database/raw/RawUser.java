package io.openaev.database.raw;

import io.openaev.helper.UserHelper;
import java.util.List;

public interface RawUser {

  default String getUser_gravatar() {
    return UserHelper.getGravatar(getUser_email());
  }

  String getUser_id();

  String getUser_firstname();

  String getUser_lastname();

  String getUser_email();

  String getUser_phone();

  String getUser_organization();

  List<String> getUser_tags();

  List<String> getUser_groups();

  List<String> getUser_teams();

  default String computeName() {
    if (this.getUser_firstname() != null && this.getUser_lastname() != null) {
      return this.getUser_firstname() + " " + this.getUser_lastname();
    } else {
      return this.getUser_email();
    }
  }
}
