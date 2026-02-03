package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.openaev.database.model.StepStateEntries;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class StepServiceParserTest {

  Gson gson = new Gson();
  @Autowired StepStateService stepStateService;

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
  public void getField() {

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
  public void getFieldOutput() {

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
  public void getFieldsOutput() {

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
              "message": {
                "stderr": "",
                "stdout": "filigran\\n",
                "exit_code": 0
              },
              "agent_id": "ba727180-73db-4c37-940b-c4eb279a23a8"
            }
          ]
        }
        """;

    Map<String, Object> fields = StepService.getFields(jsonString, "outputs.message.stdout");
    Map<String, Object> inputs = new HashMap<>();

    for (Map.Entry<String, Object> entry : fields.entrySet()) {
      String key = entry.getKey();

      if (!key.equals("outputs.message.stdout")) {
        Pattern p = Pattern.compile("^(outputs\\.\\d+)");
        Matcher m = p.matcher(key);
        if (m.find()) {
          String keyOutputs = m.group(1);
          String outputUsed = StepService.getField(jsonString, keyOutputs);
          JsonElement elements = gson.toJsonTree(outputUsed);
          JsonObject jsonObject = new JsonObject();
          jsonObject.add("outputs", elements);

          inputs.put(key, jsonObject.toString());
        }
      }
    }
    System.out.println(inputs);
    long hash = hashExecution("user" + "192.168.123.131");
    long hash1 = hashExecution("filigran" + "192.168.123.132");
    long hash2 = hashExecution("user" + "192.168.123.131");
    long hash3 = hashExecution("filigran" + "192.168.123.132");
    System.out.println(hash);
    System.out.println(hash1);
    System.out.println(hash2);
    System.out.println(hash3);
  }

  private long hashExecution(String value) {
    return Hashing.murmur3_128().hashString(value, StandardCharsets.UTF_8).asLong();
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

    System.out.println("output1");
    stepStateService.newOutput(
        stateEntries,
        "{\"outputs\": {\"message\": {\"stdout\": \"filigran\"}}}",
        "outputs.message.stdout",
        "stdout");

    assertEquals(1, stateEntries.getInputs().size());
    assertTrue(stateEntries.getInputs().get(0).getValues().contains("filigran"));

    System.out.println("output2");
    stepStateService.newOutput(
        stateEntries,
        "{\"outputs\": {\"message\": {\"exit\": \"0\"}}}",
        "outputs.message.exit",
        "exit");

    assertEquals(2, stateEntries.getInputs().size());
    assertTrue(stateEntries.getInputByKey("exit").getValues().contains("0"));

    System.out.println("output3");
    stepStateService.newOutput(
        stateEntries,
        "{\"outputs\": {\"message\": {\"port\": \"445\", \"ip\": \"192.168.123.131\"}}}",
        "outputs.message.port+outputs.message.ip",
        "port+ip");

    assertEquals(1, stateEntries.getCorrelated().size());
    StepStateEntries.Correlated c1 = stateEntries.getCorrelated().get(0);
    assertTrue(c1.getValues().contains(new StepStateEntries.Pair("port", "445")));
    assertTrue(c1.getValues().contains(new StepStateEntries.Pair("ip", "192.168.123.131")));

    System.out.println("output4");
    stepStateService.newOutput(
        stateEntries,
        "{\"outputs\": {\"message\": {\"port\": \"445\", \"ip\": \"192.168.123.131\"}}}",
        "outputs.message.port+outputs.message.ip",
        "port+ip");

    assertEquals(
        1, stateEntries.getCorrelated().size(), "Computed identique ne doit pas être dupliqué");

    System.out.println("output5");
    stepStateService.newOutput(
        stateEntries,
        "{\"outputs\": {\"message\": {\"port\": \"135\", \"ip\": \"192.168.123.132\"}}}",
        "outputs.message.port+outputs.message.ip",
        "port+ip");

    assertEquals(2, stateEntries.getCorrelated().size());
  }
}
