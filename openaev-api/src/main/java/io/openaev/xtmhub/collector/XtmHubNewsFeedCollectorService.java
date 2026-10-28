package io.openaev.xtmhub.collector;

import io.openaev.xtmhub.XtmHubService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class XtmHubNewsFeedCollectorService implements Runnable {
  private final XtmHubService xtmHubService;

  @Override
  public void run() {
    xtmHubService.syncNewsFeed();
  }
}
