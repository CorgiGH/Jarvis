package jarvis.tutor

import kotlinx.serialization.Serializable

enum class EffectorType { APPLY_EDIT, RUN_R, NAVIGATE, INSERT_SCRATCHPAD }

enum class Outcome { SUCCESS, REJECTED, ROLLED_BACK, STALE_DOC, PATH_DENIED }

@Serializable
data class Position(val line: Int, val character: Int)

@Serializable
data class Range(val start: Position, val end: Position)

@Serializable
data class TextEdit(val range: Range, val newText: String)

@Serializable
data class ApplyEditRequest(
    val taskId: String,
    val effectorId: String,
    val targetUri: String,
    val expectedDocVersion: String,
    val edits: List<TextEdit>,
    val nonce: String,
    val grantId: String,
)
