package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The email-change completion notice embeds the new address in a body that is processed by
 * FreeMarker and then rendered as HTML. The new address is caller-controlled: a quoted
 * {@code @Email} local part can carry '<', '>', '#', '$', quotes and spaces. {@link
 * UserService#escapeForEmailBody} must neutralise that content so it cannot inject HTML or a
 * FreeMarker directive/interpolation into the warning sent to the previous address. These checks
 * run without Spring, so they hold regardless of the integration environment.
 */
@DisplayName("UserService.escapeForEmailBody")
class UserServiceEmailEscapeTest {

  @Test
  @DisplayName("HTML tags in a quoted local part are escaped, not left raw")
  void escapesHtmlTags() {
    String out = UserService.escapeForEmailBody("\"<div style='display:none'>\"@x.invalid");
    assertThat(out).doesNotContain("<div");
    assertThat(out).contains("&lt;div");
  }

  @Test
  @DisplayName("FreeMarker ${...} interpolation is neutralised and never evaluated")
  void neutralisesDollarInterpolation() {
    String out = UserService.escapeForEmailBody("\"${7*7}\"@x.invalid");
    assertThat(out).doesNotContain("${");
    assertThat(out).doesNotContain("49");
  }

  @Test
  @DisplayName("FreeMarker <#...> directives are escaped")
  void escapesDirectives() {
    String out = UserService.escapeForEmailBody("\"<#if x>\"@x.invalid");
    assertThat(out).doesNotContain("<#");
  }

  @Test
  @DisplayName("A normal address is returned unchanged")
  void leavesNormalAddressUnchanged() {
    assertThat(UserService.escapeForEmailBody("alice@example.com")).isEqualTo("alice@example.com");
  }
}
