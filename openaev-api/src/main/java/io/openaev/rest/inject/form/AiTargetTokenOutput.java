package io.openaev.rest.inject.form;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiTargetTokenOutput(@JsonProperty("value") String value) {}
