package io.openaev.injectors.phishing.api;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.database.model.Action;
import io.openaev.database.model.PhishingEmailTemplate;
import io.openaev.database.model.ResourceType;
import io.openaev.injectors.phishing.form.PhishingEmailTemplateBulkProcessingInput;
import io.openaev.injectors.phishing.form.PhishingEmailTemplateInput;
import io.openaev.injectors.phishing.service.PhishingEmailTemplateService;
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

/** Admin CRUD for reusable phishing email templates (Components > Phishing emails). */
@RestController
@RequiredArgsConstructor
public class PhishingEmailTemplateApi extends RestBehavior {

  public static final String PHISHING_EMAIL_TEMPLATE_URI = "/api/phishing/email-templates";
  private static final String TENANT_PHISHING_EMAIL_TEMPLATE_URI =
      TENANT_PREFIX + "/phishing/email-templates";

  private final PhishingEmailTemplateService emailTemplateService;

  @GetMapping({PHISHING_EMAIL_TEMPLATE_URI, TENANT_PHISHING_EMAIL_TEMPLATE_URI})
  @Transactional
  @AccessControl(
      actionPerformed = Action.SEARCH,
      resourceType = ResourceType.PHISHING_EMAIL_TEMPLATE)
  public Iterable<PhishingEmailTemplate> emailTemplates() {
    return emailTemplateService.emailTemplates();
  }

  @PostMapping({
    PHISHING_EMAIL_TEMPLATE_URI + "/search",
    TENANT_PHISHING_EMAIL_TEMPLATE_URI + "/search"
  })
  @Transactional
  @AccessControl(
      actionPerformed = Action.SEARCH,
      resourceType = ResourceType.PHISHING_EMAIL_TEMPLATE)
  public Page<PhishingEmailTemplate> searchEmailTemplates(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return emailTemplateService.search(searchPaginationInput);
  }

  @GetMapping({PHISHING_EMAIL_TEMPLATE_URI + "/{id}", TENANT_PHISHING_EMAIL_TEMPLATE_URI + "/{id}"})
  @Transactional
  @AccessControl(
      resourceId = "#id",
      actionPerformed = Action.READ,
      resourceType = ResourceType.PHISHING_EMAIL_TEMPLATE)
  public PhishingEmailTemplate emailTemplate(@PathVariable String id) {
    return emailTemplateService.emailTemplate(id);
  }

  @PostMapping({PHISHING_EMAIL_TEMPLATE_URI, TENANT_PHISHING_EMAIL_TEMPLATE_URI})
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(
      actionPerformed = Action.CREATE,
      resourceType = ResourceType.PHISHING_EMAIL_TEMPLATE)
  public PhishingEmailTemplate createEmailTemplate(
      @Valid @RequestBody PhishingEmailTemplateInput input) {
    PhishingEmailTemplate emailTemplate = new PhishingEmailTemplate();
    applyInput(emailTemplate, input);
    return emailTemplateService.upsert(emailTemplate);
  }

  @PutMapping({PHISHING_EMAIL_TEMPLATE_URI + "/{id}", TENANT_PHISHING_EMAIL_TEMPLATE_URI + "/{id}"})
  @Transactional
  @AccessControl(
      resourceId = "#id",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.PHISHING_EMAIL_TEMPLATE)
  public PhishingEmailTemplate updateEmailTemplate(
      @PathVariable String id, @Valid @RequestBody PhishingEmailTemplateInput input) {
    PhishingEmailTemplate emailTemplate = emailTemplateService.emailTemplate(id);
    applyInput(emailTemplate, input);
    return emailTemplateService.upsert(emailTemplate);
  }

  @PostMapping({
    PHISHING_EMAIL_TEMPLATE_URI + "/{id}/duplicate",
    TENANT_PHISHING_EMAIL_TEMPLATE_URI + "/{id}/duplicate"
  })
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(
      resourceId = "#id",
      actionPerformed = Action.DUPLICATE,
      resourceType = ResourceType.PHISHING_EMAIL_TEMPLATE)
  public PhishingEmailTemplate duplicateEmailTemplate(@PathVariable String id) {
    PhishingEmailTemplate source = emailTemplateService.emailTemplate(id);
    PhishingEmailTemplate copy = new PhishingEmailTemplate();
    copy.setName(source.getName() + " (copy)");
    copy.setDescription(source.getDescription());
    copy.setSubject(source.getSubject());
    copy.setHtmlBody(source.getHtmlBody());
    copy.setTextBody(source.getTextBody());
    copy.setFromName(source.getFromName());
    copy.setFromEmail(source.getFromEmail());
    copy.setAddTrackingPixel(source.isAddTrackingPixel());
    return emailTemplateService.upsert(copy);
  }

  @DeleteMapping({
    PHISHING_EMAIL_TEMPLATE_URI + "/{id}",
    TENANT_PHISHING_EMAIL_TEMPLATE_URI + "/{id}"
  })
  @Transactional
  @AccessControl(
      resourceId = "#id",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.PHISHING_EMAIL_TEMPLATE)
  public void deleteEmailTemplate(@PathVariable String id) {
    emailTemplateService.delete(id);
  }

  @DeleteMapping({PHISHING_EMAIL_TEMPLATE_URI, TENANT_PHISHING_EMAIL_TEMPLATE_URI})
  @Transactional
  @AccessControl(
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.PHISHING_EMAIL_TEMPLATE)
  public List<String> bulkDeleteEmailTemplates(
      @RequestBody @Valid final PhishingEmailTemplateBulkProcessingInput input) {
    return emailTemplateService.bulkDelete(input);
  }

  private void applyInput(PhishingEmailTemplate emailTemplate, PhishingEmailTemplateInput input) {
    emailTemplate.setName(input.getName());
    emailTemplate.setDescription(input.getDescription());
    emailTemplate.setSubject(input.getSubject());
    emailTemplate.setHtmlBody(input.getHtmlBody());
    emailTemplate.setTextBody(input.getTextBody());
    emailTemplate.setFromName(input.getFromName());
    emailTemplate.setFromEmail(input.getFromEmail());
    emailTemplate.setAddTrackingPixel(input.isAddTrackingPixel());
  }
}
