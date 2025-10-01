/*
Copyright (c) 2021-2024 Filigran SAS

This file is part of the OpenAEV Enterprise Edition ("EE") and is
licensed under the OpenAEV Enterprise Edition License (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

https://github.com/OpenAEV-Platform/openaev/blob/master/LICENSE

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*/

package io.openaev.ee;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class License {

  @JsonProperty("license_is_enterprise")
  private boolean isLicenseEnterprise = false;

  @JsonProperty("license_is_valid_cert")
  private boolean isValidCert = false;

  @JsonProperty("license_type")
  private LicenseTypeEnum type;

  @JsonProperty("license_creator")
  private String creator;

  @JsonProperty("license_is_valid_product")
  private boolean isValidProduct = false;

  @JsonProperty("license_customer")
  private String customer;

  @JsonProperty("license_platform")
  private String platform;

  @JsonProperty("license_is_platform_match")
  private boolean isPlatformMatch = false;

  @JsonProperty("license_is_global")
  private boolean isGlobalLicense = false;

  @JsonProperty("license_is_expired")
  private boolean isLicenseExpired = true;

  @JsonProperty("license_start_date")
  private Instant startDate;

  @JsonProperty("license_expiration_date")
  private Instant expirationDate;

  @JsonProperty("license_is_prevention")
  private boolean isLicensePrevention;

  @JsonProperty("license_is_validated")
  private boolean isLicenseValidated = false;

  @JsonProperty("license_is_by_configuration")
  private boolean isLicenseByConfiguration = false;

  @JsonProperty("license_is_extra_expiration")
  private boolean extraExpiration = false;

  @JsonProperty("license_extra_expiration_days")
  private long extraExpirationDays = 0;

  @Override
  public String toString() {
    return "License{"
        + "isLicenseEnterprise="
        + isLicenseEnterprise
        + ", isValidCert="
        + isValidCert
        + ", type='"
        + type
        + '\''
        + ", creator='"
        + creator
        + '\''
        + ", isValidProduct="
        + isValidProduct
        + ", customer='"
        + customer
        + '\''
        + ", platform='"
        + platform
        + '\''
        + ", isPlatformMatch="
        + isPlatformMatch
        + ", isGlobalLicense="
        + isGlobalLicense
        + ", isLicenseExpired="
        + isLicenseExpired
        + ", startDate="
        + startDate
        + ", expirationDate="
        + expirationDate
        + ", isLicensePrevention="
        + isLicensePrevention
        + ", isLicenseValidated="
        + isLicenseValidated
        + ", isLicenseByConfiguration="
        + isLicenseByConfiguration
        + '}';
  }
}
