package io.openaev.utils;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.RuleAttribute;
import io.openaev.service.InjectTime;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.poi.hssf.usermodel.HSSFRichTextString;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.RichTextString;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.springframework.web.util.HtmlUtils;

public final class InjectImportUtils {

  private InjectImportUtils() {}

  /**
   * Returns the date as string from an excel cell
   *
   * @param row the row
   * @param cellColumn the column
   * @param timePattern the pattern to use to convert the date
   * @return the date as string
   */
  public static String getDateAsStringFromCell(Row row, String cellColumn, String timePattern) {
    if (cellColumn != null
        && !cellColumn.isBlank()
        && row.getCell(CellReference.convertColStringToIndex(cellColumn)) != null) {
      Cell cell = row.getCell(CellReference.convertColStringToIndex(cellColumn));
      if (cell.getCellType() == CellType.STRING) {
        return cell.getStringCellValue();
      } else if (cell.getCellType() == CellType.NUMERIC) {
        if (timePattern == null || timePattern.isEmpty()) {
          return cell.getDateCellValue().toString();
        } else {
          return DateFormatUtils.format(cell.getDateCellValue(), timePattern);
        }
      }
    }
    return "";
  }

  /**
   * Get the value of a cell as a string
   *
   * @param row the row
   * @param cellColumn the column
   * @return the value of the cell as string
   */
  public static String getValueAsString(Row row, String cellColumn) {
    if (cellColumn != null
        && !cellColumn.isBlank()
        && row.getCell(CellReference.convertColStringToIndex(cellColumn)) != null) {
      Cell cell = row.getCell(CellReference.convertColStringToIndex(cellColumn));
      if (cell.getCellType() == CellType.STRING) {
        return cell.getStringCellValue();
      } else if (cell.getCellType() == CellType.NUMERIC) {
        return Double.valueOf(cell.getNumericCellValue()).toString();
      }
    }
    return "";
  }

  /**
   * Get the value of a cell as a double
   *
   * @param row the row
   * @param cellColumn the column
   * @return the value of the cell as a double
   */
  public static Double getValueAsDouble(Row row, String cellColumn) {
    if (cellColumn != null
        && !cellColumn.isBlank()
        && row.getCell(CellReference.convertColStringToIndex(cellColumn)) != null) {
      Cell cell = row.getCell(CellReference.convertColStringToIndex(cellColumn));
      if (cell.getCellType() == CellType.STRING) {
        return Double.valueOf(cell.getStringCellValue());
      } else if (cell.getCellType() == CellType.NUMERIC) {
        return cell.getNumericCellValue();
      }
    }
    return 0.0;
  }

  /**
   * Extract the value as string and convert it to HTML if need be
   *
   * @param row the row
   * @param ruleAttribute the rule to use to extract the value
   * @param mapFieldByKey the map of the fields organized by the name of the field
   * @return the value as string
   */
  public static String extractAndConvertStringColumnValue(
      Row row, RuleAttribute ruleAttribute, Map<String, JsonNode> mapFieldByKey) {
    // Given that richText fields are editable using CKEditor which expects HTML,
    // we're converting the cell content (formatting and return lines) into HTML.
    JsonNode field = mapFieldByKey.get(ruleAttribute.getName());
    boolean richText = field.get("richText") != null && field.get("richText").asBoolean();
    return Arrays.stream(ruleAttribute.getColumns().split("\\+"))
        .map(column -> richText ? getValueAsHtml(row, column) : getValueAsString(row, column))
        .collect(Collectors.joining());
  }

  private static String getValueAsHtml(Row row, String cellColumn) {
    if (cellColumn != null && !cellColumn.isBlank()) {
      Cell cell = row.getCell(CellReference.convertColStringToIndex(cellColumn));
      if (cell != null && cell.getCellType() == CellType.STRING) {
        return richTextToHtml(cell);
      }
    }
    return getValueAsString(row, cellColumn);
  }

  private static String richTextToHtml(Cell cell) {
    RichTextString richText = cell.getRichStringCellValue();
    String text = richText.getString();
    Workbook workbook = cell.getSheet().getWorkbook();
    Font cellFont = workbook.getFontAt(cell.getCellStyle().getFontIndex());
    int runs = richText.numFormattingRuns();
    if (runs == 0 && !isFormatted(cellFont)) {
      // An unformatted cell keeps its raw content, which may already be HTML.
      return text.replaceAll("\r?\n", "<br/>");
    }
    StringBuilder html = new StringBuilder();
    int start = 0;
    for (int run = 0; run <= runs; run++) {
      int end = run < runs ? richText.getIndexOfFormattingRun(run) : text.length();
      if (end > start) {
        html.append(
            fontToHtml(
                text.substring(start, end), fontOfRun(richText, run - 1, workbook, cellFont)));
      }
      start = Math.max(start, end);
    }
    return html.toString();
  }

  private static Font fontOfRun(
      RichTextString richText, int run, Workbook workbook, Font cellFont) {
    Font font = null;
    if (run >= 0 && richText instanceof XSSFRichTextString xssfRichText) {
      font = xssfRichText.getFontOfFormattingRun(run);
    } else if (run >= 0 && richText instanceof HSSFRichTextString hssfRichText) {
      font = workbook.getFontAt(hssfRichText.getFontOfFormattingRun(run));
    }
    return font != null ? font : cellFont;
  }

  private static boolean isFormatted(Font font) {
    return font.getBold() || font.getItalic() || font.getUnderline() != Font.U_NONE;
  }

  private static String fontToHtml(String text, Font font) {
    String html = HtmlUtils.htmlEscape(text).replaceAll("\r?\n", "<br/>");
    if (font.getUnderline() != Font.U_NONE) {
      html = "<u>" + html + "</u>";
    }
    if (font.getItalic()) {
      html = "<em>" + html + "</em>";
    }
    if (font.getBold()) {
      html = "<strong>" + html + "</strong>";
    }
    return html;
  }

  /**
   * Returns a date out of an InjectTime object with a time pattern
   *
   * @param injectTime the object representing a date in the cells
   * @param timePattern a pattern to use to find out what value it is
   * @return the date
   */
  public static Temporal getInjectDate(InjectTime injectTime, String timePattern) {
    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME;
    if (timePattern != null && !timePattern.isEmpty()) {
      dateTimeFormatter = DateTimeFormatter.ofPattern(timePattern);
      try {
        return LocalDateTime.parse(injectTime.getUnformattedDate(), dateTimeFormatter);
      } catch (DateTimeParseException firstException) {
        try {
          return LocalTime.parse(injectTime.getUnformattedDate(), dateTimeFormatter);
        } catch (DateTimeParseException exception) {
          // This is a "probably" a relative date
        }
      }
    } else {
      try {
        return LocalDateTime.parse(injectTime.getUnformattedDate(), dateTimeFormatter);
      } catch (DateTimeParseException firstException) {
        // The date is not in ISO_DATE_TIME. Trying just the ISO_TIME
        dateTimeFormatter = DateTimeFormatter.ISO_TIME;
        try {
          return LocalTime.parse(injectTime.getUnformattedDate(), dateTimeFormatter);
        } catch (DateTimeParseException secondException) {
          // Neither ISO_DATE_TIME nor ISO_TIME
        }
      }
    }
    injectTime.setFormatter(dateTimeFormatter);
    return null;
  }
}
