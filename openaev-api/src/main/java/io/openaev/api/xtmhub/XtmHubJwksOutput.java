package io.openaev.api.xtmhub;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.xtmhub.XtmHubPublicJwk;
import java.util.List;

/** JWK Set (RFC 7517) of the instance's XTM Hub key. */
public record XtmHubJwksOutput(@JsonProperty("keys") List<XtmHubPublicJwk> keys) {}
