package io.openaev.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.database.model.ConnectorInstance;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

public interface IntegrationFactory {
  List<Integration> initialise() throws Exception;

  Integration spawn(ConnectorInstance instance)
      throws JsonProcessingException,
          InvocationTargetException,
          NoSuchMethodException,
          InstantiationException,
          IllegalAccessException;
}
