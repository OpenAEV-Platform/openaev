package io.openaev.utils.fixtures;

import io.openaev.database.model.Pause;
import java.time.Instant;

public class PauseFixture {
  public static Pause createPause(Instant start, long duration) {
    Pause pause = new Pause();
    pause.setDuration(duration);
    pause.setDate(start);
    return pause;
  }
}
