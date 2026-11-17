package io.openaev.database.repository;

import io.openaev.database.model.FindingLocation;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FindingLocationRepository extends JpaRepository<FindingLocation, String> {

  List<FindingLocation> findByLocationKeyIn(Collection<String> keys);
}
