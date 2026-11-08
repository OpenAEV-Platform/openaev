package io.openaev.database.model;

import static java.time.Instant.now;
import static java.util.Optional.ofNullable;
import static lombok.AccessLevel.NONE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import io.openaev.annotation.Queryable;
import io.openaev.context.TenantContext;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.*;
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
import org.hibernate.Hibernate;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Entity
@Table(name = "users")
@EntityListeners(ModelBaseListener.class)
@NamedEntityGraphs({
  @NamedEntityGraph(
      name = "Player.tags-organization",
      attributeNodes = {@NamedAttributeNode("tags"), @NamedAttributeNode("organizations")})
})
public class User implements Base {

  public static final String ADMIN_UUID = "89206193-dbfb-4513-a186-d72c037dda4c";
  public static final String ADMIN_FIRSTNAME = "admin";
  public static final String ADMIN_LASTNAME = "openaev";
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

  @Column(name = "user_home_dashboard")
  @JsonProperty("user_home_dashboard")
  @Schema(
      description =
          "Preferred home dashboard of the user; overrides the tenant home dashboard setting")
  private String homeDashboard;

  // The UI sends an empty string to mean "platform default"; normalize it to null so the FK to
  // custom_dashboards is never violated (profile updates copy this value via BeanUtils).
  public void setHomeDashboard(final String homeDashboard) {
    this.homeDashboard =
        ofNullable(homeDashboard).map(String::trim).filter(v -> !v.isEmpty()).orElse(null);
  }

  @Setter
  @Type(StringArrayType.class)
  @Column(name = "user_unsubscribed_news_feed_types", columnDefinition = "text[]")
  @JsonIgnore
  private String[] unsubscribedNewsFeedTypes = new String[0];

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

  /**
   * A user is platform-level while an organization belongs to one tenant, so a user holds at most
   * one organization per tenant.
   *
   * <p>Inverse side of {@code users_organizations}: Hibernate never writes it, this set is only a
   * read view. A flush of the owning collection would delete every row of the user as soon as the
   * organizations in scope are all removed, wiping the ones of the tenants out of scope. The table
   * is written by {@link io.openaev.database.repository.UserRepository#assignOrganization}.
   */
  @ManyToMany(mappedBy = "users", fetch = FetchType.LAZY)
  @Fetch(FetchMode.SUBSELECT)
  @JsonIgnore
  @Getter(NONE)
  private Set<Organization> organizations = new HashSet<>();

  /**
   * Serialized as {@code user_organization}: the single organization of the user in the current
   * tenant, or {@code null} when the user has none there (or when the organization is out of the
   * caller's tenant scope).
   */
  @JsonProperty("user_organization")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Queryable(dynamicValues = true, filterable = true, path = "organizations.id")
  @Schema(description = "Organization ID of the user", type = "string")
  public Organization getOrganization() {
    return organizationOfTenant(
        organizationTenantId != null ? organizationTenantId : TenantContext.getCurrentTenant());
  }

  /**
   * The tenant whose organization {@link #getOrganization()} answers for, when it is not the
   * ambient one. Only ever set on a {@link #responseViewFor response view}, never on an entity the
   * persistence context or a lifecycle event holds. Never persisted, never serialized.
   */
  @Transient
  @JsonIgnore
  @Getter(NONE)
  private String organizationTenantId;

  /**
   * A detached copy of the user for the response of a request that works on the given tenant: its
   * {@link #getOrganization()}, so the {@code user_organization} of the response, answers for that
   * tenant instead of the ambient one, which is the default tenant when the request names another
   * in {@code X-Tenant-Ids}. The copy shares the loaded associations of this user and is never
   * persisted. The entity itself is left as it is: a lifecycle event keeps it and serializes it
   * later, for the consumers of other tenants, which must not see the organization of this one.
   *
   * @param tenantId the tenant the request works on
   */
  public User responseViewFor(final String tenantId) {
    User view = new User();
    view.id = id;
    view.firstname = firstname;
    view.lastname = lastname;
    view.lang = lang;
    view.theme = theme;
    view.homeDashboard = homeDashboard;
    view.unsubscribedNewsFeedTypes = unsubscribedNewsFeedTypes;
    view.email = email;
    view.phone = phone;
    view.phone2 = phone2;
    view.pgpKey = pgpKey;
    view.status = status;
    view.password = password;
    view.createdAt = createdAt;
    view.updatedAt = updatedAt;
    view.organizations = organizations;
    view.admin = admin;
    view.country = country;
    view.city = city;
    view.groups = groups;
    view.teams = teams;
    view.tags = tags;
    view.communications = communications;
    view.exerciseTeamUsers = exerciseTeamUsers;
    view.tokens = tokens;
    view.tenants = tenants;
    view.comcheckStatuses = comcheckStatuses;
    view.organizationTenantId = tenantId;
    return view;
  }

