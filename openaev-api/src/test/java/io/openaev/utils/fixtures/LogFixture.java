package io.openaev.utils.fixtures;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.Log;
import io.openaev.database.model.User;
import io.openaev.rest.log.form.LogDetailsInput;

public class LogFixture {

  public static LogDetailsInput getDefaultLogDetailsInput(String level) {
    LogDetailsInput logDetailsInput = new LogDetailsInput();
    logDetailsInput.setMessage(
        "Message error received: [@formatjs/intl] An `id` must be provided to format a message.");
    logDetailsInput.setStack("tt");
    logDetailsInput.setLevel(level);

    return logDetailsInput;
  }

  public static Log createLog(Exercise exercise, User user) {
    Log log = new Log();
    log.setExercise(exercise);
    log.setUser(user);
    log.setTitle("Log title");
    log.setContent("Log content");
    return log;
  }
}
