package io.openaev.helper;

import io.openaev.annotation.OnDelete;
import io.openaev.database.model.ModelBehaviour;
import io.openaev.utils.reflection.FieldUtils;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

public class OnDeleteHelper {

  public static void processOnDeleteAction(ModelBehaviour entity) {
    Class<?> concreteEntityType = entity.getClass();

    Map<Field, OnDelete> onDeleteActions =
        FieldUtils.getAllDeclaredAnnotatedFieldsWithAnnotation(concreteEntityType, OnDelete.class);

    for (Map.Entry<Field, OnDelete> tuple : onDeleteActions.entrySet()) {
      switch (tuple.getValue().action()) {
        case SET_REFERENCE_NULL -> {
          Object referencing = FieldUtils.getField(entity, tuple.getKey());
          if (referencing == null) {
            continue;
          }
          if (Collection.class.isAssignableFrom(referencing.getClass())) {
            Collection<Object> coll = (Collection<Object>) referencing;
            for (Object obj : coll) {
              if (obj == null) {
                continue;
              }
              Field referencingField =
                  FieldUtils.getFieldByName(obj.getClass(), tuple.getValue().fieldName())
                      .orElseThrow();
              FieldUtils.setField(obj, referencingField, null);
            }
          } else {
            Field referencingField =
                FieldUtils.getFieldByName(referencing.getClass(), tuple.getValue().fieldName())
                    .orElseThrow();
            FieldUtils.setField(referencing, referencingField, null);
          }
        }
      }
    }
  }
}
