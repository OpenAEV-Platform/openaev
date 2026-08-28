package io.openaev.utils;

import io.openaev.context.TxCtx;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Supplies a fixed {@link TxCtx} for controllers under a standalone {@code MockMvc} setup, which
 * does not load the production argument resolver. Standalone controller tests mock the data layer
 * and do not exercise the tenant scope, so {@link #missing()} is the usual choice; a specific scope
 * can be passed when a test does care.
 */
public class TxCtxTestArgumentResolver implements HandlerMethodArgumentResolver {

  private final TxCtx ctx;

  public TxCtxTestArgumentResolver(TxCtx ctx) {
    this.ctx = ctx;
  }

  public static TxCtxTestArgumentResolver missing() {
    return new TxCtxTestArgumentResolver(TxCtx.missing());
  }

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return TxCtx.class.equals(parameter.getParameterType());
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {
    return ctx;
  }
}
