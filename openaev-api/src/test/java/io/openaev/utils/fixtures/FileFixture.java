package io.openaev.utils.fixtures;

import static java.util.Map.entry;

import io.openaev.utils.fixtures.files.BaseFile;
import io.openaev.utils.fixtures.files.BinaryFile;
import io.openaev.utils.fixtures.files.PlainTextFile;
import java.util.HexFormat;
import java.util.Map;

public class FileFixture {
  public static String DEFAULT_PLAIN_TEXT_FILENAME = "plain_text.txt";
  public static String DEFAULT_PNG_GRID_FILENAME = "grid.png";
  public static String DEFAULT_PNG_SMILE_FILENAME = "smile.png";
  public static String DEFAULT_BAD_COFFEE_FILENAME = "badcoffee";
  public static String DEFAULT_BEAD_FILENAME = "bead";

  public static Map<String, BaseFile<?>> WELL_KNOWN_FILES =
      Map.ofEntries(
          entry(DEFAULT_PLAIN_TEXT_FILENAME, getPlainTextFileContent()),
          entry(DEFAULT_PNG_GRID_FILENAME, getPngGridFileContent()),
          entry(DEFAULT_PNG_SMILE_FILENAME, getPngSmileFileContent()),
          entry(DEFAULT_BAD_COFFEE_FILENAME, getBadCoffeeFileContent()),
          entry(DEFAULT_BEAD_FILENAME, getBeadFileContent()));

  public static PlainTextFile getPlainTextFileContent() {
    return new PlainTextFile("default plain text content", DEFAULT_PLAIN_TEXT_FILENAME);
  }

  // a small 16*16 black and white pixel grid
  public static BinaryFile getPngGridFileContent() {
    String hexData =
        "89504e470d0a1a0a0000000d49484452000000100000001010000000006a"
            + "087cfe000000184944415428cf636060f8ff1f2f2620cdc0306ac2303201"
            + "000979ff01ee7eef0a0000000049454e44ae426082";
    String filename = DEFAULT_PNG_GRID_FILENAME;
    return new BinaryFile(HexFormat.of().parseHex(hexData), filename, "image/png");
  }

  // a small 16*16 beautiful smiley face
  public static BinaryFile getPngSmileFileContent() {
    String hexData =
        "89504e470d0a1a0a0000000d494844520000001000000010080200000090"
            + "916836000000854944415428cfa592c11143210844779db46013f66475f6"
            + "641316b139f0631c30717eb237f0ad804849788924765a99c74a8f5eb706"
            + "92d3938e3480d1eb2c9e8eb4f3d002cbe6d2d63066726909374577e577fd"
            + "52e13f838d18dbf86818bdbae35c9a9b30c5f79e9e4803a0a4b8b8b8104b"
            + "4aba3ec971d946bf5b92b49dd8d1574bb7bef713d50350388abc6cff0000"
            + "000049454e44ae426082";
    String filename = DEFAULT_PNG_SMILE_FILENAME;
    return new BinaryFile(HexFormat.of().parseHex(hexData), filename, "image/png");
  }

  // ???
  public static BinaryFile getBadCoffeeFileContent() {
    String hexData = "abadc0ffee";
    String filename = DEFAULT_BAD_COFFEE_FILENAME;
    return new BinaryFile(HexFormat.of().parseHex(hexData), filename);
  }

  // ???????
  public static BinaryFile getBeadFileContent() {
    String hexData = "bead";
    String filename = DEFAULT_BEAD_FILENAME;
    return new BinaryFile(HexFormat.of().parseHex(hexData), filename);
  }
}
