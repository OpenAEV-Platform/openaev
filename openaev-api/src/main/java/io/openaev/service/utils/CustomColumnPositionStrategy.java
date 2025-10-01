package io.openaev.service.utils;

import com.opencsv.bean.ColumnPositionMappingStrategy;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;

public class CustomColumnPositionStrategy<T> extends ColumnPositionMappingStrategy<T> {
  @Override
  public String[] generateHeader(T bean) throws CsvRequiredFieldEmptyException {
    super.generateHeader(bean);
    return super.getColumnMapping();
  }
}
