package jarvis.tool

interface Tool {
    val name: String
    val description: String
    suspend fun execute(args: Map<String, String>): String
}
