package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.openaev.database.model.StepStateEntries;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class StepServiceParserTest {

  Gson gson = new Gson();
  @InjectMocks StepStateService stepStateService;

  @Test
  public void updateFields() {

    String jsonString =
        """
                    {"name":"Hello",
                    "name2":"World",
                    "obj":{
                            "id":1,
                            "attack" :[ "first-attack", "second"],
                            "asset" : ["asset1","asset2"],
                            "user": [
                            {"username":"user1", "password":"psd1"},
                            {"username":"user2", "password":"psd2"}
                            ]
                          }
                    }
        """;

    Map<String, Object> u = new HashMap<>();
    u.put("name", "0000");
    u.put("obj.id", 9999);
    u.put("obj.attack.0", "new-attack");
    u.put("obj.asset", "new-asset");
    u.put("obj.user.0.username", "userChangeName1");
    u.put("obj.user.1.username", "userChangeName2");

    JsonObject oNew = StepService.useJson(jsonString, u, StepService.ACTION_JSON.REPLACE);
    String result =
        "{\"name2\":\"World\",\"obj\":{\"attack\":[\"new-attack\",\"second\"],\"user\":[{\"password\":\"psd1\",\"username\":\"userChangeName1\"},{\"password\":\"psd2\",\"username\":\"userChangeName2\"}],\"id\":9999,\"asset\":[\"new-asset\"]},\"name\":\"0000\"}";
    assertEquals(result, oNew.toString());
  }

  @Test
  public void getFields() {

    String jsonString =
        """
                    {"name":"Hello",
                    "name2":"World",
                    "obj":{
                            "id":1,
                            "attack" :[ "first-attack", "second"],
                            "asset" : ["asset1","asset2"],
                            "user": [
                            {"username":"user1", "password":"psd1"},
                            {"username":"user2", "password":"psd2"}
                            ]
                          }
                    }
        """;
    Map<String, Object> u = new HashMap<>();
    u.put("name", null);
    u.put("obj.id", null);
    u.put("obj.attack.0", null);
    u.put("obj.asset", null);
    u.put("obj.user.0.username", null);
    u.put("obj.user.1.username", null);

    StepService.useJson(jsonString, u, StepService.ACTION_JSON.GET);
    String result =
        "{\"obj.attack.0\":\"first-attack\",\"obj.asset\":[\"asset1\",\"asset2\"],\"name\":\"Hello\",\"obj.user.1.username\":\"user2\",\"obj.id\":1,\"obj.user.0.username\":\"user1\"}";
    String newMap = gson.toJson(u);
    assertEquals(result, newMap);
  }

  @Test
  public void getFieldFrom_ObjectAndArray() {

    String jsonString =
        """
                    {"name":"Hello",
                    "name2":"World",
                    "value": null,
                    "obj":{
                            "id":1,
                            "attack" :[ "first-attack", "second"],
                            "asset" : ["asset1","asset2"],
                            "user": [
                            {"username":"user1", "password":"psd1"},
                            {"username":"user2", "password":"psd2"}
                            ]
                          }
                    }
        """;

    String value = StepService.getField(jsonString, "obj.user.0.username");
    String result = "user1";
    assertEquals(result, value);

    String newValue = StepService.getField(jsonString, "value");
    assertNull(newValue);
  }

  @Test
  public void shouldIgnoreOutputsWithoutRequestedField() {

    String jsonString =
        """
        {
          "outputs": [
            {
              "message": "Implant is up and starting execution",
              "agent_id": "ba727180-73db-4c37-940b-c4eb279a23a8"
            },
            {
              "message": {
                "stderr": "",
                "stdout": "filigran\\n",
                "exit_code": 0
              },
              "agent_id": "ba727180-73db-4c37-940b-c4eb279a23a8"
            },
            {
              "message": "Payload completed",
              "agent_id": "ba727180-73db-4c37-940b-c4eb279a23a8"
            }
          ]
        }
        """;

    String value = StepService.getField(jsonString, "outputs.message.stdout");
    String result =
        """
        filigran
        """;
    assertEquals(result, value);
  }

  @Test
  public void shouldGetLastOutputsFind() {

    String jsonString =
        """
        {
          "outputs": [
            {
              "message": "Implant is up and starting execution",
              "agent_id": "ba727180-73db-4c37-940b-c4eb279a23a8"
            },
            {
              "message": {
                "stderr": "",
                "stdout": "filigran1\\n",
                "exit_code": 0
              },
              "agent_id": "ba727180-73db-4c37-940b-c4eb279a23a8"
            },
            {
              "message": {
                "stderr": "",
                "stdout": "filigran2\\n",
                "exit_code": 0
              },
              "agent_id": "ba727180-73db-4c37-940b-c4eb279a23a8"
            },
            {
              "message": "Payload completed",
              "agent_id": "ba727180-73db-4c37-940b-c4eb279a23a8"
            }
          ]
        }
        """;

    String value = StepService.getField(jsonString, "outputs.message.stdout");
    String result =
        """
        filigran2
        """;
    assertEquals(result, value, "Get last field value find");
  }

  @Test
  public void shouldGetAllOutputsPathFind() {

    String jsonString =
        """
        {
          "outputs": [
            {
              "message": "Implant is up and starting execution",
              "agent_id": "ba727180-73db-4c37-940b-c4eb279a23a8"
            },
            {
              "message": {
                "stderr": "",
                "stdout": "filigran1\\n",
                "exit_code": 0
              },
              "agent_id": "ba727180-73db-4c37-940b-c4eb279a23a8"
            },
            {
              "message": {
                "stderr": "",
                "stdout": "filigran2\\n",
                "exit_code": 0
              },
              "agent_id": "ba727180-73db-4c37-940b-c4eb279a23a8"
            },
            {
              "message": "Payload completed",
              "agent_id": "ba727180-73db-4c37-940b-c4eb279a23a8"
            }
          ]
        }
        """;

    Map<String, Object> values = StepService.getFields(jsonString, "outputs.message.stdout");
    assertEquals(3, values.size(), "Get all path for requested field");
  }

  @Test
  public void testNewOutputWithComputed() {
    // Création du stateEntries vide
    Set<String> executionKeys = new HashSet<>();
    executionKeys.add("ip");
    executionKeys.add("port");
    executionKeys.add("stdout");
    executionKeys.add("exit");
    StepStateEntries stateEntries =
        new StepStateEntries(new ArrayList<>(), new ArrayList<>(), new HashSet<>(), executionKeys);

    stepStateService.newOutput(
        stateEntries,
        "{\"outputs\": {\"message\": {\"stdout\": \"filigran\"}}}",
        "outputs.message.stdout",
        "stdout");

    assertEquals(1, stateEntries.getInputs().size());
    assertTrue(stateEntries.getInputs().get(0).getValues().contains("filigran"));

    stepStateService.newOutput(
        stateEntries,
        "{\"outputs\": {\"message\": {\"exit\": \"0\"}}}",
        "outputs.message.exit",
        "exit");

    assertEquals(2, stateEntries.getInputs().size());
    assertTrue(stateEntries.getInputByKey("exit").getValues().contains("0"));

    stepStateService.newOutput(
        stateEntries,
        "{\"outputs\": {\"message\": {\"port\": \"445\", \"ip\": \"192.168.123.131\"}}}",
        "outputs.message.port+outputs.message.ip",
        "port+ip");

    assertEquals(1, stateEntries.getCorrelated().size());
    StepStateEntries.Correlated c1 = stateEntries.getCorrelated().get(0);
    assertTrue(c1.getValues().contains(new StepStateEntries.Pair("port", "445")));
    assertTrue(c1.getValues().contains(new StepStateEntries.Pair("ip", "192.168.123.131")));

    stepStateService.newOutput(
        stateEntries,
        "{\"outputs\": {\"message\": {\"port\": \"445\", \"ip\": \"192.168.123.131\"}}}",
        "outputs.message.port+outputs.message.ip",
        "port+ip");

    assertEquals(
        1, stateEntries.getCorrelated().size(), "Computed identique ne doit pas être dupliqué");

    stepStateService.newOutput(
        stateEntries,
        "{\"outputs\": {\"message\": {\"port\": \"135\", \"ip\": \"192.168.123.132\"}}}",
        "outputs.message.port+outputs.message.ip",
        "port+ip");

    assertEquals(2, stateEntries.getCorrelated().size());
  }
}
