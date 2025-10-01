package io.openaev.helper;

import static java.util.Arrays.asList;

import io.openaev.database.model.Grant;
import io.openaev.database.model.User;
import java.util.List;

public class UserHelper {

  public static List<User> getUsersByType(List<Grant> grants, Grant.GRANT_TYPE... types) {
    return grants.stream()
        .filter(grant -> asList(types).contains(grant.getName()))
        .map(Grant::getGroup)
        .flatMap(group -> group.getUsers().stream())
        .distinct()
        .toList();
  }

  public static String getGravatar(String email) {
    String emailMd5 = CryptoHelper.md5Hex(email.trim().toLowerCase());
    return "https://www.gravatar.com/avatar/" + emailMd5 + "?d=mm";
  }
}
