package io.openaev.database.raw;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/** Projection of {@link io.openaev.database.repository.UserRepository#rawIdentities} */
public interface RawUserIdentity {

  String getUser_id();

  String getUser_email();

  String getUser_firstname();

  String getUser_lastname();

  /** Same rule as {@link io.openaev.database.model.User#getNameOrEmail()}. */
  default String getUser_name() {
    return isNotBlank(getUser_firstname()) && isNotBlank(getUser_lastname())
        ? getUser_firstname() + " " + getUser_lastname()
        : getUser_email();
  }
}
