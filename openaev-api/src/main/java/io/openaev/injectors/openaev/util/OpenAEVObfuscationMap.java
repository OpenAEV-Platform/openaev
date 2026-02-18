package io.openaev.injectors.openaev.util;

import static io.openaev.executors.Executor.CMD;
import static io.openaev.executors.Executor.PSH;

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
    private final BiFunction<String, String, String> obfuscate;

    public OpenAEVObfuscation(BiFunction<String, String, String> obfuscate) {
      this.obfuscate = obfuscate;
    }
  }

  public OpenAEVObfuscationMap(String executor) {
    this.obfuscationMap = new HashMap<>();
    this.registerObfuscation("plain-text", this::obfuscatePlainText);
    if (!CMD.equals(executor)) {
      this.registerObfuscation("base64", this::obfuscateBase64);
    }
  }

  public void registerObfuscation(String key, BiFunction<String, String, String> function) {
    if (key == null || function == null) {
      throw new IllegalArgumentException("Key and function must not be null.");
    }
    obfuscationMap.put(key, new OpenAEVObfuscation(function));
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
      // Key is used for both label and value (common use case where they're identical)
      keyInfoMap.put(entry.getKey(), entry.getKey());
    }
    return keyInfoMap;
  }

  private String obfuscatePlainText(String command, String executor) {
    return command;
  }

  private String obfuscateBase64(String command, String executor) {
    String obfuscatedCommand = command;

    if (PSH.equals(executor) || CMD.equals(executor)) {
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
