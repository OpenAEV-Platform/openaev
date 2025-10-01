package io.openaev.rest.rbac;

import io.openaev.aop.RBAC;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

@Component
public class RbacEndpointScanner {
  @Autowired private ApplicationContext applicationContext;

  public List<EndpointInfo> findRbacEndpoints() {
    List<EndpointInfo> endpoints = new ArrayList<>();

    Map<String, Object> controllers =
        applicationContext.getBeansWithAnnotation(RestController.class);

    for (Object controllerBean : controllers.values()) {
      Class<?> targetClass = AopUtils.getTargetClass(controllerBean);

      RequestMapping classRequestMapping =
          AnnotationUtils.findAnnotation(targetClass, RequestMapping.class);
      String[] classPaths =
          (classRequestMapping != null && classRequestMapping.path().length > 0)
              ? classRequestMapping.path()
              : new String[] {""};

      for (Method method : targetClass.getDeclaredMethods()) {
        RBAC rbacAnnotation = AnnotationUtils.findAnnotation(method, RBAC.class);
        if (rbacAnnotation == null) continue;

        List<RequestMethod> httpMethods = new ArrayList<>();
        List<String> methodPaths = new ArrayList<>();
        List<String> consumes = new ArrayList<>();

        if (method.isAnnotationPresent(GetMapping.class)) {
          GetMapping mapping = method.getAnnotation(GetMapping.class);
          httpMethods.add(RequestMethod.GET);
          methodPaths.addAll(Arrays.asList(resolvePaths(mapping.path(), mapping.value())));
          consumes.addAll(Arrays.asList(mapping.consumes()));
        } else if (method.isAnnotationPresent(PostMapping.class)) {
          PostMapping mapping = method.getAnnotation(PostMapping.class);
          httpMethods.add(RequestMethod.POST);
          methodPaths.addAll(Arrays.asList(resolvePaths(mapping.path(), mapping.value())));
          consumes.addAll(Arrays.asList(mapping.consumes()));
        } else if (method.isAnnotationPresent(PutMapping.class)) {
          PutMapping mapping = method.getAnnotation(PutMapping.class);
          httpMethods.add(RequestMethod.PUT);
          methodPaths.addAll(Arrays.asList(resolvePaths(mapping.path(), mapping.value())));
          consumes.addAll(Arrays.asList(mapping.consumes()));
        } else if (method.isAnnotationPresent(DeleteMapping.class)) {
          DeleteMapping mapping = method.getAnnotation(DeleteMapping.class);
          httpMethods.add(RequestMethod.DELETE);
          methodPaths.addAll(Arrays.asList(resolvePaths(mapping.path(), mapping.value())));
          consumes.addAll(Arrays.asList(mapping.consumes()));
        } else if (method.isAnnotationPresent(RequestMapping.class)) {
          RequestMapping mapping = method.getAnnotation(RequestMapping.class);
          RequestMethod[] methods = mapping.method();
          if (methods.length == 0) {
            httpMethods.add(RequestMethod.GET); // default if not specified
          } else {
            httpMethods.addAll(Arrays.asList(methods));
          }
          methodPaths.addAll(Arrays.asList(resolvePaths(mapping.path(), mapping.value())));
          consumes.addAll(Arrays.asList(mapping.consumes()));
        }

        if (methodPaths.isEmpty()) {
          methodPaths.add(""); // Default to empty if no path
        }

        // fallback to application/json if not specified
        if (consumes.isEmpty()) {
          consumes.add(MediaType.APPLICATION_JSON_VALUE);
        }

        for (String classPath : classPaths) {
          for (String methodPath : methodPaths) {
            String fullPath = normalizePath(classPath) + normalizePath(methodPath);
            for (RequestMethod httpMethod : httpMethods) {
              endpoints.add(new EndpointInfo(httpMethod, fullPath, rbacAnnotation, consumes));
            }
          }
        }
      }
    }

    return endpoints;
  }

  private String[] resolvePaths(String[] path, String[] value) {
    if (path.length > 0) return path;
    if (value.length > 0) return value;
    return new String[] {""};
  }

  private String normalizePath(String path) {
    if (!path.startsWith("/")) path = "/" + path;
    if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
    return path.equals("/") ? "" : path;
  }
}
