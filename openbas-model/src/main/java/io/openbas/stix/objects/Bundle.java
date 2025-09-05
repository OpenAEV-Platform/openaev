package io.openbas.stix.objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openbas.stix.parsing.ParsingException;
import io.openbas.stix.parsing.StixSerialisable;
import io.openbas.stix.types.Identifier;
import java.util.List;
import lombok.Getter;

public class Bundle implements StixSerialisable {
  private static final String TYPE = "bundle";

  public Bundle(Identifier id, List<ObjectBase> objects) {
    this.id = id;
    this.objects = objects;
  }

  public List<ObjectBase> findByType(String type) {
    return this.getObjects().stream().filter(o -> o.getProperty("type").equals(type)).toList();
  }

  public ObjectBase findById(Identifier id) throws ParsingException {
    return this.findByIds(List.of(id)).stream().findFirst().orElseThrow();
  }

  public List<ObjectBase> findByIds(List<Identifier> ids) throws ParsingException {
    List<ObjectBase> found = findByIdsWithPossibleMisses(ids);
    if (found.isEmpty() || found.size() != ids.size()) {
      throw new ParsingException("some requested ids are missing from the bundle");
    }
    return found;
  }

  private List<ObjectBase> findByIdsWithPossibleMisses(List<Identifier> ids) {
    return this.objects.stream()
        .filter(ob -> ids.stream().anyMatch(id -> ob.getProperty("id").equals(id)))
        .toList();
  }

  @Getter private final Identifier id;
  @Getter private final String type = TYPE;
  @Getter private final List<ObjectBase> objects;

  @Override
  public JsonNode toStix(ObjectMapper mapper) {
    ObjectNode node = mapper.createObjectNode();
    node.set("id", getId().toStix(mapper));
    node.set("type", mapper.valueToTree(getType()));
    node.set(
        "objects", mapper.valueToTree(getObjects().stream().map(ob -> ob.toStix(mapper)).toList()));
    return node;
  }
}
