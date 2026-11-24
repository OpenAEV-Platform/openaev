package io.openaev.notification.engine;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A notification message rendered per recipient, the OpenAEV equivalent of OpenCTI's {@code
 * extractStixRepresentativeForUser}: an event is never dropped because an object it mentions is
 * hidden from a recipient; the hidden object's name is replaced by {@link #RESTRICTED} instead.
 *
 * <p>The template names its related objects by index ({@code {0}}, {@code {1}}...), each one a
 * {@link Reference} the recipient's read right is checked on. A template without references renders
 * as itself. Rendering happens before the dispatch, so the interface, email, webhook and digest all
 * carry the recipient's own rendering.
 */
public record NotificationMessage(String template, List<Reference> references) {

  /** The name shown in place of an object the recipient cannot read. */
  public static final String RESTRICTED = "Restricted";

  private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)}");

  /** An object a message names: its catalogue entry, id and name. */
  public record Reference(NotificationResourceCatalog entry, String id, String name) {}

  public NotificationMessage {
    references = references == null ? List.of() : List.copyOf(references);
  }

  /** A message that names no related object. */
  public static NotificationMessage of(String text) {
    return new NotificationMessage(text, List.of());
  }

  /**
   * The message for one recipient: each reference shows its name when {@code readable} accepts it,
   * {@link #RESTRICTED} otherwise (a check that throws, a missing name or an unknown index
   * included: fail closed). Single pass, so a name that itself looks like a placeholder is never
   * expanded.
   */
  public String render(Predicate<Reference> readable) {
    if (template == null || references.isEmpty()) {
      return template;
    }
    Matcher matcher = PLACEHOLDER.matcher(template);
    StringBuilder rendered = new StringBuilder();
    while (matcher.find()) {
      matcher.appendReplacement(rendered, Matcher.quoteReplacement(nameFor(matcher, readable)));
    }
    matcher.appendTail(rendered);
    return rendered.toString();
  }

  private String nameFor(Matcher matcher, Predicate<Reference> readable) {
    int index = Integer.parseInt(matcher.group(1));
    if (index >= references.size()) {
      return RESTRICTED;
    }
    Reference reference = references.get(index);
    boolean visible;
    try {
      visible = readable.test(reference);
    } catch (Exception e) {
      visible = false;
    }
    return visible && reference.name() != null ? reference.name() : RESTRICTED;
  }
}
