package io.openaev;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.openaev.service.chaining.StepService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
public class StepServiceTest {

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

    JsonObject oNew = StepService.useJson(jsonString, u, StepService.ACTION_JSON.REPLACE);
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
}
