package io.openaev.utils.fixtures;

import io.openaev.rest.user.form.player.PlayerInput;

public class PlayerFixture {

  public static final String PLAYER_FIXTURE_FIRSTNAME = "Firstname";
  public static final String PLAYER_FIXTURE_MAIL = "player@example.com";

  public static PlayerInput createPlayerInput() {
    PlayerInput player = new PlayerInput();
    player.setEmail(PLAYER_FIXTURE_MAIL);
    player.setFirstname(PLAYER_FIXTURE_FIRSTNAME);
    player.setLastname("Lastname");
    player.setCountry("France");
    player.setPhone("+33123456789");
    player.setPgpKey("a123b");
    return player;
  }
}
