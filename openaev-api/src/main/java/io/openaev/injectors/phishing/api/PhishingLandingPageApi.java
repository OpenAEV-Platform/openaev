package io.openaev.injectors.phishing.api;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.database.model.Action;
import io.openaev.database.model.PhishingLandingPage;
import io.openaev.database.model.ResourceType;
import io.openaev.injectors.phishing.form.PhishingLandingPageBulkProcessingInput;
import io.openaev.injectors.phishing.form.PhishingLandingPageInput;
import io.openaev.injectors.phishing.form.PhishingLandingPageLogoInput;
import io.openaev.injectors.phishing.service.PhishingLandingPageService;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Admin CRUD for reusable phishing landing pages (Components > Phishing pages). */
@RestController
@RequiredArgsConstructor
public class PhishingLandingPageApi extends RestBehavior {

  public static final String PHISHING_LANDING_PAGE_URI = "/api/phishing/landing-pages";
  private static final String TENANT_PHISHING_LANDING_PAGE_URI =
      TENANT_PREFIX + "/phishing/landing-pages";

  private final PhishingLandingPageService landingPageService;

  @GetMapping({PHISHING_LANDING_PAGE_URI, TENANT_PHISHING_LANDING_PAGE_URI})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.PHISHING_LANDING_PAGE)
  public Iterable<PhishingLandingPage> landingPages() {
    return landingPageService.landingPages();
  }

  @PostMapping({
    PHISHING_LANDING_PAGE_URI + "/search",
    TENANT_PHISHING_LANDING_PAGE_URI + "/search"
  })
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.PHISHING_LANDING_PAGE)
  public Page<PhishingLandingPage> searchLandingPages(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return landingPageService.search(searchPaginationInput);
  }

  @GetMapping({PHISHING_LANDING_PAGE_URI + "/{id}", TENANT_PHISHING_LANDING_PAGE_URI + "/{id}"})
  @Transactional
  @AccessControl(
      resourceId = "#id",
      actionPerformed = Action.READ,
      resourceType = ResourceType.PHISHING_LANDING_PAGE)
  public PhishingLandingPage landingPage(@PathVariable String id) {
    return landingPageService.landingPage(id);
  }

  @PostMapping({PHISHING_LANDING_PAGE_URI, TENANT_PHISHING_LANDING_PAGE_URI})
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.PHISHING_LANDING_PAGE)
  public PhishingLandingPage createLandingPage(@Valid @RequestBody PhishingLandingPageInput input) {
    PhishingLandingPage landingPage = new PhishingLandingPage();
    applyInput(landingPage, input);
    return landingPageService.upsert(landingPage);
  }

  @PutMapping({PHISHING_LANDING_PAGE_URI + "/{id}", TENANT_PHISHING_LANDING_PAGE_URI + "/{id}"})
  @Transactional
  @AccessControl(
      resourceId = "#id",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.PHISHING_LANDING_PAGE)
  public PhishingLandingPage updateLandingPage(
      @PathVariable String id, @Valid @RequestBody PhishingLandingPageInput input) {
    PhishingLandingPage landingPage = landingPageService.landingPage(id);
    applyInput(landingPage, input);
    return landingPageService.upsert(landingPage);
  }

  @PutMapping({
    PHISHING_LANDING_PAGE_URI + "/{id}/logos",
    TENANT_PHISHING_LANDING_PAGE_URI + "/{id}/logos"
  })
  @Transactional
  @AccessControl(
      resourceId = "#id",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.PHISHING_LANDING_PAGE)
  public PhishingLandingPage updateLandingPageLogos(
      @PathVariable String id, @Valid @RequestBody PhishingLandingPageLogoInput input) {
    return landingPageService.updateLogos(id, input.getLogoDark(), input.getLogoLight());
  }

  @PostMapping({
    PHISHING_LANDING_PAGE_URI + "/{id}/duplicate",
    TENANT_PHISHING_LANDING_PAGE_URI + "/{id}/duplicate"
  })
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(
      resourceId = "#id",
      actionPerformed = Action.DUPLICATE,
      resourceType = ResourceType.PHISHING_LANDING_PAGE)
  public PhishingLandingPage duplicateLandingPage(@PathVariable String id) {
    PhishingLandingPage source = landingPageService.landingPage(id);
    PhishingLandingPage copy = new PhishingLandingPage();
    copy.setName(source.getName() + " (copy)");
    copy.setDescription(source.getDescription());
    copy.setHtml(source.getHtml());
    copy.setCss(source.getCss());
    copy.setCaptureSubmittedData(source.isCaptureSubmittedData());
    copy.setCapturePasswords(source.isCapturePasswords());
    copy.setRedirectUrl(source.getRedirectUrl());
    copy.setPrimaryColorDark(source.getPrimaryColorDark());
    copy.setPrimaryColorLight(source.getPrimaryColorLight());
    copy.setLogoDark(source.getLogoDark());
    copy.setLogoLight(source.getLogoLight());
    copy.setCustomDomain(source.getCustomDomain());
    return landingPageService.upsert(copy);
  }

  @DeleteMapping({PHISHING_LANDING_PAGE_URI + "/{id}", TENANT_PHISHING_LANDING_PAGE_URI + "/{id}"})
  @Transactional
  @AccessControl(
      resourceId = "#id",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.PHISHING_LANDING_PAGE)
  public void deleteLandingPage(@PathVariable String id) {
    landingPageService.delete(id);
  }

  @DeleteMapping({PHISHING_LANDING_PAGE_URI, TENANT_PHISHING_LANDING_PAGE_URI})
  @Transactional
  @AccessControl(actionPerformed = Action.DELETE, resourceType = ResourceType.PHISHING_LANDING_PAGE)
  public List<String> bulkDeleteLandingPages(
      @RequestBody @Valid final PhishingLandingPageBulkProcessingInput input) {
    return landingPageService.bulkDelete(input);
  }

  private void applyInput(PhishingLandingPage landingPage, PhishingLandingPageInput input) {
    landingPage.setName(input.getName());
    landingPage.setDescription(input.getDescription());
    landingPage.setHtml(input.getHtml());
    landingPage.setCss(input.getCss());
    landingPage.setCaptureSubmittedData(input.isCaptureSubmittedData());
    landingPage.setCapturePasswords(input.isCapturePasswords());
    landingPage.setRedirectUrl(input.getRedirectUrl());
    landingPage.setPrimaryColorDark(input.getPrimaryColorDark());
    landingPage.setPrimaryColorLight(input.getPrimaryColorLight());
    landingPage.setCustomDomain(
        landingPageService.resolveVerifiedCustomDomain(input.getCustomDomainId()));
  }
}
