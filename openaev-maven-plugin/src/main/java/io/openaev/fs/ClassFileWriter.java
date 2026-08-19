package io.openaev.fs;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ClassFileWriter {
  private String composeFullFilePath(String directory, String className) {
    return "%s/%s.java".formatted(directory, className);
  }

  private void ensureDirectoryExists(String directory) throws IOException {
    Path path = Paths.get(directory);
    if (Files.exists(path)) return;
    Files.createDirectories(path);
  }

  public void write(String directory, String className, String contents) throws IOException {
    String fullFilePath = composeFullFilePath(directory, className);
    if (new File(fullFilePath).exists()) {
      throw new IOException("File %s already exists, aborting.".formatted(fullFilePath));
    }
    overwrite(directory, className, contents);
  }

  public void overwrite(String directory, String className, String contents) throws IOException {
    ensureDirectoryExists(directory);
    try (FileWriter fw = new FileWriter(composeFullFilePath(directory, className))) {
      fw.write(contents);
    }
  }
}
