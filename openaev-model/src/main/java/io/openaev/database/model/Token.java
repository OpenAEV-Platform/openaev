package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.helper.MonoIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Setter
@Getter
@Entity
@Table(name = "tokens")
public class Token implements Base {

  public static final String ADMIN_TOKEN_UUID = "0d17ce9a-f3a8-4c6d-9721-c98dc3dc023f";

  @Id
  @Column(name = "token_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("token_id")
  @NotBlank
  private String id;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "token_user")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @JsonProperty("token_user")
  @Schema(type = "string")
  private User user;

  @Column(name = "token_value")
  @JsonProperty("token_value")
  @NotBlank
  private String value;

  @Column(name = "token_created_at")
  @JsonProperty("token_created_at")
  @NotNull
  private Instant created;

  @Override
  public boolean isUserHasAccess(User user) {
    return this.user.equals(user);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || !Base.class.isAssignableFrom(o.getClass())) return false;
    Base base = (Base) o;
    return id.equals(base.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
