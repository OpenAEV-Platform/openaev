package io.openaev.utils.fixtures.payload_fixture;

import io.openaev.api.payload.output_parser.OutputParserInput;
import io.openaev.database.model.*;

public class OutputParserInputFixture {

  public static OutputParserInput createDefaultOutputParseInput() {
    OutputParserInput outputParserInput = new OutputParserInput();
    outputParserInput.setMode(ParserMode.STDOUT);
    outputParserInput.setType(ParserType.REGEX);
    return outputParserInput;
  }
}