  /**
   * Loads the organizations of the user. Call it inside the scoped transaction of an endpoint that
   * returns the raw user: {@code user_organization} is otherwise read after the commit, where the
   * tenant scope is gone and a lazy load of the v2-active {@code organizations} fails closed to
   * nothing.
   */
  public void loadOrganizations() {
    Hibernate.initialize(organizations);
  }

  /**
   * Returns the organization the user holds in the given tenant, or {@code null}.
   *
   * @param tenantId the tenant the organization must belong to
   */
  public Organization organizationOfTenant(final String tenantId) {
    if (tenantId == null) {
      return null;
    }
    return organizations.stream()
        .filter(org -> org.getTenant() != null && tenantId.equals(org.getTenant().getId()))
        .findFirst()
        .orElse(null);
  }

  /**
   * Updates the in-memory view of the organization of the user in the given tenant, so the entity
   * answers {@link #getOrganization()} correctly for the rest of the request. It does not persist
   * anything: use {@link io.openaev.database.repository.UserRepository#assignOrganization}, which
   * calls it. Organizations of the other tenants are left untouched.
   *
   * @param tenantId the tenant whose organization is replaced
   * @param organization the new organization of that tenant, or {@code null} to clear it
   */
  public void setOrganizationForTenant(final String tenantId, final Organization organization) {
    Objects.requireNonNull(tenantId, "tenantId must not be null");
    organizations.removeIf(
        org -> org.getTenant() != null && tenantId.equals(org.getTenant().getId()));
    if (organization != null) {
      organizations.add(organization);
    }
  }

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

