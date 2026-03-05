package io.openaev.service;

import io.openaev.api.users.dto.UserInput;
import io.openaev.config.SessionManager;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.database.specification.GroupSpecification;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.user.form.user.CreateUserInput;
import io.openaev.rest.user.form.user.UpdateUserInput;
import io.openaev.utils.ReferenceResolver;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

import static io.openaev.helper.DatabaseHelper.updateRelation;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

/**
 * Service for user CRUD operations.
 *
 * <p>For authentication, session management, password encoding, and token handling,
 * see {@link UserAuthService}.
 *
 * @see io.openaev.database.model.User
 */
@Service
@RequiredArgsConstructor
public class UserService {

    @Resource
    private SessionManager sessionManager;
    private final ReferenceResolver referenceResolver;
    private final UserAuthService userAuthService;

    private final UserRepository userRepository;
    private final TagRepository tagRepository;
    private final GroupRepository groupRepository;
    private final OrganizationRepository organizationRepository;

    // -- COUNT --

    public long globalCount() {
        return userRepository.globalCount();
    }

    // -- CREATE --

    public User createUser(UserInput input) {
        if (userRepository.findByEmailIgnoreCase(input.email()).isPresent()) {
            throw new DataIntegrityViolationException(
                    "User with email " + input.email() + " already exists");
        }
        User user = new User();
        user.setUpdateAttributes(input);
        user.setOrganization(referenceResolver.resolve(input.organizationId(), Organization.class));
        user.setTags(referenceResolver.resolve(input.tagIds(), Tag.class, tagRepository::countByIdIn));
        return createUser(user, input.plainPassword(), UUID.randomUUID().toString());
    }

    public User createUser(CreateUserInput input, int status) {
        if (userRepository.findByEmailIgnoreCase(input.getEmail()).isPresent()) {
            throw new DataIntegrityViolationException(
                    "User with email " + input.getEmail() + " already exists");
        }
        User user = new User();
        user.setUpdateAttributes(input);
        user.setStatus((short) status);
        user.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
        user.setOrganization(
                updateRelation(input.getOrganizationId(), user.getOrganization(), organizationRepository));
        return createUser(user, input.getPassword(), input.getToken());
    }

    public User createUser(User user, String password, String token) {
        if (StringUtils.hasLength(password)) {
            user.setPassword(userAuthService.encodeUserPassword(password));
        }
        List<Group> assignableGroups =
                groupRepository.findAll(GroupSpecification.defaultUserAssignable());
        user.setGroups(assignableGroups);
        User savedUser = userRepository.save(user);
        userAuthService.createUserToken(savedUser, token);
        return savedUser;
    }

    // -- READ --

    @Transactional(readOnly = true)
    public User user(@NotBlank final String userId) {
        return this.userRepository
                .findById(userId)
                .orElseThrow(() -> new ElementNotFoundException("User not found with id: " + userId));
    }

    public List<User> users() {
        return this.userRepository.findAll();
    }

    // -- SEARCH --

    @Transactional(readOnly = true)
    public Page<User> search(SearchPaginationInput searchPaginationInput) {
        return buildPaginationJPA(
                (Specification<User> specification, Pageable pageable) -> userRepository.findAll(specification, pageable), searchPaginationInput, User.class);
    }

    // -- UPDATE --

    public User updateUser(User user) {
        return userRepository.save(user);
    }

    public User updateUser(String userId, UpdateUserInput input) {
        User user = user(userId);
        return this.updateUser(user, input);
    }

    public User updateUser(User user, UpdateUserInput input) {
        user.setUpdateAttributes(input);
        user.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
        user.setOrganization(
                updateRelation(input.getOrganizationId(), user.getOrganization(), organizationRepository));
        User savedUser = userRepository.save(user);
        sessionManager.refreshUserSessions(savedUser);
        return savedUser;
    }

    public User update(String userId, UserInput input) {
        User existing = user(userId);
        existing.setUpdateAttributes(input);
        if (StringUtils.hasLength(input.plainPassword())) {
            existing.setPassword(userAuthService.encodeUserPassword(input.plainPassword()));
        }
        existing.setOrganization(referenceResolver.resolve(input.organizationId(), Organization.class));
        existing.setTags(referenceResolver.resolve(input.tagIds(), Tag.class, tagRepository::countByIdIn));
        User savedUser = userRepository.save(existing);
        sessionManager.refreshUserSessions(savedUser);
        return savedUser;
    }

    // -- DELETE --

    public void delete(String userId) {
        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("User not found: " + userId);
        }
        sessionManager.invalidateUserSession(userId);
        userRepository.deleteByIdNative(userId);
    }

    // -- TENANT --

    @Transactional
    public User createUserInTenant(UserInput input, String tenantId) {
        User user = createUser(input);
        userRepository.addUserToTenant(user.getId(), tenantId);
        return user;
    }

    @Transactional
    public void removeUserFromTenant(String userId, String tenantId) {
        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("User not found: " + userId);
        }
        if (!userRepository.isUserInTenant(userId, tenantId)) {
            throw new EntityNotFoundException("User is not in tenant: " + tenantId);
        }
        userRepository.removeUserFromTenant(userId, tenantId);
    }
}
