package io.openaev.utils.fixtures;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class XlsFixture {

  public static final String DEFAULT_SHEET_NAME = "TestSheet";
  public static final String DEFAULT_INJECT_TYPE = "Test";
  public static final String DEFAULT_TITLE = "My Title";
  public static final String DEFAULT_DESCRIPTION = "My Description";
  public static final String DEFAULT_TRIGGER_TIME = "J+1";

  public static Builder xlsFile() {
    return new Builder();
  }

  public static String createDefaultXlsFile() throws IOException {
    return xlsFile().withDefaultInjectRow().build();
  }

  public static class Builder {

    private String sheetName = DEFAULT_SHEET_NAME;
    private final Map<Integer, String> cellsByColumnIndex = new LinkedHashMap<>();

    public Builder withSheetName(String sheetName) {
      this.sheetName = sheetName;
      return this;
    }

    public Builder withDefaultInjectRow() {
      return withCell("A", DEFAULT_INJECT_TYPE)
          .withCell("B", DEFAULT_TITLE)
          .withCell("C", DEFAULT_DESCRIPTION)
          .withCell("D", DEFAULT_TRIGGER_TIME);
    }

    // Columns are named as the mappers name them, and resolved with the same POI helper as
    // InjectImportUtils, so a fixture and the importer cannot disagree on a column
    public Builder withCell(String column, String value) {
      this.cellsByColumnIndex.put(CellReference.convertColStringToIndex(column), value);
      return this;
    }

    public String build() throws IOException {
      String importId = UUID.randomUUID().toString();
      Path importDir =
          Files.createDirectory(Path.of(System.getProperty("java.io.tmpdir"), importId));
      Path xlsFile = importDir.resolve("test.xlsx");

      try (Workbook wb = new XSSFWorkbook()) {
        Sheet sheet = wb.createSheet(sheetName);
        Row dataRow = sheet.createRow(0);
        cellsByColumnIndex.forEach(
            (columnIndex, value) -> dataRow.createCell(columnIndex).setCellValue(value));

        try (FileOutputStream fos = new FileOutputStream(xlsFile.toFile())) {
          wb.write(fos);
        }
      }

      importDir.toFile().deleteOnExit();
      xlsFile.toFile().deleteOnExit();
      return importId;
    }
  }
}
