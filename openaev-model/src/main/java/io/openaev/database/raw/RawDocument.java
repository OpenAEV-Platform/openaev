package io.openaev.database.raw;

import java.util.List;

public interface RawDocument {

  String getDocument_id();

  String getDocument_name();

  String getDocument_description();

  String getDocument_type();

  String getDocument_target();

  List<String> getDocument_tags();

  List<String> getDocument_exercises();

  List<String> getDocument_scenarios();
}
