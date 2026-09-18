package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.audit.ModelBaseListener;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.Instant;

@Entity
@Data
@Table(name = "user_email_change_confirmations")
@EntityListeners(ModelBaseListener.class)
public class UserEmailChangeConfirmation implements Base {
    @Id
    @Column(name = "user_id", nullable = false)
    @NotBlank
    @JsonProperty("user_id")
    @Schema(description = "User ID")
    private String id;

    @Column(name = "confirmation_code", nullable = false)
    @NotBlank
    @JsonProperty("confirmation_code")
    @Schema(description = "Confirmation code")
    private String code;

    @Column(name = "confirmation_email", nullable = false)
    @NotBlank
    @JsonProperty("confirmation_email")
    @Schema(description = "New candidate email address")
    private String email;

    @Column(name = "confirmation_valid_until", nullable = false)
    @NotBlank
    @JsonProperty("confirmation_valid_until")
    @Schema(description = "Valid until this date and time")
    private Instant validUntil;
}
