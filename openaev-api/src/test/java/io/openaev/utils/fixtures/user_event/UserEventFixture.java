package io.openaev.utils.fixtures.user_event;

import io.openaev.database.model.User;
import io.openaev.database.model.UserEvent;
import io.openaev.database.model.UserEventType;

public class UserEventFixture {

  public static UserEvent getUserEventLogin(User user) {
    UserEvent userEvent = new UserEvent();
    userEvent.setUser(user);
    userEvent.setType(UserEventType.LOGIN_SUCCESS);
    return userEvent;
  }
}
