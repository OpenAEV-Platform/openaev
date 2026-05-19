package io.openaev.api.chaining;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.openaev.rest.inject.output.InjectOutput;

/**
 * Interface representing the data associated with a step.
 *
 * <p>This is a polymorphic type used to handle different kinds of step data. The concrete type is
 * determined by the JSON property {@code "type"}.
 *
 * <p>Currently supported implementations:
 *
 * <ul>
 *   <li>{@link InjectOutput} with type name "inject"
 * </ul>
 *
 * <p>When serialized/deserialized with Jackson, the {@code type} property determines which concrete
 * implementation to use.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
    defaultImpl = InjectOutput.class)
@JsonSubTypes({@JsonSubTypes.Type(value = InjectOutput.class, name = "inject")})
public interface DataOutputStep {}
