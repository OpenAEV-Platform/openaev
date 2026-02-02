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
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellReference;

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
    String columnValue =
        Arrays.stream(ruleAttribute.getColumns().split("\\+"))
            .map(column -> getValueAsString(row, column))
            .collect(Collectors.joining());

    // Given that richText fields are editable using CKEditor which expects HTML,
    // we're converting return line into <br>.
    // TODO : convert properly the whole cell into HTML including formatting (bold, ...)
    if (mapFieldByKey.get(ruleAttribute.getName()).get("richText") != null
        && mapFieldByKey.get(ruleAttribute.getName()).get("richText").asBoolean()) {
      columnValue = columnValue.replaceAll("\n", "<br/>");
    }
    return columnValue;
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
