package io.openaev.rest.user.form.player;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.audit.AuditLogHash;
import io.openaev.database.audit.AuditLogIgnore;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class PlayerOutput {

  @JsonProperty("user_id")
  @NotBlank
  private String id;

  @JsonProperty("user_firstname")
  @AuditLogIgnore
  private String firstname;

  @JsonProperty("user_lastname")
  @AuditLogIgnore
  private String lastname;

  @JsonProperty("user_email")
  @NotBlank
  @AuditLogIgnore
  private String email;

  @JsonProperty("user_organization")
  private String organization;

  @JsonProperty("user_country")
  @AuditLogIgnore
  private String country;

  @JsonProperty("user_phone")
  @AuditLogIgnore
  private String phone;

  @JsonProperty("user_phone2")
  @AuditLogIgnore
  private String phone2;

  @JsonProperty("user_pgp_key")
  @AuditLogHash
  private String pgpKey;

  @JsonProperty("user_tags")
  private Set<String> tags;

  // Platform administrators are protected accounts: the persons UI disables
  // deletion (and restricts update) for them, so the flag must flow to the list.
  @JsonProperty("user_admin")
  private boolean admin;
}
