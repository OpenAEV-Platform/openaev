package io.openaev.api.chaining;

import io.openaev.api.chaining.dto.ChainingConfigurationOutput;
import io.openaev.api.chaining.dto.ChainingRateLimitInput;
import io.openaev.api.chaining.dto.ChainingRateLimitOutput;
import io.openaev.api.chaining.dto.ChainingTimeOutInput;
import io.openaev.api.chaining.dto.ChainingTimeOutOutput;
import io.openaev.database.model.ChainingConfiguration;
import io.openaev.database.model.ChainingRateLimit;
import io.openaev.database.model.ChainingTimeOut;
import org.springframework.stereotype.Component;

@Component
public class ChainingConfigurationMapper {

  // -- Input → Entity --

  /**
   * Maps a {@link ChainingRateLimitInput} DTO to a {@link ChainingRateLimit} entity.
   *
   * @param input the rate limit input, may be {@code null}
   * @return the mapped entity, or {@code null} if input is {@code null}
   */
  public ChainingRateLimit toRateLimit(ChainingRateLimitInput input) {
    if (input == null) {
      return null;
    }
    ChainingRateLimit rateLimit = new ChainingRateLimit();
    rateLimit.setRateLimit(input.isRateLimit());
    rateLimit.setMaxAttempts(input.getMaxAttempts());
    rateLimit.setMaxTemporalRateMinutes(input.getMaxTemporalRateMinutes());
    return rateLimit;
  }

  /**
   * Maps a {@link ChainingTimeOutInput} DTO to a {@link ChainingTimeOut} entity, converting hours
   * and minutes to seconds.
   *
   * @param input the timeout input, may be {@code null}
   * @return the mapped entity, or {@code null} if input is {@code null}
   */
  public ChainingTimeOut toTimeOut(ChainingTimeOutInput input) {
    if (input == null) {
      return null;
    }
    int hours = input.getTimeOutHours() != null ? input.getTimeOutHours() : 0;
    int minutes = input.getTimeOutMinutes() != null ? input.getTimeOutMinutes() : 0;
    ChainingTimeOut timeOut = new ChainingTimeOut();
    timeOut.setTimeOut(input.isTimeOut());
    timeOut.setTimeOutSeconds(hours * 3600 + minutes * 60);
    return timeOut;
  }

  // -- Entity → Output --

  /**
   * Maps a {@link ChainingConfiguration} entity to its {@link ChainingConfigurationOutput} DTO.
   *
   * @param chainingConfiguration the entity to map
   * @return the mapped output DTO
   */
  public ChainingConfigurationOutput toOutput(ChainingConfiguration chainingConfiguration) {
    return ChainingConfigurationOutput.builder()
        .rateLimit(toRateLimitOutput(chainingConfiguration.getRateLimit()))
        .timeOut(toTimeOutOutput(chainingConfiguration.getTimeOut()))
        .isSafeMode(chainingConfiguration.isSafeMode())
        .build();
  }

  /**
   * Maps a {@link ChainingRateLimit} entity to its {@link ChainingRateLimitOutput} DTO.
   *
   * @param rateLimit the entity to map, may be {@code null}
   * @return the mapped output DTO, or {@code null} if input is {@code null}
   */
  private ChainingRateLimitOutput toRateLimitOutput(ChainingRateLimit rateLimit) {
    if (rateLimit == null) {
      return null;
    }
    return ChainingRateLimitOutput.builder()
        .isRateLimit(rateLimit.isRateLimit())
        .maxAttempts(rateLimit.getMaxAttempts())
        .maxTemporalRateMinutes(rateLimit.getMaxTemporalRateMinutes())
        .build();
  }

  /**
   * Maps a {@link ChainingTimeOut} entity to its {@link ChainingTimeOutOutput} DTO, converting the
   * stored seconds into hours and remaining minutes.
   *
   * @param timeOut the entity to map, may be {@code null}
   * @return the mapped output DTO, or {@code null} if input is {@code null}
   */
  private ChainingTimeOutOutput toTimeOutOutput(ChainingTimeOut timeOut) {
    if (timeOut == null) {
      return null;
    }
    Integer seconds = timeOut.getTimeOutSeconds();
    return ChainingTimeOutOutput.builder()
        .isTimeOut(timeOut.isTimeOut())
        .timeOutHours(seconds != null ? seconds / 3600 : null)
        .timeOutMinutes(seconds != null ? (seconds % 3600) / 60 : null)
        .build();
  }
}
