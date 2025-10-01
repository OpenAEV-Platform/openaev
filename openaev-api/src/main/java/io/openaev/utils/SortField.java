package io.openaev.utils;

import javax.annotation.Nullable;
import lombok.Builder;
import org.springframework.data.domain.Sort;

@Builder
public record SortField(
    String property, @Nullable String direction, @Nullable Sort.NullHandling nullHandling) {}
