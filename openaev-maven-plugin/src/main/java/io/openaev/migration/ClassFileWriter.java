package io.openaev.migration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class ClassFileWriter {
  public void write(String directory, String className, String contents) throws IOException {
    String fullFileName = "%s/%s.java".formatted(directory, className);
    if (new File(fullFileName).exists()) {
      throw new IOException("File %s already exists, aborting.".formatted(fullFileName));
    }

    try (FileWriter fw = new FileWriter(fullFileName)) {
      fw.write(contents);
    }
  }
}
