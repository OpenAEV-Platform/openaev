package io.openaev.database.model.attackpath.projection;

/**
 * Flat projection for the endpoint expand read: the (type, value) of a finding on one endpoint. No
 * join and no execution link — expand is a single indexed read on {@code attackpath_finding}.
 */
public record AttackPathEndpointFindingRow(String type, String value) {}
