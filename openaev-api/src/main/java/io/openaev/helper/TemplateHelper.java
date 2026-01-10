package io.openaev.helper;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import io.openaev.execution.ExecutionContext;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;

public final class TemplateHelper {

  private TemplateHelper() {}

  /** Cached Freemarker configuration - thread-safe and designed for reuse */
  private static final Configuration FREEMARKER_CONFIG;

  static {
    FREEMARKER_CONFIG = new Configuration(Configuration.VERSION_2_3_31);
    FREEMARKER_CONFIG.setTemplateExceptionHandler(new TemplateExceptionManager());
    FREEMARKER_CONFIG.setLogTemplateExceptions(false);
  }

  public static String buildContextualContent(String content, ExecutionContext context)
      throws IOException, TemplateException {
    return buildContentWithDataMap(content, context);
  }

  public static String buildContentWithDataMap(String content, Map<String, Object> dataMap)
      throws IOException, TemplateException {
    if (content == null) return "";
    Template template = new Template("template", new StringReader(content), FREEMARKER_CONFIG);
    return FreeMarkerTemplateUtils.processTemplateIntoString(template, dataMap);
  }
}
