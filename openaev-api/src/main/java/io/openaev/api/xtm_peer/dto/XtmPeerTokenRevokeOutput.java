package io.openaev.api.xtm_peer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record XtmPeerTokenRevokeOutput(@JsonProperty("revoked") boolean revoked) {}
