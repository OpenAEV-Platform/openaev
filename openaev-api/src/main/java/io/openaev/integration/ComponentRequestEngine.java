package io.openaev.integration;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ComponentRequestEngine {
  public List<Field> validate(ComponentRequest request, List<Field> inputFields) {
    List<Field> validFields = new ArrayList<>();
    for (Field inputField : inputFields) {
      if (inputField.isAnnotationPresent(QualifiedComponent.class)) {
        QualifiedComponent qualifier = inputField.getAnnotation(QualifiedComponent.class);

        if (Arrays.stream(qualifier.identifier()).anyMatch(id -> id.equals(request.identifier()))) {
          validFields.add(inputField);
        }
      }
    }
    return validFields;
  }
}
