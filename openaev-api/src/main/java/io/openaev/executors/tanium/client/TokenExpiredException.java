package io.openaev.executors.tanium.client;

public class TokenExpiredException extends RuntimeException {

  public TokenExpiredException(String message) {
    super(message);
  }
}
