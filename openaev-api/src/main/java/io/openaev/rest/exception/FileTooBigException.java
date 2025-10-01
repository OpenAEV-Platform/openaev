package io.openaev.rest.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class FileTooBigException extends RuntimeException {

  public FileTooBigException() {
    super();
  }

  public FileTooBigException(String errorMessage) {
    super(errorMessage);
  }
}
