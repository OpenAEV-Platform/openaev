package io.openaev.utils.fixtures;

import java.awt.*;
import java.util.Random;

public class ColourFixture {
  public static String getRandomRgbString() {
    Random random = new Random();

    float red = random.nextFloat();
    float green = random.nextFloat();
    float blue = random.nextFloat();

    Color colour = new Color(red, green, blue);

    int rgb = colour.getRGB();

    int redInt = (rgb >> 16) & 0xff;
    int greenInt = (rgb >> 8) & 0xff;
    int blueInt = rgb & 0xff;

    return String.format("#%02x%02x%02x", redInt, greenInt, blueInt);
  }
}
