package io.openbas.database.model;

import static java.time.Instant.now;
import static java.util.Optional.ofNullable;
import static lombok.AccessLevel.NONE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openbas.annotation.Queryable;
import io.openbas.database.audit.ModelBaseListener;
import io.openbas.helper.*;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Entity
@Table(name = "users")
@EntityListeners(ModelBaseListener.class)
@NamedEntityGraphs({
  @NamedEntityGraph(
      name = "Player.tags-organization",
      attributeNodes = {@NamedAttributeNode("tags"), @NamedAttributeNode("organization")})
})
public class User implements Base {

  public static final String ADMIN_UUID = "89206193-dbfb-4513-a186-d72c037dda4c";
  public static final String ADMIN_FIRSTNAME = "admin";
  public static final String ADMIN_LASTNAME = "openbas";
  public static final String ROLE_ADMIN = "ROLE_ADMIN";
  public static final String ROLE_USER = "ROLE_USER";
  public static final String THEME_DEFAULT = "default";
  public static final String LANG_AUTO = "auto";

  public static final List<String> ALL_ROLES = Arrays.asList(ROLE_ADMIN, ROLE_USER);

  @Setter
  @Id
  @Column(name = "user_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("user_id")
  @NotBlank
  @Schema(description = "User ID")
  private String id;

  @Setter
  @Column(name = "user_firstname")
  @JsonProperty("user_firstname")
  @Queryable(filterable = true, searchable = true, sortable = true)
  @Schema(description = "First name of the user")
  private String firstname;

  @Setter
  @Column(name = "user_lastname")
  @JsonProperty("user_lastname")
  @Queryable(filterable = true, searchable = true, sortable = true)
  @Schema(description = "Last name of the user")
  private String lastname;

  @Getter(NONE)
  @Setter
  @Column(name = "user_lang")
  @JsonProperty("user_lang")
  @Schema(description = "Language of the user")
  private String lang = LANG_AUTO;

  public String getLang() {
    return ofNullable(this.lang).orElse(LANG_AUTO);
  }

  @Getter(NONE)
  @Setter
  @Column(name = "user_theme")
  @JsonProperty("user_theme")
  @Schema(description = "Theme of the user")
  private String theme = THEME_DEFAULT;

  public String getTheme() {
    return ofNullable(this.theme).orElse(THEME_DEFAULT);
  }

  @Getter(NONE)
  @Setter(NONE)
  @Column(name = "user_email")
  @JsonProperty("user_email")
  @Queryable(filterable = true, searchable = true, sortable = true)
  @NotBlank
  @Schema(description = "Email of the user")
  private String email;

  public void setEmail(final String email) {
    this.email =
        ofNullable(email)
            .map(String::toLowerCase)
            .orElseThrow(() -> new IllegalArgumentException("Email can't be null"));
  }

  public String getEmail() {
    return ofNullable(this.email).map(String::toLowerCase).orElse(null);
  }

  @Setter
  @Column(name = "user_phone")
  @JsonProperty("user_phone")
  @Schema(description = "Phone number of the user")
  private String phone;

  @Setter
  @Column(name = "user_phone2")
  @JsonProperty("user_phone2")
  @Schema(description = "Secondary phone number of the user")
  private String phone2;

  @Setter
  @Column(name = "user_pgp_key")
  @JsonProperty("user_pgp_key")
  @Schema(description = "PGP key of the user")
  private String pgpKey;

  @Setter
  @Column(name = "user_status")
  @JsonProperty("user_status")
  @NotNull
  @Schema(description = "Status of the user")
  private Short status = 0;

  @Setter
  @Column(name = "user_password")
  @JsonIgnore
  private String password;

  @Setter
  @Column(name = "user_created_at")
  @JsonProperty("user_created_at")
  @NotNull
  @Schema(description = "Creation date of the user", accessMode = Schema.AccessMode.READ_ONLY)
  private Instant createdAt = now();

  @Setter
  @Column(name = "user_updated_at")
  @JsonProperty("user_updated_at")
  @NotNull
  @Schema(description = "Update date of the user", accessMode = Schema.AccessMode.READ_ONLY)
  private Instant updatedAt = now();

  @Setter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_organization")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @JsonProperty("user_organization")
  @Queryable(dynamicValues = true, filterable = true, sortable = true, path = "organization.id")
  @Schema(description = "Organization ID of the user", type = "string")
  private Organization organization;

  @Setter
  @Column(name = "user_admin")
  @JsonProperty("user_admin")
  @Queryable(filterable = true, sortable = true)
  @Schema(description = "True if the user is admin")
  private boolean admin = false;

  @Setter
  @Column(name = "user_country")
  @JsonProperty("user_country")
  @Schema(description = "Country of the user")
  private String country;

  @Setter
  @Column(name = "user_city")
  @JsonProperty("user_city")
  @Schema(description = "City of the user")
  private String city;

  // -- RELATIONS --

  @ArraySchema(schema = @Schema(description = "Group IDs of the user", type = "string"))
  @Setter
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "users_groups",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "group_id"))
  @JsonSerialize(using = MultiIdListDeserializer.class)
  @JsonProperty("user_groups")
  private List<Group> groups = new ArrayList<>();

  @ArraySchema(schema = @Schema(description = "Team IDs of the user", type = "string"))
  @Setter
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "users_teams",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "team_id"))
  @JsonSerialize(using = MultiIdListDeserializer.class)
  @JsonProperty("user_teams")
  @Queryable(dynamicValues = true, filterable = true, sortable = true, path = "teams.id")
  private List<Team> teams = new ArrayList<>();

  @ArraySchema(schema = @Schema(description = "Tag IDs of the user", type = "string"))
  @Setter
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "users_tags",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "tag_id"))
  @JsonSerialize(using = MultiIdSetDeserializer.class)
  @JsonProperty("user_tags")
  @Queryable(dynamicValues = true, filterable = true, sortable = true, path = "tags.id")
  private Set<Tag> tags = new HashSet<>();

  @ArraySchema(schema = @Schema(description = "Communication IDs of the user", type = "string"))
  @Setter
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "communications_users",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "communication_id"))
  @JsonSerialize(using = MultiIdListDeserializer.class)
  @JsonProperty("user_communications")
  private List<Communication> communications = new ArrayList<>();

  @ArraySchema(
      schema =
          @Schema(
              description = "List of 3-tuple linking simulation IDs and team IDs to this user ID",
              type = "string"))
  @OneToMany(
      mappedBy = "user",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @JsonProperty("team_exercises_users")
  @JsonSerialize(using = MultiModelDeserializer.class)
  private List<ExerciseTeamUser> exerciseTeamUsers = new ArrayList<>();

  @Setter
  @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
  @JsonIgnore
  private List<Token> tokens = new ArrayList<>();

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.USER;

  @Setter
  @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
  @JsonIgnore
  private List<ComcheckStatus> comcheckStatuses = new ArrayList<>();

  @JsonProperty("user_gravatar")
  @Schema(description = "Gravatar of the user")
  public String getGravatar() {
    return UserHelper.getGravatar(getEmail());
  }

  @JsonIgnore
  public String getName() {
    return getFirstname() + " " + getLastname();
  }

  @JsonIgnore
  public String getNameOrEmail() {
    if (getFirstname() != null && getLastname() != null) {
      return getName();
    } else {
      return getEmail();
    }
  }

  // -- RBAC --

  @JsonProperty("user_is_planner")
  @Schema(description = "True if the user is planner")
  public boolean isPlanner() {
    return isAdmin()
        || getGroups().stream()
            .flatMap(group -> group.getGrants().stream())
            .anyMatch(grant -> Grant.GRANT_TYPE.PLANNER.equals(grant.getName()));
  }

  @JsonProperty("user_is_observer")
  @Schema(description = "True if the user is observer")
  public boolean isObserver() {
    return isAdmin() || getGroups().stream().mapToLong(group -> group.getGrants().size()).sum() > 0;
  }

  @JsonProperty("user_is_manager")
  @Schema(description = "True if the user is manager")
  public boolean isManager() {
    return isPlanner() || isObserver();
  }

  @JsonProperty("user_is_player")
  @Schema(description = "True if the user is player")
  public boolean isPlayer() {
    return isAdmin() || isPlanner() || isObserver() || !getTeams().isEmpty();
  }

  @JsonProperty("user_is_external")
  @Schema(description = "True if the user is external")
  public boolean isExternal() {
    return this.getId().equals(ADMIN_UUID);
  }

  @JsonProperty("user_is_only_player")
  @Schema(description = "True if the user is only a player")
  public boolean isOnlyPlayer() {
    return !isAdmin() && !isManager();
  }

  @JsonProperty("user_is_admin_or_bypass")
  @Schema(description = "True if the user is admin or has bypass capa")
  public boolean isAdminOrBypass() {
    return isAdmin() || getCapabilities().contains(Capability.BYPASS);
  }

  @JsonProperty("user_capabilities")
  @Enumerated(EnumType.STRING)
  public Set<Capability> getCapabilities() {
    return getGroups().stream()
        .flatMap(group -> group.getRoles().stream())
        .flatMap(role -> role.getCapabilities().stream())
        .collect(Collectors.toSet());
  }

  @JsonProperty("user_grants")
  public Map<String, String> getGrants() {
    return getGroups().stream()
        .flatMap(group -> group.getGrants().stream())
        .filter(grant -> grant.getResourceId() != null)
        .collect(
            Collectors.toMap(
                Grant::getResourceId,
                grant -> grant.getName().toString(),
                (grantA, grantB) ->
                    Grant.GRANT_TYPE.valueOf(grantA).getPriority()
                            >= Grant.GRANT_TYPE.valueOf(grantB).getPriority()
                        ? grantA
                        : grantB));
  }

  @Override
  public boolean isUserHasAccess(User user) {
    return user.isAdmin() || user.getId().equals(getId());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || !Base.class.isAssignableFrom(o.getClass())) {
      return false;
    }
    Base base = (Base) o;
    return this.id.equals(base.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.id);
  }

  @Override
  public String toString() {
    return this.email;
  }
}
