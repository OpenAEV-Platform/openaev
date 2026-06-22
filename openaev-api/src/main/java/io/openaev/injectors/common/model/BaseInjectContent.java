package io.openaev.injectors.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.model.inject.form.Expectation;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class BaseInjectContent {

    @JsonProperty("expectations")
    private List<Expectation> expectations = new ArrayList<>();

}