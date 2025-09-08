package io.openbas.stix.parsing;

import static io.openbas.stix.types.Hashes.parseHashes;
import static io.openbas.stix.types.StixString.parseString;
import static io.openbas.stix.types.inner.ExternalReference.parseExternalReference;
import static io.openbas.stix.types.inner.KillChainPhase.parseKillChainPhase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openbas.stix.objects.Bundle;
import io.openbas.stix.objects.ObjectBase;
import io.openbas.stix.types.*;
import io.openbas.stix.types.Dictionary;
import io.openbas.stix.types.Integer;
import io.openbas.stix.types.enums.HashingAlgorithms;
import io.openbas.stix.types.inner.ExternalReference;
import io.openbas.stix.types.inner.KillChainPhase;
import java.time.Instant;
import java.util.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class Parser {
  private final ObjectMapper mapper;

  public Bundle parseBundle(java.lang.String source)
      throws JsonProcessingException, ParsingException {
    JsonNode jsonNode = mapper.readTree(source);
    if (!jsonNode.has("type") || !"bundle".equals(jsonNode.get("type").asText())) {
      throw new ParsingException("Invalid STIX: not a STIX bundle");
    }

    List<ObjectBase> objects = new ArrayList<>();
    Identifier id = new Identifier(jsonNode.get("id").asText());
    Iterator<JsonNode> iterator = jsonNode.get("objects").elements();
    while (iterator.hasNext()) {
      JsonNode objectNode = iterator.next();
      objects.add(parseObject(objectNode));
    }
    return new Bundle(id, objects);
  }

  public ObjectBase parseObject(String json) throws JsonProcessingException, ParsingException {
    return parseObject(mapper.readTree(json));
  }

  private ObjectBase parseObject(JsonNode propertyNode)
      throws JsonProcessingException, ParsingException {
    return ObjectFactory.instantiateFromProps(jsonObjectToPropertyMap(propertyNode));
  }

  private BaseType<?> parseProperty(String propertyName, JsonNode propertyNode)
      throws JsonProcessingException, ParsingException {
    switch (propertyNode.getNodeType()) {
      case OBJECT:
        if (propertyName.endsWith("hashes")) {
          return parseHashes(propertyNode);
        } else if (propertyNode.has("source_name")) {
          return new Complex<>(parseExternalReference(propertyNode));
        } else if (propertyNode.has("kill_chain_name")) {
          return new Complex<>(parseKillChainPhase(propertyNode));
        } else {
          return new Dictionary(jsonObjectToPropertyMap(propertyNode));
        }
      case ARRAY:
        List<BaseType<?>> list = new ArrayList<>();
        Iterator<JsonNode> iterator = propertyNode.elements();
        while (iterator.hasNext()) {
          JsonNode node = iterator.next();
          list.add(parseProperty(propertyName, node));
        }
        return new io.openbas.stix.types.List<>(list);
      case STRING:
        return parseString(propertyName, propertyNode);
      case NUMBER:
        return new io.openbas.stix.types.Integer(propertyNode.asInt());
      case BOOLEAN:
        return new io.openbas.stix.types.Boolean(propertyNode.asBoolean());
      case NULL:
        return null;
      default:
        throw new ParsingException("Invalid STIX: not a STIX property");
    }
  }

  private Map<String, BaseType<?>> jsonObjectToPropertyMap(JsonNode propertyNode)
      throws JsonProcessingException, ParsingException {
    Map<String, BaseType<?>> properties = new HashMap<>();
    Iterator<Map.Entry<String, JsonNode>> iterator = propertyNode.fields();
    while (iterator.hasNext()) {
      Map.Entry<String, JsonNode> entry = iterator.next();
      properties.put(entry.getKey(), parseProperty(entry.getKey(), entry.getValue()));
    }
    return properties;
  }
}
