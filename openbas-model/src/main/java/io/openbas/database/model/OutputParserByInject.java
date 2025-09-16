package io.openbas.database.model;

public interface OutputParserByInject {

  String getInjectId();

  OutputParser getOutputParser();

  InjectorContract getInjectorContract();
}
