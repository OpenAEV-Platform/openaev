package io.openaev.rest.helper;

import io.openaev.service.ImportService;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ExportHelper {
  public static ZipOutputStream initExport(OutputStream outputStream) throws IOException {
    ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream);
    for (int i = 0; i < ImportService.EXPORT_ENTRY.values().length; i++) {
      zipOutputStream.putNextEntry(new ZipEntry(ImportService.EXPORT_ENTRY.values()[i] + "/"));
    }
    return zipOutputStream;
  }
}
