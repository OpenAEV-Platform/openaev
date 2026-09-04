package io.openaev.utils;

import static com.opencsv.ICSVWriter.DEFAULT_QUOTE_CHARACTER;
import static com.opencsv.ICSVWriter.DEFAULT_SEPARATOR;

import com.opencsv.CSVWriter;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Shared RFC 4180 CSV export helper (UTF-8, opencsv-backed), used by the Attack Chaining exports
 * (findings, execution trace, chokepoints) to stream a bean list as a {@code text/csv} HTTP
 * response with a "Content-Disposition: attachment" header, the same way {@code
 * io.openaev.service.MapperService} does for the mapper/endpoint/threat-arsenal CSV exports.
 */
public final class CsvExportUtils {

  private static final DateTimeFormatter FILENAME_TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

  private CsvExportUtils() {}

  /** Builds an export filename as {@code prefix-suffix-yyyyMMddHHmmss.csv}. */
  public static String buildFilename(String prefix, String suffix) {
    String timestamp = FILENAME_TIMESTAMP.format(LocalDateTime.now());
    String safePrefix = sanitizeForFilename(prefix);
    String safeSuffix = sanitizeForFilename(suffix);
    return safePrefix + "-" + safeSuffix + "-" + timestamp + ".csv";
  }

  private static String sanitizeForFilename(String value) {
    if (value == null || value.isBlank()) {
      return "unknown";
    }
    // Keep the file name portable across OSes: strip anything but alphanumerics/dash/underscore.
    String sanitized = value.trim().replaceAll("[^a-zA-Z0-9-_]+", "-");
    return sanitized.isBlank() ? "unknown" : sanitized;
  }

  /**
   * Streams {@code rows} to the HTTP response as a UTF-8, RFC 4180 compliant CSV file (comma
   * separator, double-quote quoting, embedded quotes doubled per the opencsv default writer).
   */
  public static <T> void writeCsv(
      HttpServletResponse response, String filename, List<T> rows, Class<T> rowClass)
      throws IOException, CsvDataTypeMismatchException, CsvRequiredFieldEmptyException {
    response.setContentType("text/csv;charset=UTF-8");
    response.setCharacterEncoding("UTF-8");
    response.setHeader("Content-Disposition", "attachment; filename=" + filename);
    response.setStatus(HttpServletResponse.SC_OK);

    PrettyHeaderColumnPositionStrategy<T> columns = new PrettyHeaderColumnPositionStrategy<>();
    columns.setType(rowClass);

    if (rows.isEmpty()) {
      // StatefulBeanToCsv can't generate a header from an empty list (generateHeader() needs a
      // bean instance to introspect), so a header-only CSV (e.g. "no findings match the current
      // filters") would otherwise come back completely empty. Build a throwaway instance instead
      // just to get the header row out, matching the "visible list = exported CSV" behavior for
      // an empty result set too.
      try {
        T dummy = rowClass.getDeclaredConstructor().newInstance();
        String[] header = columns.generateHeader(dummy);
        CSVWriter csvWriter =
            new CSVWriter(
                response.getWriter(),
                DEFAULT_SEPARATOR,
                DEFAULT_QUOTE_CHARACTER,
                CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                CSVWriter.DEFAULT_LINE_END);
        csvWriter.writeNext(header);
        csvWriter.flush();
      } catch (ReflectiveOperationException e) {
        throw new IOException("Unable to generate CSV header for " + rowClass.getName(), e);
      }
      return;
    }

    StatefulBeanToCsv<T> writer =
        new StatefulBeanToCsvBuilder<T>(response.getWriter())
            .withQuotechar(DEFAULT_QUOTE_CHARACTER)
            .withSeparator(DEFAULT_SEPARATOR)
            .withMappingStrategy(columns)
            .build();

    writer.write(rows);
  }

  /** Empty-safe join of values with ", ", falling back to "-" like the mapper CSV export does. */
  public static String joinOrDash(List<String> values) {
    if (values == null || values.isEmpty()) {
      return "-";
    }
    String joined =
        String.join(", ", values.stream().filter(v -> v != null && !v.isBlank()).toList());
    return joined.isBlank() ? "-" : joined;
  }

  /** Empty-safe scalar, falling back to "-" like the mapper CSV export does. */
  public static String valueOrDash(String value) {
    return value == null || value.isBlank() ? "-" : value;
  }
}
