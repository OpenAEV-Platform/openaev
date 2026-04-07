package io.openaev.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.helper.ObjectMapperHelper;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import jakarta.annotation.Resource;
import org.springdoc.core.converters.models.SortObject;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.client.RestTemplate;

@Component
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
public class AppConfig {

  static {
    /*
     * Spring Data's Sort type is a Streamable and does not map directly to an OpenAPI array in OpenAPI 3.1 while it did so in OpenaAPI 3.0
     * To preserve the existing API contract, we override the schema generation
     *
     * References:
     * - StackOverflow discussion on custom type handling in Springdoc:
     *   https://stackoverflow.com/questions/74091899/how-to-define-custom-handling-for-a-response-class-in-spring-doc
     * - Stack overflow example with a Pageable objext, similar to Sort:
     *   https://stackoverflow.com/questions/60058976/open-api-3-how-to-read-spring-boot-pagination-properties
     */
    SpringDocUtils.getConfig().replaceWithClass(Sort.class, SortObject.class);
  }

  // Validations
  public static final String EMPTY_MESSAGE = "This list cannot be empty.";
  public static final String MANDATORY_MESSAGE = "This value should not be blank.";
  public static final String NOW_FUTURE_MESSAGE = "This date must be now or in the future.";
  public static final String EMAIL_FORMAT = "This field must be a valid email.";
  public static final String PHONE_FORMAT =
      "This field must start with '+' character and country identifier.";
  public static final String PHONE_REGEXP = "^$|^\\+[\\d\\s\\-.()]+$";
  public static final String MAX_255_MESSAGE = "This field must be 255 characters or less.";

  @Resource private OpenAEVConfig openAEVConfig;

  @Bean
  ObjectMapper openAEVJsonMapper() {
    return ObjectMapperHelper.openAEVJsonMapper();
  }

  @Bean
  public OpenAPI openAEVOpenAPI() {
    final String securitySchemaName = "JSESSIONID";
    return new OpenAPI()
        .info(
            new Info()
                .title("OpenAEV API")
                .description(
                    "Software under open source licence designed to plan and conduct exercises")
                .version(this.openAEVConfig.getVersion())
                .license(new License().name("Apache 2.0").url("https://filigran.io/")))
        .addSecurityItem(new SecurityRequirement().addList(securitySchemaName))
        .components(
            new Components()
                .addSecuritySchemes(
                    securitySchemaName,
                    new SecurityScheme()
                        .name(securitySchemaName)
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)))
        .externalDocs(
            new ExternalDocumentation()
                .description("OpenAEV documentation")
                .url("https://docs.openaev.io/"));
  }

  @Bean
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }
}
