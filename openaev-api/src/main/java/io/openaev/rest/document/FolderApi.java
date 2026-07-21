package io.openaev.rest.document;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.helper.StreamHelper.fromIterable;

import io.openaev.aop.AccessControl;
import io.openaev.database.model.Action;
import io.openaev.database.model.Folder;
import io.openaev.database.model.ResourceType;
import io.openaev.database.repository.FolderRepository;
import io.openaev.rest.document.form.FolderInput;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** CRUD + move for the tenant-scoped folder tree that organizes files. */
@RestController
@RequiredArgsConstructor
public class FolderApi extends RestBehavior {

  public static final String FOLDER_API = "/api/folders";
  private static final String TENANT_FOLDER_API = TENANT_PREFIX + "/folders";

  private final FolderRepository folderRepository;

  @GetMapping({FOLDER_API, TENANT_FOLDER_API})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.DOCUMENT)
  public List<Folder> folders() {
    return fromIterable(folderRepository.findAll());
  }

  @PostMapping({FOLDER_API, TENANT_FOLDER_API})
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.DOCUMENT)
  public Folder createFolder(@Valid @RequestBody FolderInput input) {
    Folder folder = new Folder();
    folder.setName(input.getName());
    folder.setParent(resolveParent(input.getParentId(), null));
    return folderRepository.save(folder);
  }

  @PutMapping({FOLDER_API + "/{folderId}", TENANT_FOLDER_API + "/{folderId}"})
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.DOCUMENT)
  public Folder updateFolder(@PathVariable String folderId, @Valid @RequestBody FolderInput input) {
    Folder folder =
        folderRepository
            .findById(folderId)
            .orElseThrow(() -> new ElementNotFoundException("Folder not found"));
    folder.setName(input.getName());
    folder.setParent(resolveParent(input.getParentId(), folderId));
    folder.setUpdatedAt(Instant.now());
    return folderRepository.save(folder);
  }

  @DeleteMapping({FOLDER_API + "/{folderId}", TENANT_FOLDER_API + "/{folderId}"})
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(actionPerformed = Action.DELETE, resourceType = ResourceType.DOCUMENT)
  public void deleteFolder(@PathVariable String folderId) {
    Folder folder =
        folderRepository
            .findById(folderId)
            .orElseThrow(() -> new ElementNotFoundException("Folder not found"));
    // ON DELETE CASCADE removes sub-folders; files are detached (FK SET NULL).
    folderRepository.delete(folder);
  }

  // Resolve a parent folder id, rejecting a folder becoming its own parent (direct cycle).
  private Folder resolveParent(String parentId, String selfId) {
    if (parentId == null || parentId.isBlank()) {
      return null;
    }
    if (parentId.equals(selfId)) {
      throw new BadRequestException("A folder cannot be its own parent");
    }
    return folderRepository
        .findById(parentId)
        .orElseThrow(() -> new ElementNotFoundException("Parent folder not found"));
  }
}
