package io.openaev.database.raw;

/** A security coverage send job with its simulation and that simulation's tenant. */
public interface RawSecurityCoverageSendJobTenant {

  String getSecurity_coverage_send_job_id();

  String getExercise_id();

  String getTenant_id();
}
