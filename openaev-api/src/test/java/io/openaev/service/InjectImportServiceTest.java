package io.openaev.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.IntegrationTest;
import io.openaev.database.model.RuleAttribute;
import io.openaev.service.utils.InjectImportUtils;
import io.openaev.utils.mockMapper.MockMapperUtils;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.util.Date;
import java.util.Map;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;

public class InjectImportServiceTest extends IntegrationTest {
  private Row row;
  private Cell cell;
  private ObjectNode json;
  private Workbook workbook;

  @BeforeEach
  void before() throws Exception {
    workbook = new XSSFWorkbook();
    Sheet sheet = workbook.createSheet();
    row = sheet.createRow(0);
    cell = row.createCell(0);

    json =
        new ObjectMapper()
            .readValue(
                """
                                  {
                                   "key":"Test",
                                   "richText":true
                                  }
                                  """,
                ObjectNode.class);
  }

  @AfterEach
  void after() throws Exception {
    workbook.close();
  }

  // -- INJECT IMPORT --

  @DisplayName("Test get a date cell as string")
  @Test
  void testGetDateAsString() throws Exception {
    // -- PREPARE --
    Date date = Date.from(LocalDateTime.of(2025, 1, 1, 12, 0).toInstant(ZoneOffset.UTC));
    cell.setCellValue(date);
    // -- EXECUTE --
    String result = InjectImportUtils.getDateAsStringFromCell(row, "A", null);

    // -- ASSERT --
    assertNotNull(result);
    assertEquals(date.toString(), result);
  }

  @DisplayName("Test get a date cell as string with a specific time pattern")
  @Test
  void testGetDateAsStringWithTimePattern() throws Exception {
    // -- PREPARE --
    Date date = Date.from(LocalDateTime.of(2025, 1, 2, 12, 0).toInstant(ZoneOffset.UTC));
    cell.setCellValue(date);
    // -- EXECUTE --
    String result = InjectImportUtils.getDateAsStringFromCell(row, "A", "DD/MM/YY HH:mm:ss");

    // -- ASSERT --
    assertNotNull(result);
    assertEquals(new SimpleDateFormat("dd/MM/yy HH:mm:ss").format(date), result);
  }

  @DisplayName("Test get a date cell as string when no column specified")
  @Test
  void testGetDateAsStringWhenNoColumn() throws Exception {
    // -- PREPARE --
    cell.setCellValue(Date.from(LocalDateTime.of(2025, 1, 1, 12, 0).toInstant(ZoneOffset.UTC)));
    // -- EXECUTE --
    String result = InjectImportUtils.getDateAsStringFromCell(row, "", null);

    // -- ASSERT --
    assertNotNull(result);
    assertEquals("", result);
  }

  @DisplayName("Test get a date cell as string when it's already plain text")
  @Test
  void testGetDateAsStringWhenAlreadyString() throws Exception {
    // -- PREPARE --
    cell.setCellValue("J+1");
    // -- EXECUTE --
    String result = InjectImportUtils.getDateAsStringFromCell(row, "A", null);

    // -- ASSERT --
    assertNotNull(result);
    assertEquals("J+1", result);
  }

  @DisplayName("Test get a string cell as string")
  @Test
  void testGetCellValueAsString() throws Exception {
    // -- PREPARE --
    cell.setCellValue("A value");
    // -- EXECUTE --
    String result = InjectImportUtils.getValueAsString(row, "A");

    // -- ASSERT --
    assertNotNull(result);
    assertEquals("A value", result);
  }

  @DisplayName("Test get a string cell as string when no column specified")
  @Test
  void testGetCellValueAsStringWhenNoColumn() throws Exception {
    // -- PREPARE --
    cell.setCellValue("A value");
    // -- EXECUTE --
    String result = InjectImportUtils.getValueAsString(row, "");

    // -- ASSERT --
    assertNotNull(result);
    assertEquals("", result);
  }

  @DisplayName("Test get a numeric cell as string")
  @Test
  void testGetCellValueAsStringWhenAlreadyString() throws Exception {
    // -- PREPARE --
    cell.setCellValue(10.0);
    // -- EXECUTE --
    String result = InjectImportUtils.getValueAsString(row, "A");

    // -- ASSERT --
    assertNotNull(result);
    assertEquals("10.0", result);
  }

  @DisplayName("Test get a double cell as string")
  @Test
  void testGetCellDoubleAsString() throws Exception {
    // -- PREPARE --
    cell.setCellValue("10.0");
    // -- EXECUTE --
    Double result = InjectImportUtils.getValueAsDouble(row, "A");

    // -- ASSERT --
    assertNotNull(result);
    assertEquals(10.0, result);
  }

  @DisplayName("Test get a double cell as string when no column specified")
  @Test
  void testGetCellDoubleAsStringWhenNoColumn() throws Exception {
    // -- PREPARE --
    cell.setCellValue("A value");
    // -- EXECUTE --
    Double result = InjectImportUtils.getValueAsDouble(row, "");

    // -- ASSERT --
    assertNotNull(result);
    assertEquals(0.0, result);
  }

