package jarvis.tutor.google

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
)

/**
 * Google Drive REST client (minimal subset: files.list).
 *
 * Scope required: [https://www.googleapis.com/auth/drive.readonly]
 *
 * All HTTP is delegated to [GoogleApiClient].
 */
class DriveClient(private val api: GoogleApiClient) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * List Drive files matching [query].
     *
     * @param query     Drive query string, e.g. `"name contains 'syllabus'"`.
     * @param pageSize  Maximum number of results (default 10, max 20 per spec §K).
     * @return          List of [DriveFile] or failure with upstream error message.
     */
    fun filesList(query: String, pageSize: Int = 10): Result<List<DriveFile>> {
        val encodedQ = java.net.URLEncoder.encode(query, "UTF-8")
        val path = "/drive/v3/files?q=$encodedQ&pageSize=${pageSize.coerceIn(1, 20)}" +
            "&fields=files(id,name,mimeType)"
        return api.httpGetRaw(path).map { body ->
            val root = json.parseToJsonElement(body).jsonObject
            val filesArray = root["files"]?.jsonArray ?: return@map emptyList()
            filesArray.map { elem ->
                val obj = elem.jsonObject
                DriveFile(
                    id = obj["id"]?.jsonPrimitive?.content.orEmpty(),
                    name = obj["name"]?.jsonPrimitive?.content.orEmpty(),
                    mimeType = obj["mimeType"]?.jsonPrimitive?.content.orEmpty(),
                )
            }
        }
    }
}
