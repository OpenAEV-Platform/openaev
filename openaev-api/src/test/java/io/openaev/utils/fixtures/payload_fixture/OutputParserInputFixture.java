package io.openaev.utils.fixtures.payload_fixture;

import io.openaev.database.model.*;
import io.openaev.rest.payload.output_parser.OutputParserInput;

public class OutputParserInputFixture {

  public static OutputParserInput createDefaultOutputParseInput() {
    OutputParserInput outputParserInput = new OutputParserInput();
    outputParserInput.setMode(ParserMode.STDOUT);
    outputParserInput.setType(ParserType.REGEX);
    return outputParserInput;
  }
}
