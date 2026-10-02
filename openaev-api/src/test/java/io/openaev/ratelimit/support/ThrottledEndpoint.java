package io.openaev.ratelimit.support;

import io.openaev.annotation.NoTenantScope;
import io.openaev.aop.AccessControl;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.ratelimit.aop.RateLimit;
import io.openaev.rest.helper.RestBehavior;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ThrottledEndpoint extends RestBehavior {
  /** Path prefix for rate limit test endpoints */
  public static final String RATELIMIT_ENDPOINT_PATH_PREFIX = "/ratelimit-tests";

  /** Assertion helper for simple get endpoints */
  public static final String SIMPLE_GET_RESPONSE_BODY = "Got the GET";

  /** Helper constant for setting up looped calls and expect a throttling */
  public static final long THROTTLED_ENDPOINT_AUTHED_RPS = 25L;

  // endpoint paths
  public static final String AUTHED_SIMPLE_GET = RATELIMIT_ENDPOINT_PATH_PREFIX + "/simple-get";
  public static final String AUTHED_SIMPLE_GET_SKIP_RBAC =
      RATELIMIT_ENDPOINT_PATH_PREFIX + "/simple-get-skip-rbac";
  // this endpoint piggybacks on the AppSecurityConfig setting of `permitAll()` on "/api/reset/**"
  public static final String OPEN_SIMPLE_GET = "/api/reset/ratelimit-integration-test";

  @GetMapping(AUTHED_SIMPLE_GET)
  @Transactional
  @NoTenantScope
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.JOB)
  @RateLimit(rps = THROTTLED_ENDPOINT_AUTHED_RPS)
  public String simpleGet() {
    return SIMPLE_GET_RESPONSE_BODY;
  }

  @GetMapping(AUTHED_SIMPLE_GET_SKIP_RBAC)
  @Transactional
  @NoTenantScope
  @AccessControl(skipRBAC = true)
  @RateLimit(rps = THROTTLED_ENDPOINT_AUTHED_RPS)
  public String simpleGetSkipRbac() {
    return SIMPLE_GET_RESPONSE_BODY;
  }

  @GetMapping(OPEN_SIMPLE_GET)
  @Transactional
  @NoTenantScope
  @RateLimit(rps = THROTTLED_ENDPOINT_AUTHED_RPS)
  public String openSimpleGet() {
    return SIMPLE_GET_RESPONSE_BODY;
  }
}
