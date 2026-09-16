package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Supported AWS regions for cloud credentials. */
public enum AwsRegion {
  US_EAST_2("US East (Ohio)", "us-east-2"),
  US_EAST_1("US East (N. Virginia)", "us-east-1"),
  US_WEST_1("US West (N. California)", "us-west-1"),
  US_WEST_2("US West (Oregon)", "us-west-2"),
  AF_SOUTH_1("Africa (Cape Town)", "af-south-1"),
  AP_EAST_1("Asia Pacific (Hong Kong)", "ap-east-1"),
  AP_SOUTH_2("Asia Pacific (Hyderabad)", "ap-south-2"),
  AP_SOUTHEAST_3("Asia Pacific (Jakarta)", "ap-southeast-3"),
  AP_SOUTHEAST_5("Asia Pacific (Malaysia)", "ap-southeast-5"),
  AP_SOUTHEAST_4("Asia Pacific (Melbourne)", "ap-southeast-4"),
  AP_SOUTH_1("Asia Pacific (Mumbai)", "ap-south-1"),
  AP_SOUTHEAST_6("Asia Pacific (New Zealand)", "ap-southeast-6"),
  AP_NORTHEAST_3("Asia Pacific (Osaka)", "ap-northeast-3"),
  AP_NORTHEAST_2("Asia Pacific (Seoul)", "ap-northeast-2"),
  AP_SOUTHEAST_1("Asia Pacific (Singapore)", "ap-southeast-1"),
  AP_SOUTHEAST_2("Asia Pacific (Sydney)", "ap-southeast-2"),
  AP_EAST_2("Asia Pacific (Taipei)", "ap-east-2"),
  AP_SOUTHEAST_7("Asia Pacific (Thailand)", "ap-southeast-7"),
  AP_NORTHEAST_1("Asia Pacific (Tokyo)", "ap-northeast-1"),
  CA_CENTRAL_1("Canada (Central)", "ca-central-1"),
  CA_WEST_1("Canada West (Calgary)", "ca-west-1"),
  EU_CENTRAL_1("Europe (Frankfurt)", "eu-central-1"),
  EU_WEST_1("Europe (Ireland)", "eu-west-1"),
  EU_WEST_2("Europe (London)", "eu-west-2"),
  EU_SOUTH_1("Europe (Milan)", "eu-south-1"),
  EU_WEST_3("Europe (Paris)", "eu-west-3"),
  EU_SOUTH_2("Europe (Spain)", "eu-south-2"),
  EU_NORTH_1("Europe (Stockholm)", "eu-north-1"),
  EU_CENTRAL_2("Europe (Zurich)", "eu-central-2"),
  IL_CENTRAL_1("Israel (Tel Aviv)", "il-central-1"),
  MX_CENTRAL_1("Mexico (Central)", "mx-central-1"),
  ME_SOUTH_1("Middle East (Bahrain)", "me-south-1"),
  ME_CENTRAL_1("Middle East (UAE)", "me-central-1"),
  SA_EAST_1("South America (Sao Paulo)", "sa-east-1"),
  US_GOV_EAST_1("AWS GovCloud (US-East)", "us-gov-east-1"),
  US_GOV_WEST_1("AWS GovCloud (US-West)", "us-gov-west-1");

  private final String label;
  private final String code;

  AwsRegion(String label, String code) {
    this.label = label;
    this.code = code;
  }

  public String label() {
    return label;
  }

  @JsonValue
  public String code() {
    return code;
  }

  public static List<String> codes() {
    return Arrays.stream(values()).map(AwsRegion::code).toList();
  }

  @JsonCreator
  public static AwsRegion fromJson(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    String normalizedValue = value.trim();
    String normalizedEnumLike = normalizedValue.replace('-', '_').toUpperCase(Locale.ROOT);

    return Arrays.stream(values())
        .filter(
            region ->
                region.code.equalsIgnoreCase(normalizedValue)
                    || region.name().equals(normalizedEnumLike))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unsupported AWS region: " + value));
  }
}
