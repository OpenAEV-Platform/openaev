package io.openaev.api.stix_process;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.opencti.dto.CTIEvent;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;

/**
 * Reads the event of an IOC validation request from its HTTP body within {@link
 * #MAX_REQUEST_BYTES}, counted while the body is read: neither a declared length nor a chunked body
 * makes the server hold more. Above the bound, only the first bytes are kept, enough to find the
 * OpenCTI work of the event (OpenCTI writes its {@code internal} part first) and acknowledge it in
 * error.
 */
final class IocValidationEventReader {

  /**
   * Four bytes per character of the largest bundle the parser accepts: OpenCTI writes UTF-8, at
   * most three bytes per character, and the escaped quotes and backslashes of the bundle string
   * take two, which leaves room for the rest of the event.
   */
  static final long MAX_REQUEST_BYTES = 64L * 1024 * 1024;

  static final int PREFIX_BYTES = 64 * 1024;

  static final String TOO_LARGE = "the request is larger than 64 MiB";
  static final String NOT_AN_EVENT = "the request is not a JSON event";

  /**
   * @param event the event, null for an empty body, a JSON null or a refused request
   * @param workId the OpenCTI work named in the first bytes of a refused request, if any
   * @param refusal why the request was not read, null when it was
   */
  record Read(CTIEvent event, String workId, String refusal) {}

  private IocValidationEventReader() {}

  static Read read(InputStream body, long declaredLength, ObjectMapper mapper) throws IOException {
    return read(body, declaredLength, mapper, MAX_REQUEST_BYTES);
  }

  static Read read(InputStream body, long declaredLength, ObjectMapper mapper, long limit)
      throws IOException {
    if (declaredLength > limit) {
      byte[] prefix = body.readNBytes(PREFIX_BYTES);
      return new Read(null, workIdOf(prefix, prefix.length, mapper), TOO_LARGE);
    }
    BoundedBody bounded = new BoundedBody(body, limit);
    try {
      PushbackInputStream content = new PushbackInputStream(bounded);
      int first = content.read();
      if (first < 0) {
        return new Read(null, null, null);
      }
      content.unread(first);
      return new Read(mapper.readValue(content, CTIEvent.class), null, null);
    } catch (IOException e) {
      if (!bounded.exceeded && !(e instanceof JacksonException)) {
        throw e;
      }
      return new Read(
          null,
          workIdOf(bounded.prefix, bounded.prefixLength, mapper),
          bounded.exceeded ? TOO_LARGE : NOT_AN_EVENT);
    }
  }

  /** The {@code internal.work_id} of an event, read from its first bytes. */
  static String workIdOf(byte[] bytes, int length, ObjectMapper mapper) {
    try (JsonParser parser = mapper.getFactory().createParser(bytes, 0, length)) {
      if (parser.nextToken() != JsonToken.START_OBJECT) {
        return null;
      }
      while (parser.nextToken() == JsonToken.FIELD_NAME) {
        String field = parser.currentName();
        JsonToken value = parser.nextToken();
        if ("internal".equals(field) && value == JsonToken.START_OBJECT) {
          while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String name = parser.currentName();
            if (parser.nextToken() == JsonToken.VALUE_STRING && "work_id".equals(name)) {
              return parser.getText();
            }
            parser.skipChildren();
          }
          return null;
        }
        parser.skipChildren();
      }
    } catch (IOException e) {
      // the first bytes end before the work, or are not JSON
    }
    return null;
  }

  /** Counts the bytes read, keeps the first ones, and fails once the limit is passed. */
  private static final class BoundedBody extends FilterInputStream {

    private final long limit;
    private final byte[] prefix = new byte[PREFIX_BYTES];
    private int prefixLength;
    private long count;
    private boolean exceeded;

    private BoundedBody(InputStream in, long limit) {
      super(in);
      this.limit = limit;
    }

    @Override
    public int read() throws IOException {
      byte[] one = new byte[1];
      return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      int read = super.read(buffer, offset, length);
      if (read > 0) {
        int kept = Math.min(read, PREFIX_BYTES - prefixLength);
        System.arraycopy(buffer, offset, prefix, prefixLength, kept);
        prefixLength += kept;
        count += read;
        if (count > limit) {
          exceeded = true;
          throw new IOException(TOO_LARGE);
        }
      }
      return read;
    }

    @Override
    public long skip(long n) throws IOException {
      return Math.max(read(new byte[(int) Math.min(n, 8192)]), 0);
    }
  }
}
