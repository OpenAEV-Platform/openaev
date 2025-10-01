package io.openaev.database.raw;

import static java.util.Optional.ofNullable;

import io.openaev.database.model.Organization;
import io.openaev.database.model.Tag;
import io.openaev.database.model.User;
import java.util.List;
import lombok.Data;

@Data
public class RawPaginationPlayer {

  String user_id;
  String user_email;
  String user_firstname;
  String user_lastname;
  String user_organization;
  String user_phone;
  String user_phone2;
  String user_country;
  String user_pgp_key;
  List<String> user_tags;

  public RawPaginationPlayer(final User user) {
    this.user_id = user.getId();
    this.user_email = user.getEmail();
    this.user_firstname = user.getFirstname();
    this.user_lastname = user.getLastname();
    this.user_phone = user.getPhone();
    this.user_phone2 = user.getPhone2();
    this.user_country = user.getCountry();
    this.user_pgp_key = user.getPgpKey();
    this.user_organization =
        ofNullable(user.getOrganization()).map(Organization::getId).orElse(null);
    this.user_tags = user.getTags().stream().map(Tag::getId).toList();
  }
}
