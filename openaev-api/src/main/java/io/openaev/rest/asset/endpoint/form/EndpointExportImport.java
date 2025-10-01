package io.openaev.rest.asset.endpoint.form;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvBindByPosition;
import io.openaev.database.model.Endpoint;
import lombok.Data;

@Data
public class EndpointExportImport {

  @CsvBindByName(column = "asset_name", required = true)
  @CsvBindByPosition(position = 0)
  private String name;

  @CsvBindByName(column = "asset_description", required = true)
  @CsvBindByPosition(position = 1)
  private String description;

  @CsvBindByName(column = "endpoint_hostname", required = true)
  @CsvBindByPosition(position = 2)
  private String hostname;

  @CsvBindByName(column = "endpoint_ips", required = true)
  @CsvBindByPosition(position = 3)
  private String ips;

  @CsvBindByName(column = "endpoint_platform", required = true)
  @CsvBindByPosition(position = 4)
  private Endpoint.PLATFORM_TYPE platform;

  @CsvBindByName(column = "endpoint_arch", required = true)
  @CsvBindByPosition(position = 5)
  private Endpoint.PLATFORM_ARCH arch;

  @CsvBindByName(column = "endpoint_mac_addresses", required = true)
  @CsvBindByPosition(position = 6)
  private String macAddresses;

  @CsvBindByName(column = "asset_tags", required = true)
  @CsvBindByPosition(position = 7)
  private String tags;

  @CsvBindByName(column = "endpoint_is_eol", required = true)
  @CsvBindByPosition(position = 8)
  private boolean isEol;
}
