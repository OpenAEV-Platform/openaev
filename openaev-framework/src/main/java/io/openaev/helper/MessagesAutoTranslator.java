package io.openaev.helper;

import com.deepl.api.DeepLException;
import com.deepl.api.TextResult;
import com.deepl.api.Translator;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

public class MessagesAutoTranslator {

  private final Translator translator;
  private final String baseName;
  private final String sourceLang;
  private final List<String> targetLangs;

  public MessagesAutoTranslator(String apiKey,
      String baseName,
      String sourceLang,
      List<String> targetLangs) {
    this.translator = new Translator(apiKey);
    this.baseName = baseName;
    this.sourceLang = sourceLang;
    this.targetLangs = targetLangs;
  }

  public void run() throws Exception {
    Properties sourceProps = loadProps(baseName + ".properties");

    for (String targetLang : targetLangs) {
      String targetFile = baseName + "_" + targetLang.toLowerCase() + ".properties";
      Properties targetProps = loadProps(targetFile);

      boolean changed = false;

      for (String key : sourceProps.stringPropertyNames()) {
        String srcText = sourceProps.getProperty(key);
        String existing = targetProps.getProperty(key);

        if (existing == null || existing.isBlank()) {
          String translated = translateSafe(srcText, sourceLang, targetLang);
          targetProps.setProperty(key, translated);
          changed = true;
          System.out.printf("Translated [%s] to %s: %s -> %s%n",
              key, targetLang, srcText, translated);
        }
      }

      if (changed) {
        storeProps(targetProps, targetFile, "Auto‑translated from " + sourceLang);
      } else {
        System.out.println("No new keys for " + targetLang);
      }
    }
  }

  private Properties loadProps(String filename) throws IOException {
    Properties props = new Properties();
    File file = new File("src/main/resources/" + filename);
    if (file.exists()) {
      try (Reader reader = new InputStreamReader(
          new FileInputStream(file), StandardCharsets.UTF_8)) {
        props.load(reader);
      }
    }
    return props;
  }

  private void storeProps(Properties props, String filename, String comment) throws IOException {
    File file = new File("src/main/resources/" + filename);
    try (Writer writer = new OutputStreamWriter(
        new FileOutputStream(file), StandardCharsets.UTF_8)) {
      props.store(writer, comment);
    }
  }

  private String translateSafe(String text, String from, String to) {
    try {
      TextResult result = translator.translateText(text, from, to);
      return result.getText();
    } catch (DeepLException | InterruptedException e) {
      System.err.println("Failed to translate: " + text + " (" + e.getMessage() + ")");
      return text; // fallback: keep source text
    }
  }

}
