package io.openaev.xtmone;

import io.openaev.xtmone.XtmOneClient.IntentInput;
import java.util.List;

/**
 * Intent declarations for OpenAEV platform registration with XTM One.
 *
 * <p>These intents are sent during registration so that XTM One returns the intent catalog with
 * available agents for each capability.
 */
public final class XtmOneIntents {

  private XtmOneIntents() {}

  public static final List<IntentInput> OPENAEV_INTENTS =
      List.of(
          new IntentInput("ttp.extractor", "Extract TTPs from documents"),
          new IntentInput("detection.generate", "Generate detection rules from malware behavior"),
          new IntentInput("fix.spelling", "Fix spelling and grammar"),
          new IntentInput("make.it.shorter", "Make text shorter"),
          new IntentInput("make.it.longer", "Make text longer"),
          new IntentInput("change.tone", "Change the tone of text"),
          new IntentInput("summarize", "Summarize text"),
          new IntentInput("explain", "Explain text in simple terms"));
}
