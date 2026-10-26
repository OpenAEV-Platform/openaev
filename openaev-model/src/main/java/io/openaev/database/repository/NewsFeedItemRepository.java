package io.openaev.database.repository;

import io.openaev.database.model.NewsFeedItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NewsFeedItemRepository extends JpaRepository<NewsFeedItem, String> {}
