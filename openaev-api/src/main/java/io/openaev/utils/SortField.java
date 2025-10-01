package io.openaev.utils;

import javax.annotation.Nullable;
import lombok.Builder;

@Builder
public record SortField(String property, @Nullable String direction) {}
