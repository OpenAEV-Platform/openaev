package io.openaev.xtmhub.collector;

import io.openaev.service.NewsFeedItemService;
import io.openaev.xtmhub.XtmHubService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class XtmHubNewsFeedCollectorService implements Runnable {
  private final XtmHubService xtmHubService;
  private final NewsFeedItemService newsFeedItemService;

  @Override
  public void run() {
    try {
      xtmHubService
          .consumeProvisionedNewsFeedItems()
          .ifPresent(provisioned -> newsFeedItemService.createMissingItems(provisioned.items()));
    } catch (Exception e) {
      // The Hub removes the items it returns: those of a failed run are not delivered again.
      log.error("The XTM Hub news feed items could not be created: {}", e.getMessage(), e);
    }
  }
}
