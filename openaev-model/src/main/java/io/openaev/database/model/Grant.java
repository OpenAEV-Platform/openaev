package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.helper.MonoIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Setter
@Getter
@Entity
@Table(name = "grants")
public class Grant implements Base {

  public enum GRANT_RESOURCE_TYPE {
    SCENARIO,
    SIMULATION,
    ATOMIC_TESTING,
    PAYLOAD,
    UNKNOWN;
    ;

    public static GRANT_RESOURCE_TYPE fromRbacResourceType(ResourceType resourceType) {
      return switch (resourceType) {
        case SCENARIO -> SCENARIO;
        case SIMULATION -> SIMULATION;
        case ATOMIC_TESTING -> ATOMIC_TESTING;
        case PAYLOAD -> PAYLOAD;
        default -> UNKNOWN;
      };
    }
  }

  public enum GRANT_TYPE {
    OBSERVER(1),
    PLANNER(2),
    LAUNCHER(3);

    private final int priority;

    GRANT_TYPE(int priority) {
      this.priority = priority;
    }

    public int getPriority() {
      return priority;
    }

    // Get this grant type and all higher priority ones
    public List<GRANT_TYPE> andHigher() {
      return Arrays.stream(values())
          .filter(gt -> gt.getPriority() >= this.getPriority())
          .collect(Collectors.toList());
    }

    public static GRANT_TYPE fromRbacAction(Action action) {
      return switch (action) {
        case READ, SEARCH -> OBSERVER;
        case WRITE, DELETE, CREATE, DUPLICATE -> PLANNER;
        case LAUNCH -> LAUNCHER;
        default -> throw new IllegalArgumentException("No GRANT_TYPE for action: " + action);
      };
    }

    //  verify that priority is unique
    static {
      var priorities = new java.util.HashSet<Integer>();
      for (GRANT_TYPE type : GRANT_TYPE.values()) {
        if (!priorities.add(type.priority)) {
          throw new IllegalStateException("Duplicate priority found in GRANT_TYPE: " + type.name());
        }
      }
    }
  }

  @Id
  @Column(name = "grant_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("grant_id")
  @NotBlank
  private String id;

  @Column(name = "grant_name")
  @JsonProperty("grant_name")
  @Enumerated(EnumType.STRING)
  @NotNull
  private GRANT_TYPE name;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "grant_group")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @JsonProperty("grant_group")
  @Schema(type = "string")
  private Group group;

  @Column(name = "grant_resource")
  @JsonProperty("grant_resource")
  @Schema(type = "string")
  private String resourceId;

  @Enumerated(EnumType.STRING)
  @Column(name = "grant_resource_type")
  @JsonProperty("grant_resource_type")
  private GRANT_RESOURCE_TYPE grantResourceType = GRANT_RESOURCE_TYPE.UNKNOWN;

  @Override
  public boolean isUserHasAccess(User user) {
    return user.isAdmin();
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
