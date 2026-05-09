export type EffectorType = "APPLY_EDIT" | "RUN_R" | "NAVIGATE" | "INSERT_SCRATCHPAD";
export type Outcome = "SUCCESS" | "REJECTED" | "ROLLED_BACK" | "STALE_DOC" | "PATH_DENIED";

export interface ContentRef { repo: string; path: string; sha: string; }
export interface ScratchpadRef { docId: string; version: number; }
export interface SubmissionRef { docId: string; version: number; submittedAt: string; }
export interface GradeRecord { score: number; rubricVersion: string; gradedAt: string; modelId: string; }

export type TaskStatus = "TODO" | "ACTIVE" | "SUBMITTED" | "GRADED" | "ARCHIVED";

export interface Task {
  id: string;
  userId: string;
  subject: string;
  title: string;
  deadline: string;
  problemRef: ContentRef;
  conceptRefs: ContentRef[];
  rubricRef: ContentRef;
  scratchpad: ScratchpadRef | null;
  submission: SubmissionRef | null;
  grade: GradeRecord | null;
  cardRefs: string[];
  status: TaskStatus;
  createdAt: string;
  updatedAt: string;
}

export interface Position { line: number; character: number; }
export interface Range { start: Position; end: Position; }
export interface TextEdit { range: Range; newText: string; }
export interface ApplyEditRequest {
  taskId: string;
  effectorId: string;
  targetUri: string;
  expectedDocVersion: string;
  edits: TextEdit[];
  nonce: string;
  grantId: string;
}
