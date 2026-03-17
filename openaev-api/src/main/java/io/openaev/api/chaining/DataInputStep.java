package io.openaev.api.chaining;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.openaev.rest.inject.form.InjectInput;

/**
 * Interface representing the data associated with a step.
 *
 * <p>This is a polymorphic type used to handle different kinds of step data. The concrete type is
 * determined by the JSON property {@code "type"}.
 *
 * <p>Currently supported implementations:
 *
 * <ul>
 *   <li>{@link InjectInput} with type name "inject"
 * </ul>
 *
 * <p>When serialized/deserialized with Jackson, the {@code type} property determines which concrete
 * implementation to use.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
    defaultImpl = InjectInput.class)
@JsonSubTypes({@JsonSubTypes.Type(value = InjectInput.class, name = "inject")})
public interface DataInputStep {}