  @Setter
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "users_groups",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "group_id"))
  @Fetch(FetchMode.SUBSELECT)
  @JsonIgnore
  @Getter(NONE)
  private List<Group> groups = new ArrayList<>();

  /**
   * Returns the raw, unscoped group collection for internal mutations only (SSO sync, bidirectional
   * cleanup before delete). Never serialize this — use {@link #getScopedGroups()} for API
   * responses.
   */
  public List<Group> getUnscopedGroups() {
    return groups;
  }

  /**
   * Serialized as {@code user_groups} in JSON: filtered to the current tenant context (tenant
   * groups belonging to the current tenant + platform-level groups with {@code tenant IS NULL}).
   */
  @ArraySchema(
      schema = @Schema(description = "Group IDs of the user", implementation = String.class))
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("user_groups")
  public List<Group> getScopedGroups() {
    return scopedGroups();
  }

  @ArraySchema(
      schema = @Schema(description = "Team IDs of the user", implementation = String.class))
  @Setter
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "users_teams",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "team_id"))
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("user_teams")
  @Queryable(dynamicValues = true, filterable = true, path = "teams.id")
  private List<Team> teams = new ArrayList<>();

  /**
   * Owning side of {@code users_tags}, but a user is platform-level and its tags may belong to
   * several tenants, so the collection only holds the ones in the scope of the transaction. The
   * tags of an existing user are never written through it: a flush of this collection deletes every
   * row of the user as soon as it is empty, the links to the tags out of scope included. The links
   * of an existing user are written by {@code UserService#replaceTags}; the setter is for a user
   * that is not saved yet, and {@link Immutable} makes any later change of the loaded collection
   * fail at flush instead of deleting links.
   */
  @ArraySchema(schema = @Schema(description = "Tag IDs of the user", implementation = String.class))
  @Setter
  @Immutable
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "users_tags",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "tag_id"))
  @JsonSerialize(using = MultiIdSetSerializer.class)
  @JsonProperty("user_tags")
  @Queryable(dynamicValues = true, filterable = true, path = "tags.id")
  private Set<Tag> tags = new HashSet<>();

  @ArraySchema(
      schema =
          @Schema(description = "Communication IDs of the user", implementation = String.class))
  @Setter
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "communications_users",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "communication_id"))
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("user_communications")
  private List<Communication> communications = new ArrayList<>();

  @ArraySchema(
      schema =
          @Schema(
              description = "List of 3-tuple linking simulation IDs and team IDs to this user ID",
              implementation = String.class))
  @OneToMany(
      mappedBy = "user",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @JsonProperty("team_exercises_users")
  @JsonSerialize(using = MultiModelSerializer.class)
  private List<ExerciseTeamUser> exerciseTeamUsers = new ArrayList<>();

  @Setter
  @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
  @JsonIgnore
  private List<Token> tokens = new ArrayList<>();

  @Setter
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "users_tenants",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "tenant_id"))
  @JsonIgnore
  private List<Tenant> tenants = new ArrayList<>();

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
    if (getFirstname() != null
        && !getFirstname().isBlank()
        && getLastname() != null
        && !getLastname().isBlank()) {
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
        || scopedGroups().stream()
            .flatMap(group -> group.getGrants().stream())
            .anyMatch(grant -> Grant.GRANT_TYPE.PLANNER.equals(grant.getName()));
  }

  @JsonProperty("user_is_observer")
  @Schema(description = "True if the user is observer")
  public boolean isObserver() {
    return isAdmin()
        || scopedGroups().stream().mapToLong(group -> group.getGrants().size()).sum() > 0;
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

  /**
   * Returns true if the user has BYPASS via a platform-level group (tenant IS NULL). A platform
   * BYPASS grants access to both platform-only and tenant-scoped resources.
   */
  public boolean hasPlatformBypass() {
    return platformGroups().stream()
        .flatMap(group -> group.getRoles().stream())
        .flatMap(role -> role.getCapabilities().stream())
        .anyMatch(Capability.BYPASS::equals);
  }

  /**
   * Returns true if the user has BYPASS via a tenant-level group. A tenant BYPASS only grants
   * access to tenant-scoped resources, not platform-only ones.
   */
  public boolean hasTenantBypass() {
    String currentTenant = TenantContext.getCurrentTenant();
    if (currentTenant == null) {
      return false;
    }
    return scopedGroups().stream()
        .filter(
            group -> group.getTenant() != null && currentTenant.equals(group.getTenant().getId()))
        .flatMap(group -> group.getRoles().stream())
        .flatMap(role -> role.getCapabilities().stream())
        .anyMatch(Capability.BYPASS::equals);
  }

  /** Returns only platform-level groups (tenant IS NULL). */
  private List<Group> platformGroups() {
    return getUnscopedGroups().stream().filter(group -> group.getTenant() == null).toList();
  }

  /**
   * Returns the groups visible in the current tenant context: groups belonging to the current
   * tenant plus platform-level groups (tenant IS NULL).
   *
   * <p>A request without an explicit tenant context - token/bearer API clients (Postman, httpx, the
   * integrations) and Community Edition, where multi-tenancy is disabled - still operates on the
   * default tenant. {@link TenantContext#getCurrentTenant()} falls back to the default tenant when
   * none is set, so such requests resolve default-tenant groups plus platform groups. Without this,
   * {@code getCapabilities()} was empty for those requests and {@code AccessControlAspect} denied
   * them with 403 (issues #6331 / #6332).
   */
  private List<Group> scopedGroups() {
    String currentTenant = scopedTenantId();
    return getUnscopedGroups().stream()
        .filter(
            group ->
                group.getTenant() == null
                    || (currentTenant != null && currentTenant.equals(group.getTenant().getId())))
        .toList();
  }

  /**
   * The tenant whose groups count for this user in the current request, next to platform groups.
   * Capabilities, {@code user_grants} and the grant checks of the backend all resolve against it,
   * so a grant applies in the tenant of the group that holds it, and everywhere for a platform
   * group. Falls back to the default tenant, see {@link #scopedGroups()}.
   */
  public String scopedTenantId() {
    return TenantContext.getCurrentTenant();
  }

  @JsonProperty("user_capabilities")
  @Enumerated(EnumType.STRING)
  public Set<Capability> getCapabilities() {
    return capabilitiesOf(scopedGroups());
  }

  @JsonProperty("user_grants")
  public Map<String, String> getGrants() {
    return scopedGroups().stream()
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

  // -- UTILS --

  public boolean hasBypassIn(CapabilityScope scope) {
    return scope == CapabilityScope.PLATFORM ? hasPlatformBypass() : hasTenantBypass();
  }

  /**
   * Returns the capabilities the user actually holds <em>within</em> the given scope, used to
   * decide what they are allowed to grant to others.
   */
  public Set<Capability> getCapabilities(CapabilityScope scope) {
    return capabilitiesOf(scope == CapabilityScope.PLATFORM ? platformGroups() : scopedGroups());
  }

  /**
   * Shared by both {@code getCapabilities()} overloads: the caller picks the groups, this expands
   * them. BYPASS is expanded rather than returned, so it never leaks into a capability set.
   */
  private static Set<Capability> capabilitiesOf(List<Group> groups) {
    Set<Capability> capabilities = new HashSet<>();
    for (Group group : groups) {
      boolean isPlatformGroup = group.getTenant() == null;
      for (Role role : group.getRoles()) {
        for (Capability cap : role.getCapabilities()) {
          if (cap == Capability.BYPASS) {
            // Expand BYPASS into concrete capabilities matching the group scope
            capabilities.addAll(
                isPlatformGroup ? Capability.allPlatformScoped() : Capability.allTenantScoped());
          } else {
            capabilities.add(cap);
          }
        }
      }
    }
    return capabilities;
  }
}
