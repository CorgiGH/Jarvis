package jarvis.tutor

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals

class DrillContentDtoTest {
    @Test fun `drillsJson encodes a problemId-keyed map with vizId`() {
        val map = mapOf("p1" to DrillContentDto(
            drill = "d", worked = "w", definition = "def", check = "c",
            expectedAnswerHint = "5", vizId = "recursion-tree",
        ))
        val s = TutorTypes.tutorJson.encodeToString(
            MapSerializer(String.serializer(), DrillContentDto.serializer()), map)
        val back = TutorTypes.tutorJson.decodeFromString(
            MapSerializer(String.serializer(), DrillContentDto.serializer()), s)
        assertEquals("recursion-tree", back["p1"]!!.vizId)
        assertEquals("5", back["p1"]!!.expectedAnswerHint)
    }
}
