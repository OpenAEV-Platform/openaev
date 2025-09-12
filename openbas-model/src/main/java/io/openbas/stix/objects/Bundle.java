package io.openbas.stix.objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openbas.stix.objects.constants.CommonProperties;
import io.openbas.stix.parsing.ParsingException;
import io.openbas.stix.parsing.StixSerialisable;
import io.openbas.stix.types.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.Getter;

public class Bundle implements StixSerialisable {
  private static final String TYPE = "bundle";

  public Bundle(Identifier id, List<ObjectBase> objects) {
    this.id = id;
    this.domainObjects = new ArrayList<>();
    this.relationshipObjects = new ArrayList<>();
    for (ObjectBase obj : objects) {
      switch (obj) {
        case DomainObject sdo -> this.domainObjects.add(sdo);
        case RelationshipObject sro -> this.relationshipObjects.add(sro);
        default -> throw new IllegalArgumentException();
      }
    }
  }

  public List<ObjectBase> findByType(String type) {
    return this.allObjects().stream()
        .filter(o -> o.getProperty(CommonProperties.TYPE.toString()).equals(type))
        .toList();
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

  public List<RelationshipObject> findRelationshipsByTargetRef(Identifier targetRef) {
    return this.relationshipObjects.stream()
        .filter(
            sro ->
                sro.getProperty(RelationshipObject.Properties.TARGET_REF.toString())
                    .equals(targetRef))
        .toList();
  }

  public List<RelationshipObject> findRelationshipsBySourceRef(Identifier sourceRef) {
    return this.relationshipObjects.stream()
        .filter(
            sro ->
                sro.getProperty(RelationshipObject.Properties.SOURCE_REF.toString())
                    .equals(sourceRef))
        .toList();
  }

  private List<ObjectBase> findByIdsWithPossibleMisses(List<Identifier> ids) {
    return this.allObjects().stream()
        .filter(ob -> ids.stream().anyMatch(id -> ob.getId().equals(id)))
        .toList();
  }

  @Getter private final Identifier id;
  @Getter private final String type = TYPE;
  @Getter private final List<DomainObject> domainObjects;
  @Getter private final List<RelationshipObject> relationshipObjects;

  private List<ObjectBase> allObjects() {
    return Stream.concat(domainObjects.stream(), relationshipObjects.stream()).toList();
  }

  @Override
  public JsonNode toStix(ObjectMapper mapper) {
    ObjectNode node = mapper.createObjectNode();
    node.set(CommonProperties.ID.toString(), getId().toStix(mapper));
    node.set(CommonProperties.TYPE.toString(), mapper.valueToTree(getType()));
    node.set(
        "objects", mapper.valueToTree(allObjects().stream().map(ob -> ob.toStix(mapper)).toList()));
    return node;
  }
}
