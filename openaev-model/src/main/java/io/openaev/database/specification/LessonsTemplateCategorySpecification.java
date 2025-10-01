package io.openaev.database.specification;

import io.openaev.database.model.LessonsTemplateCategory;
import org.springframework.data.jpa.domain.Specification;

public class LessonsTemplateCategorySpecification {

  public static Specification<LessonsTemplateCategory> fromTemplate(String templateId) {
    return (root, query, cb) -> cb.equal(root.get("template").get("id"), templateId);
  }
}