  @DisplayName("Test get a double cell when it's already a double")
  @Test
  void testGetCellDoubleAsStringWhenAlreadyString() throws Exception {
    // -- PREPARE --
    cell.setCellValue(10.0);
    // -- EXECUTE --
    Double result = InjectImportUtils.getValueAsDouble(row, "A");

    // -- ASSERT --
    assertNotNull(result);
    assertEquals(10.0, result);
  }

  @DisplayName("Test get a string cell and convert it to HTML")
  @Test
  void testExtractAndConvertCellAsHTML() throws Exception {
    // -- PREPARE --
    cell.setCellValue("Test\nTest");
    RuleAttribute ruleAttribute = MockMapperUtils.createRuleAttribute();
    ruleAttribute.setColumns("A");
    // -- EXECUTE --
    String result =
        InjectImportUtils.extractAndConvertStringColumnValue(
            row, ruleAttribute, Map.of("Test", json));

    // -- ASSERT --
    assertNotNull(result);
    assertEquals("Test<br/>Test", result);
  }

  @DisplayName("Test get a string cell and keep it as plain text")
  @Test
  void testExtractWithoutConvertingCellAsHTML() throws Exception {
    // -- PREPARE --
    cell.setCellValue("Test\nTest");
    RuleAttribute ruleAttribute = MockMapperUtils.createRuleAttribute();
    ruleAttribute.setColumns("A");
    json.put("richText", false);
    // -- EXECUTE --
    String result =
        InjectImportUtils.extractAndConvertStringColumnValue(
            row, ruleAttribute, Map.of("Test", json));

    // -- ASSERT --
    assertNotNull(result);
    assertEquals("Test\nTest", result);
  }

  @DisplayName("Test get inject date without pattern but with an ISO_DATE_TIME format")
  @Test
  void testGetInjectDateWithoutPattern() throws Exception {
    // -- PREPARE --
    InjectTime injectTime = new InjectTime();
    injectTime.setUnformattedDate(LocalDateTime.of(2025, 1, 1, 12, 0, 0).toString());
    // -- EXECUTE --
    Temporal result = InjectImportUtils.getInjectDate(injectTime, null);

    // -- ASSERT --
    assertNotNull(result);
    assertEquals(LocalDateTime.of(2025, 1, 1, 12, 0, 0), result);
  }

  @DisplayName("Test get inject time without pattern but with an ISO_TIME format")
  @Test
  void testGetInjectTimeWithoutPattern() throws Exception {
    // -- PREPARE --
    InjectTime injectTime = new InjectTime();
    injectTime.setUnformattedDate(LocalTime.of(12, 0, 0).format(DateTimeFormatter.ISO_TIME));
    // -- EXECUTE --
    Temporal result = InjectImportUtils.getInjectDate(injectTime, null);

    // -- ASSERT --
    assertNotNull(result);
    assertEquals(LocalTime.of(12, 0, 0), result);
  }

  @DisplayName("Test get inject time without pattern and in an unknown format")
  @Test
  void testGetInjectTimeUndetected() throws Exception {
    // -- PREPARE --
    InjectTime injectTime = new InjectTime();
    injectTime.setUnformattedDate("13 heures et demi");
    // -- EXECUTE --
    Temporal result = InjectImportUtils.getInjectDate(injectTime, null);

    // -- ASSERT --
    assertNull(result);
  }

  @DisplayName("Test get inject date and time with a specified pattern")
  @Test
  void testGetInjectDateTimeWithPattern() throws Exception {
    // -- PREPARE --
    InjectTime injectTime = new InjectTime();
    injectTime.setUnformattedDate("25/01/20 13h05:52");
    // -- EXECUTE --
    Temporal result = InjectImportUtils.getInjectDate(injectTime, "yy/MM/dd HH'h'mm:ss");

    // -- ASSERT --
    assertNotNull(result);
    assertEquals(LocalDateTime.of(2025, 1, 20, 13, 5, 52), result);
  }

  @DisplayName("Test get inject time with a specified pattern")
  @Test
  void testGetInjectTimeWithPattern() throws Exception {
    // -- PREPARE --
    InjectTime injectTime = new InjectTime();
    injectTime.setUnformattedDate("13h05:52");
    // -- EXECUTE --
    Temporal result = InjectImportUtils.getInjectDate(injectTime, "HH'h'mm:ss");

    // -- ASSERT --
    assertNotNull(result);
    assertEquals(LocalTime.of(13, 5, 52), result);
  }

  @DisplayName("Test get inject time with a specified pattern that does not match")
  @Test
  void testGetInjectTimeUndetectedWithTimePattern() throws Exception {
    // -- PREPARE --
    InjectTime injectTime = new InjectTime();
    injectTime.setUnformattedDate("13 heures et demi");
    // -- EXECUTE --
    Temporal result = InjectImportUtils.getInjectDate(injectTime, "HH'h'mm:ss");

    // -- ASSERT --
    assertNull(result);
  }
}
