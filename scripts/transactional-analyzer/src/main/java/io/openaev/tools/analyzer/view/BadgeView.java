package io.openaev.tools.analyzer.view;

/** A coloured badge displayed next to a method node. */
public final class BadgeView {
  public final String cssClass;
  public final String text;

  public BadgeView(String cssClass, String text) {
    this.cssClass = cssClass;
    this.text = text;
  }
}
