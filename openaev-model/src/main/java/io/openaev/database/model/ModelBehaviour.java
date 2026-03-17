package io.openaev.database.model;

import io.openaev.helper.OnDeleteHelper;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PreRemove;

@MappedSuperclass
public abstract class ModelBehaviour {
  @PreRemove
  public void preRemove() {
    OnDeleteHelper.processOnDeleteAction(this);
  }
}
