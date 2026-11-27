package io.openaev.utils.fixtures.opencti;

import static io.openaev.opencti.service.OpenCTIService.JWKS_FIELD_UNKNOWN_ERROR;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.opencti.client.response.Response;
import io.openaev.opencti.client.response.fields.Error;
import io.openaev.opencti.client.response.fields.Location;
import java.util.List;
import org.apache.hc.core5.http.HttpStatus;

public class ResponseFixture {
  public static Response getOkResponse() {
    Response r = new Response();
    r.setStatus(HttpStatus.SC_OK);
    r.setData(new ObjectMapper().createObjectNode());
    return r;
  }

  public static Response getErrorResponse() {
    Response r = new Response();
    r.setStatus(HttpStatus.SC_OK); // misleading ! It can absolutely happen.
    r.setData(new ObjectMapper().createObjectNode());
    Error err = new Error();
    err.setMessage("Oops! It did it again.");
    Location loc = new Location();
    loc.setColumn(1);
    loc.setLine(2);
    err.setLocations(List.of(loc));
    r.setErrors(List.of(err));
    return r;
  }

  /** What an OpenCTI 6.x without the jwks field answers (GraphQL validation error). */
  public static Response getJwksFieldUnknownResponse() {
    Response r = new Response();
    r.setStatus(HttpStatus.SC_BAD_REQUEST);
    Error err = new Error();
    err.setMessage(JWKS_FIELD_UNKNOWN_ERROR + " on type \"Connector\".");
    r.setErrors(List.of(err));
    return r;
  }
}
