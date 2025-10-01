package io.openaev.injectors.openaev.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import lombok.Getter;

public class OpenAEVObfuscationMap {
  private final Map<String, OpenAEVObfuscation> obfuscationMap;

  @Getter
  public static class OpenAEVObfuscation {
    private final String information;
    private final BiFunction<String, String, String> obfuscate;

    public OpenAEVObfuscation(String information, BiFunction<String, String, String> obfuscate) {
      this.information = information;
      this.obfuscate = obfuscate;
    }
  }

  public OpenAEVObfuscationMap() {
    this.obfuscationMap = new HashMap<>();
    this.registerObfuscation("plain-text", "", this::obfuscatePlainText);
    this.registerObfuscation(
        "base64", "CMD does not support base64 obfuscation", this::obfuscateBase64);
  }

  public void registerObfuscation(
      String key, String information, BiFunction<String, String, String> function) {
    if (key == null || function == null) {
      throw new IllegalArgumentException("Key and function must not be null.");
    }
    obfuscationMap.put(key, new OpenAEVObfuscation(information, function));
  }

  public String executeObfuscation(String key, String command, String executor) {
    OpenAEVObfuscation obfuscation = obfuscationMap.get(key);
    if (obfuscation != null) {
      return obfuscation.getObfuscate().apply(command, executor);
    }
    throw new IllegalArgumentException("No obfuscation found for key: " + key);
  }

  public Map<String, String> getAllObfuscationInfo() {
    Map<String, String> keyInfoMap = new HashMap<>();
    for (Map.Entry<String, OpenAEVObfuscation> entry : obfuscationMap.entrySet()) {
      keyInfoMap.put(entry.getKey(), entry.getValue().getInformation());
    }
    return keyInfoMap;
  }

  private String obfuscatePlainText(String command, String executor) {
    return command;
  }

  private String obfuscateBase64(String command, String executor) {
    String obfuscatedCommand = command;

    if (executor.equals("psh") || executor.equals("cmd")) {
      byte[] utf16Bytes = command.getBytes(StandardCharsets.UTF_16LE);
      String base64 = Base64.getEncoder().encodeToString(utf16Bytes);
      obfuscatedCommand = String.format("powershell -Enc %s", base64);

    } else if (executor.equals("bash") || executor.equals("sh")) {
      obfuscatedCommand =
          String.format(
              "eval \"$(echo %s | base64 --decode)\"",
              Base64.getEncoder().encodeToString(command.getBytes()));
    }
    return obfuscatedCommand;
  }

  public String getDefaultObfuscator() {
    return "plain-text";
  }
}
