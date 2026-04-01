package io.openaev.utils.fixtures;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class XlsFixture {

  public static final String DEFAULT_SHEET_NAME = "TestSheet";
  public static final String DEFAULT_INJECT_TYPE = "Test";
  public static final String DEFAULT_TITLE = "My Title";
  public static final String DEFAULT_DESCRIPTION = "My Description";
  public static final String DEFAULT_TRIGGER_TIME = "J+1";

  public static String createDefaultXlsFile() throws IOException {
    String importId = UUID.randomUUID().toString();
    Path importDir = Files.createDirectory(Path.of(System.getProperty("java.io.tmpdir"), importId));
    Path xlsFile = importDir.resolve("test.xlsx");

    try (Workbook wb = new XSSFWorkbook()) {
      Sheet sheet = wb.createSheet(DEFAULT_SHEET_NAME);
      Row dataRow = sheet.createRow(0);
      dataRow.createCell(0).setCellValue(DEFAULT_INJECT_TYPE);
      dataRow.createCell(1).setCellValue(DEFAULT_TITLE);
      dataRow.createCell(2).setCellValue(DEFAULT_DESCRIPTION);
      dataRow.createCell(3).setCellValue(DEFAULT_TRIGGER_TIME);

      try (FileOutputStream fos = new FileOutputStream(xlsFile.toFile())) {
        wb.write(fos);
      }
    }

    importDir.toFile().deleteOnExit();
    xlsFile.toFile().deleteOnExit();
    return importId;
  }
}
