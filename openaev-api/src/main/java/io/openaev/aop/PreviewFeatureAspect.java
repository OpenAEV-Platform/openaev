package io.openaev.aop;

import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.PreviewFeatureService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Enforces {@link RequirePreviewFeature}: an endpoint (a request-mapped method) of an annotated
 * controller answers a 404 while its preview feature is off. Exception handlers and other public
 * methods of the controller are left alone, so the 404 itself is still rendered.
 */
@Aspect
@Component
// Outer to the lock (LP-5), transaction (LP-4), tenant scoping (LP-3 / LP-2), audit (LP-1) and
// access control (LP) aspects: a disabled feature is refused before any of them reads or refuses.
@Order(Ordered.LOWEST_PRECEDENCE - 10)
@RequiredArgsConstructor
public class PreviewFeatureAspect {

  private final PreviewFeatureService previewFeatureService;

  @Before(
      "@within(requirePreviewFeature) && ("
          + "@annotation(org.springframework.web.bind.annotation.RequestMapping)"
          + " || @annotation(org.springframework.web.bind.annotation.GetMapping)"
          + " || @annotation(org.springframework.web.bind.annotation.PostMapping)"
          + " || @annotation(org.springframework.web.bind.annotation.PutMapping)"
          + " || @annotation(org.springframework.web.bind.annotation.PatchMapping)"
          + " || @annotation(org.springframework.web.bind.annotation.DeleteMapping))")
  public void requireFeature(RequirePreviewFeature requirePreviewFeature) {
    if (!previewFeatureService.isFeatureEnabled(requirePreviewFeature.value())) {
      throw new ElementNotFoundException(
          "The " + requirePreviewFeature.value().getValue() + " preview feature is disabled");
    }
  }
}
