package jarvis.tool

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

object ToolRegistry {
    val all: Map<String, Tool> by lazy {
        listOf(
            ReadTool(),
            WriteTool(),
            EditTool(),
            GrepTool(),
            GlobTool(),
            BashTool(),
        ).associateBy { it.name }
    }

    val descriptions: String by lazy {
        all.values.joinToString("\n") { "- ${it.name}: ${it.description}" }
    }

    suspend fun executeTool(name: String, argsJson: String): String {
        val tool = all[name]
            ?: return "ERROR: unknown tool '$name'. Available: ${all.keys.joinToString(", ")}"

        val args: Map<String, String> = try {
            Json.parseToJsonElement(argsJson.trim()).jsonObject.mapValues { (_, v) ->
                (v as? JsonPrimitive)?.content ?: v.toString()
            }
        } catch (e: Exception) {
            return "ERROR: invalid JSON args ($argsJson): ${e.message}"
        }

        return try {
            tool.execute(args)
        } catch (e: Exception) {
            "ERROR: ${e.javaClass.simpleName}: ${e.message}"
        }
    }
}
