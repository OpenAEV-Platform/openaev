package io.openaev.search;

import jakarta.validation.constraints.NotBlank;
import java.util.Set;

public record FullTextSearchResult(
    @NotBlank String id,
    @NotBlank String name,
    String description,
    Set<String> tags,
    @NotBlank String clazz) {}
