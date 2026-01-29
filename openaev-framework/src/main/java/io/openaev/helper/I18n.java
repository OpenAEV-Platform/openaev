package io.openaev.helper;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

@Component
public class I18n {

  private final MessageSource messageSource;

  public I18n(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  public String t(String key, Object... args) {
    return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
  }

}
