package io.openaev.search;

import jakarta.validation.constraints.NotBlank;

public record FullTextSearchCountResult(@NotBlank String clazz, @NotBlank long count) {}
