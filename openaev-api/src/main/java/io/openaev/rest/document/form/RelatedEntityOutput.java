package io.openaev.rest.document.form;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

@Builder
@JsonInclude(NON_NULL)
public record RelatedEntityOutput(String id, String name, String context) {}
