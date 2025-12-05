package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "steps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(ModelBaseListener.class)
public class Step {

    @Id
    @Column(name = "step_id")
    private String id;

    @Column(name = "step_action_class")
    private STEP_ACTION_CLASS stepAction;
    //action_class (Enum)

    @Type(JsonType.class)
    @JsonProperty("step_output")
    @Column(name="output", columnDefinition = "jsonb")
    private Map<String, Object> output;

    @Type(JsonType.class)
    @JsonProperty("step_input")
    @Column(name="input", columnDefinition = "jsonb")
    private Map<String, Object> input;

    @Type(JsonType.class)
    @JsonProperty("step_data")
    @Column(name="data", columnDefinition = "jsonb")
    private Map<String, Object> data;

    @Column(name="step_limit_execution")
    int limit_execution;

    @Column(name = "step_status")
    private STEP_STATUS status;

    @Min(1)
    @Column(name="step_order")
    int order;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
