package io.openaev.utils.fixtures.opencti;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.opencti.client.mutations.QueryTypeFields;
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

  public static Response getSchemaResponseWithJwks() {
    Response r = new Response();
    r.setStatus(HttpStatus.SC_OK);
    QueryTypeFields.ResponsePayload payload = new QueryTypeFields.ResponsePayload();
    QueryTypeFields.ResponsePayload.TypeContent tc =
        new QueryTypeFields.ResponsePayload.TypeContent();
    QueryTypeFields.ResponsePayload.TypeContent.FieldContent fc =
        new QueryTypeFields.ResponsePayload.TypeContent.FieldContent();
    fc.setName("jwks");
    tc.setFields(List.of(fc));
    payload.setTypeContent(tc);
    r.setData(new ObjectMapper().valueToTree(payload));
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
}
