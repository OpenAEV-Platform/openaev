package io.openaev.api.stix_process;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.stix_process.IocValidationEventReader.Read;
import io.openaev.helper.ObjectMapperHelper;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IocValidationEventReaderTest {

  private static final ObjectMapper MAPPER = ObjectMapperHelper.openAEVJsonMapper();
  private static final String HEAD =
      "{\"internal\":{\"applicant_id\":null,\"work_id\":\"work_1\"},\"event\":{\"entity_id\":\"e1\","
          + "\"stix_objects\":\"";

  private static InputStream bytes(String body) {
    return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
  }

  /** The head of an event, then a bundle string that never ends, counting the bytes served. */
  private static InputStream endless(AtomicLong served) {
    byte[] head = HEAD.getBytes(StandardCharsets.UTF_8);
    return new InputStream() {
      @Override
      public int read() {
        long position = served.getAndIncrement();
        return position < head.length ? head[(int) position] : 'a';
      }

      @Override
      public int read(byte[] buffer, int offset, int length) {
        for (int index = 0; index < length; index++) {
          buffer[offset + index] = (byte) read();
        }
        return length;
      }
    };
  }

  @Test
  @DisplayName("reads an event within the bound")
  void given_eventWithinTheBound_should_readIt() throws Exception {
    String body = HEAD + "{}\"}}";
    Read read = IocValidationEventReader.read(bytes(body), body.length(), MAPPER);
    assertThat(read.refusal()).isNull();
    assertThat(read.event().getInternal().getWorkId()).isEqualTo("work_1");
    assertThat(read.event().getEvent().getStixObjects()).isEqualTo("{}");
  }

  @Test
  @DisplayName("reads no event from an empty body or a JSON null, without refusing it")
  void given_emptyBodyOrNull_should_readNoEvent() throws Exception {
    for (String body : new String[] {"", "null"}) {
      Read read = IocValidationEventReader.read(bytes(body), body.length(), MAPPER);
      assertThat(read.event()).as(body).isNull();
      assertThat(read.refusal()).as(body).isNull();
    }
  }

  @Test
  @DisplayName("refuses a declared length above the bound after reading only the first bytes")
  void given_declaredLengthAboveTheBound_should_readOnlyTheFirstBytes() throws Exception {
    AtomicLong served = new AtomicLong();
    Read read = IocValidationEventReader.read(endless(served), 10_000, MAPPER, 1_000);
    assertThat(read.event()).isNull();
    assertThat(read.refusal()).isEqualTo(IocValidationEventReader.TOO_LARGE);
    assertThat(read.workId()).isEqualTo("work_1");
    assertThat(served.get()).isEqualTo(IocValidationEventReader.PREFIX_BYTES);
  }

  @Test
  @DisplayName("stops reading a chunked body at the bound, whatever it would send next")
  void given_chunkedBodyAboveTheBound_should_stopAtTheBound() throws Exception {
    AtomicLong served = new AtomicLong();
    long limit = 1_000_000;
    Read read = IocValidationEventReader.read(endless(served), -1, MAPPER, limit);
    assertThat(read.event()).isNull();
    assertThat(read.refusal()).isEqualTo(IocValidationEventReader.TOO_LARGE);
    assertThat(read.workId()).isEqualTo("work_1");
    // Jackson reads in chunks of a few kilobytes: one chunk at most past the bound
    assertThat(served.get()).isGreaterThan(limit).isLessThan(limit + 64 * 1024);
  }

  @Test
  @DisplayName("refuses a body that is not a JSON event, naming its work when it can")
  void given_malformedBody_should_refuseIt() throws Exception {
    String body = "{\"internal\":{\"work_id\":\"work_2\"},\"event\":{\"stix_objects\": nope}}";
    Read read = IocValidationEventReader.read(bytes(body), body.length(), MAPPER);
    assertThat(read.event()).isNull();
    assertThat(read.refusal()).isEqualTo(IocValidationEventReader.NOT_AN_EVENT);
    assertThat(read.workId()).isEqualTo("work_2");

    Read garbage = IocValidationEventReader.read(bytes("<xml/>"), 6, MAPPER);
    assertThat(garbage.refusal()).isEqualTo(IocValidationEventReader.NOT_AN_EVENT);
    assertThat(garbage.workId()).isNull();
  }

  @Test
  @DisplayName("finds the work only in the internal part of the event")
  void given_firstBytes_should_findTheWorkOfTheInternalPart() {
    byte[] decoy =
        "{\"event\":{\"work_id\":\"decoy\"},\"internal\":{\"work_id\":\"work_3\"}}"
            .getBytes(StandardCharsets.UTF_8);
    assertThat(IocValidationEventReader.workIdOf(decoy, decoy.length, MAPPER)).isEqualTo("work_3");
    byte[] cut = HEAD.substring(0, 20).getBytes(StandardCharsets.UTF_8);
    assertThat(IocValidationEventReader.workIdOf(cut, cut.length, MAPPER)).isNull();
  }
}
