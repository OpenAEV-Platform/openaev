package io.openaev.service.attackpath.dto;

import java.util.List;

/**
 * A page of finding-widget drawer rows (issue 5048): the items for the current page and the total
 * count across the simulation, so the drawer can show a count and paginate on scroll.
 */
public record AttackPathFindingPageDTO(List<AttackPathFindingItemDTO> items, long total) {}
