package io.openaev.rest.user.form.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.audit.AuditLogHash;
import io.openaev.database.audit.AuditLogIgnore;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserInfoInput {

  @JsonProperty("user_pgp_key")
  @AuditLogHash
  private String pgpKey;

  @JsonProperty("user_phone")
  @AuditLogIgnore
  private String phone;

  @JsonProperty("user_phone2")
  @AuditLogIgnore
  private String phone2;
}
