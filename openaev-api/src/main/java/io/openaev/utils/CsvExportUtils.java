package io.openaev.utils;

import static com.opencsv.ICSVWriter.DEFAULT_QUOTE_CHARACTER;
import static com.opencsv.ICSVWriter.DEFAULT_SEPARATOR;

import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvException;
import io.openaev.service.utils.CustomColumnPositionStrategy;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class CsvExportUtils {

  private CsvExportUtils() {}

  /** Writes the rows as a UTF-8 CSV attachment named after the prefix and the current date. */
  public static <T> void write(
      HttpServletResponse response, String filenamePrefix, List<T> rows, Class<T> rowClass) {
    String date = DateTimeFormatter.ofPattern("yyyyMMddHHmm").format(LocalDateTime.now());
    response.setContentType("text/csv");
    // Before getWriter(): the default ISO-8859-1 garbles accents and turns other scripts into '?'
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setHeader(
        "Content-Disposition", "attachment; filename=" + filenamePrefix + date + ".csv");
    response.setStatus(HttpServletResponse.SC_OK);

    CustomColumnPositionStrategy<T> columns = new CustomColumnPositionStrategy<>();
    columns.setType(rowClass);
    try {
      new StatefulBeanToCsvBuilder<T>(response.getWriter())
          .withQuotechar(DEFAULT_QUOTE_CHARACTER)
          .withSeparator(DEFAULT_SEPARATOR)
          .withMappingStrategy(columns)
          .build()
          .write(rows);
    } catch (IOException | CsvException e) {
      throw new RuntimeException("Error during export CSV", e);
    }
  }
}
