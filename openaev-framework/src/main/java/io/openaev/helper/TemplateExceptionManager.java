package io.openaev.helper;

import freemarker.core.Environment;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import java.io.IOException;

public class TemplateExceptionManager implements TemplateExceptionHandler {
  public void handleTemplateException(TemplateException te, Environment env, java.io.Writer out)
      throws TemplateException {
    try {
      out.write("${" + te.getBlamedExpressionString() + "}");
    } catch (IOException e) {
      throw new TemplateException("Failed to print error message. Cause: " + e, env);
    }
  }
}
