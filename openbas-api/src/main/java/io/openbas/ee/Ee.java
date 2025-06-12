/*
Copyright (c) 2021-2024 Filigran SAS

This file is part of the OpenBAS Enterprise Edition ("EE") and is
licensed under the OpenBAS Enterprise Edition License (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

https://github.com/OpenBAS-Platform/openbas/blob/master/LICENSE

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*/

package io.openbas.ee;

import static io.openbas.database.model.SettingKeys.PLATFORM_ENTERPRISE_LICENSE;
import static io.openbas.database.model.SettingKeys.PLATFORM_INSTANCE;
import static io.openbas.ee.Pem.*;
import static io.openbas.executors.crowdstrike.service.CrowdStrikeExecutorService.CROWDSTRIKE_EXECUTOR_NAME;
import static io.openbas.executors.tanium.service.TaniumExecutorService.TANIUM_EXECUTOR_NAME;
import static io.openbas.helper.StreamHelper.fromIterable;
import static java.util.Optional.ofNullable;

import io.openbas.config.OpenBASConfig;
import io.openbas.database.model.*;
import io.openbas.database.repository.SettingRepository;
import io.openbas.rest.exception.LicenseRestrictionException;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import java.security.cert.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class Ee {

  public static final String LICENSE_OPTION_TYPE = "2.14521.4.4.10";
  public static final String LICENSE_OPTION_PRODUCT = "2.14521.4.4.20";
  public static final String LICENSE_OPTION_CREATOR = "2.14521.4.4.30";
  private final List<String> eeExecutorsNames =
      List.of(CROWDSTRIKE_EXECUTOR_NAME, TANIUM_EXECUTOR_NAME);

  @Resource private OpenBASConfig openBASConfig;

  private SettingRepository settingRepository;

  @Autowired
  public void setSettingRepository(SettingRepository settingRepository) {
    this.settingRepository = settingRepository;
  }

  public String getInSubject(X509Certificate caCert, String variable) throws Exception {
    String dn = caCert.getSubjectX500Principal().getName();
    LdapName ldapDn = new LdapName(dn);
    for (Rdn rdn : ldapDn.getRdns()) {
      Attributes attributes = rdn.toAttributes();
      Attribute ouAttribute = attributes.get(variable);
      if (ouAttribute != null) {
        return ouAttribute.get().toString();
      }
    }
    throw new UnsupportedOperationException("No attribute found for " + variable);
  }

  public static boolean verifyCertificate(
      X509Certificate certToVerify, X509Certificate trustedCaCert) {
    try {
      TrustAnchor trustAnchor = new TrustAnchor(trustedCaCert, null);
      Set<TrustAnchor> trustAnchors = Collections.singleton(trustAnchor);
      PKIXParameters pkixParams = new PKIXParameters(trustAnchors);
      pkixParams.setRevocationEnabled(false);
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      List<Certificate> certList = Collections.singletonList(certToVerify);
      CertPath certPath = cf.generateCertPath(certList);
      CertPathValidator validator = CertPathValidator.getInstance("PKIX");
      validator.validate(certPath, pkixParams);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private Map<String, Setting> mapOfSettings(@NotBlank List<Setting> settings) {
    return settings.stream().collect(Collectors.toMap(Setting::getKey, Function.identity()));
  }

  private License getEnterpriseEditionInfoFromPem(String certificatePem) throws Exception {
    Map<String, Setting> dbSettings = mapOfSettings(fromIterable(this.settingRepository.findAll()));
    String instanceId =
        ofNullable(dbSettings.get(PLATFORM_INSTANCE.key()))
            .map(Setting::getValue)
            .orElse(PLATFORM_INSTANCE.defaultValue());
    String pemFromConfig = openBASConfig.getApplicationLicense();
    boolean isLicenseByConfig = pemFromConfig != null && !pemFromConfig.trim().isEmpty();
    X509Certificate x509License;
    try {
      x509License = parseCert(certificatePem);
    } catch (Exception e) {
      return new License();
    }
    X509Certificate caCert = getCaCert();
    boolean verifyCertificate = verifyCertificate(x509License, caCert);
    License license = new License();
    license.setLicenseByConfiguration(isLicenseByConfig);
    license.setLicenseEnterprise(true);
    license.setValidCert(true);
    String licenseType = getExtension(x509License, LICENSE_OPTION_TYPE);
    license.setType(LicenseTypeEnum.valueOf(licenseType));
    boolean isValidProduct = "openbas".equals(getExtension(x509License, LICENSE_OPTION_PRODUCT));
    license.setValidProduct(isValidProduct);
    license.setCreator(getExtension(x509License, LICENSE_OPTION_CREATOR));
    license.setCustomer(getInSubject(x509License, "O"));
    String licensePlatform = getInSubject(x509License, "OU");
    license.setPlatform(licensePlatform);
    boolean isPlatformMatch =
        isValidProduct && (licensePlatform.equals("global") || licensePlatform.equals(instanceId));
    license.setPlatformMatch(isPlatformMatch);
    license.setGlobalLicense(licensePlatform.equals("global"));
    Instant start = x509License.getNotBefore().toInstant();
    license.setStartDate(start);
    Instant end = x509License.getNotAfter().toInstant();
    license.setExpirationDate(end);
    boolean isLicenseExpired = Instant.now().isAfter(end) || Instant.now().isBefore(start);
    license.setLicenseExpired(isLicenseExpired);
    boolean isLicenseValidated = verifyCertificate && isPlatformMatch;
    license.setLicenseValidated(isLicenseValidated);
    // Handle grace period
    if (isLicenseValidated && isLicenseExpired) {
      // If trial license, deactivation for expiration is direct
      if (!licenseType.equals("trial")) {
        Instant extraExpirationEndDate =
            x509License.getNotBefore().toInstant().plus(90, ChronoUnit.DAYS);
        Instant now = Instant.now();
        boolean isLicenseExtended = now.isBefore(extraExpirationEndDate);
        license.setExtraExpiration(true);
        license.setExtraExpirationDays(ChronoUnit.DAYS.between(now, extraExpirationEndDate));
        license.setLicenseValidated(isLicenseExtended);
      }
    }
    return license;
  }

  public boolean isLicenseActive(License license) {
    return license.isLicenseValidated()
        && (Instant.now().isBefore(license.getExpirationDate()) || license.isExtraExpiration());
  }

  public String getEnterpriseEditionLicensePem() {
    Setting pemSetting =
        this.settingRepository.findByKey(PLATFORM_ENTERPRISE_LICENSE.key()).orElse(new Setting());
    String pem = pemSetting.getValue();
    String pemFromConfig = openBASConfig.getApplicationLicense();
    if (pemFromConfig != null && !pemFromConfig.trim().isEmpty()) {
      return pemFromConfig.trim();
    }
    if (pem != null && !pem.trim().isEmpty()) {
      return pem.trim();
    }
    return "";
  }

  /**
   * This should not be called directly. Prefer to use the license Cache Manager:
   *
   * @see(LicenseCacheManager#getEnterpriseEditionInfo)
   */
  public License getEnterpriseEditionInfo() {
    String certificatePem = getEnterpriseEditionLicensePem();
    try {
      if (certificatePem.isEmpty()) {
        throw new IllegalArgumentException("Certificate Pem is null or empty");
      }
      return getEnterpriseEditionInfoFromPem(certificatePem);
    } catch (Exception e) {
      License license = new License();
      license.setLicenseByConfiguration(false);
      return license;
    }
  }

  public License verifyCertificate(String pemToVerify) throws Exception {
    return getEnterpriseEditionInfoFromPem(pemToVerify);
  }

  public void throwEEExecutorService(
      License license, String serviceName, InjectStatus injectStatus) {
    if (!this.isLicenseActive(license) && eeExecutorsNames.contains(serviceName)) {
      String licenseRestrictedMsg =
          "LICENSE RESTRICTION - Asset will be executed through the " + serviceName + " executor";
      injectStatus.addInfoTrace(licenseRestrictedMsg, ExecutionTraceAction.EXECUTION);
      throw new LicenseRestrictionException(licenseRestrictedMsg);
    }
  }

  public List<String> detectEEExecutors(List<Agent> agents) {
    List<String> found = new ArrayList<>();

    for (Agent agent : agents) {
      Executor executor = agent.getExecutor();
      if (executor == null) continue;

      if (eeExecutorsNames.contains(executor.getName())) {
        found.add(executor.getName());
        // exit early if all types have been found
        if (found.size() == eeExecutorsNames.size()) break;
      }
    }

    return found;
  }
}
