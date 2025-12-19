package io.openaev;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.openaev.service.chaining.StepService;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class StepServiceTest {
  @Autowired StepService stepService;
  Gson gson = new Gson();

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

    JsonObject oNew = stepService.useJson(jsonString, u, StepService.ACTION_JSON.REPLACE);
    String result =
        "{\"name2\":\"World\",\"obj\":{\"attack\":[\"new-attack\",\"second\"],\"user\":[{\"password\":\"psd1\",\"username\":\"userChangeName1\"},{\"password\":\"psd2\",\"username\":\"userChangeName2\"}],\"asset\":[\"new-asset\"],\"id\":9999},\"name\":\"0000\"}";
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

    stepService.useJson(jsonString, u, StepService.ACTION_JSON.GET);
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

    JsonPrimitive value = stepService.getField(jsonString, "obj.user.0.username");
    String result = "user1";
    assertEquals(result, value.getAsString());
  }
}
