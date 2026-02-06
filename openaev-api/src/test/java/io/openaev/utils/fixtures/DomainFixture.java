package io.openaev.utils.fixtures;

import io.openaev.database.model.Domain;
import java.awt.*;
import java.util.UUID;

public class DomainFixture {
  public static Domain getRandomDomain() {
    return getDomainWithNameAndColour(
        UUID.randomUUID().toString(), ColourFixture.getRandomRgbString());
  }

  public static Domain getDomainWithNameAndColour(String name, String rgbColour) {
    Domain domain = new Domain();
    domain.setName(name);
    domain.setColor(rgbColour);
    return domain;
  }
}
