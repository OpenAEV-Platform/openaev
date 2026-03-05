package io.openaev.api.chaining.validator;

import io.openaev.api.chaining.annotation.ValidTimeOutDuration;
import io.openaev.api.chaining.dto.ChainingTimeOutInput;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for {@link ValidTimeOutDuration}.
 *
 * <p>When the timeout feature is enabled ({@code isTimeOut = true}), at least one of {@code
 * timeOutHours} or {@code timeOutMinutes} must be greater than zero. When timeout is disabled, no
 * duration constraint is enforced.
 */
public class TimeOutDurationValidator
    implements ConstraintValidator<ValidTimeOutDuration, ChainingTimeOutInput> {

  @Override
  public boolean isValid(ChainingTimeOutInput input, ConstraintValidatorContext context) {
    if (input == null || !input.isTimeOut()) {
      return true;
    }
    int hours = input.getTimeOutHours() != null ? input.getTimeOutHours() : 0;
    int minutes = input.getTimeOutMinutes() != null ? input.getTimeOutMinutes() : 0;
    return hours > 0 || minutes > 0;
  }
}
