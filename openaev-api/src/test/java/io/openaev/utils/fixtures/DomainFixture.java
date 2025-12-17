package io.openaev.utils.fixtures;

import io.openaev.database.model.Domain;
import java.awt.*;
import java.util.Random;
import java.util.UUID;

public class DomainFixture {
  public static Domain getRandomDomain() {
    Random random = new Random();

    float red = random.nextFloat();
    float green = random.nextFloat();
    float blue = random.nextFloat();

    Color colour = new Color(red, green, blue);

    int rgb = colour.getRGB();

    int redInt = (rgb >> 16) & 0xff;
    int greenInt = (rgb >> 8) & 0xff;
    int blueInt = rgb & 0xff;

    return getDomainWithNameAndColour(
        UUID.randomUUID().toString(), String.format("#%02x%02x%02x", redInt, greenInt, blueInt));
  }

  public static Domain getDomainWithNameAndColour(String name, String rgbColour) {
    Domain domain = new Domain();
    domain.setName(name);
    domain.setColor(rgbColour);
    return domain;
  }
}
