package jarvis.tutor

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    @Test fun `provenance defaults null and a generated marker round-trips`() {
        // Legacy DTO (no provenance) decodes with the null default — additive, no migration.
        val legacy = """{"drill":"d","worked":"w","definition":"def","check":"c","expectedAnswerHint":"5"}"""
        val back0 = TutorTypes.tutorJson.decodeFromString(DrillContentDto.serializer(), legacy)
        assertNull(back0.provenance, "legacy JSON ⇒ provenance defaults null")

        // A generated drill carries an explicit provenance marker that survives the round-trip.
        val gen = DrillContentDto(
            drill = "d", worked = "w", definition = "def", check = "c", expectedAnswerHint = "5",
            provenance = DrillProvenanceDto(type = "generated", hasBeenFaithfulChecked = false),
        )
        val s = TutorTypes.tutorJson.encodeToString(DrillContentDto.serializer(), gen)
        val back = TutorTypes.tutorJson.decodeFromString(DrillContentDto.serializer(), s)
        assertEquals("generated", back.provenance!!.type)
        assertEquals(false, back.provenance!!.hasBeenFaithfulChecked)
    }
}
