# REST API
OpenBAS provides a REST API, allowing users to perform various actions programmatically. The API enables users to interact with OpenBAS's functionality and data, offering a powerful tool for automation, integration, and customization. Any action available through the platform's graphical interface can also be executed via the API.


## Authentication

Accessing the OpenBAS API requires authentication through standard mechanisms. To authenticate, users must include the following headers in their API requests:


```html
Content-Type: application/json
Authorization: Bearer [API key]
```

Using the API key will provide you admin access.

## SwaggerUI

The OpenBAS API is documented using SwaggerUI, which provides an interactive interface for exploring the API's endpoints, parameters, and responses. The SwaggerUI is accessible at the following URL: [openbas url]:8080/swagger-ui/index.html
