package io.openaev.utils;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class RandomUtils {
  private final SecureRandom csprng = SecureRandom.getInstanceStrong();

  private final int numberPossibleChars = 62; // number of chars in range 0-9A-Za-z
  private final char[] alphanumericAlphabet = new char[numberPossibleChars];

  public RandomUtils() throws NoSuchAlgorithmException {
    int asciiValue = 48; // '0'
    for (int i = 0; i < numberPossibleChars; i++) {
      alphanumericAlphabet[i] = (char) asciiValue++;
      switch (asciiValue) {
        case 58 -> asciiValue = 65; // 'A'
        case 91 -> asciiValue = 97; // 'a'
      }
    }
  }

  public String getRandomAlphanumeric(int length) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < length; i++) {
      builder.append(alphanumericAlphabet[csprng.nextInt(numberPossibleChars)]);
    }
    return builder.toString();
  }
}
