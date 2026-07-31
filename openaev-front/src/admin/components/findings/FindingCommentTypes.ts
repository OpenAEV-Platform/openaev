// TODO: replace with the generated `FindingCommentOutput` type from `api-types.d.ts` once the
// backend FindingComment feature is merged and `yarn generate-types-from-api` has been re-run.
export interface FindingCommentOutput {
  finding_comment_id: string;
  finding_comment_finding_id: string;
  finding_comment_author_id: string;
  finding_comment_author_firstname?: string;
  finding_comment_author_lastname?: string;
  finding_comment_content: string;
  finding_comment_created_at: string;
  finding_comment_updated_at?: string;
}
