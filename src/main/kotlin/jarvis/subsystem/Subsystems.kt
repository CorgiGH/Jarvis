package jarvis.subsystem

object Subsystems {
    val all: List<Subsystem> by lazy {
        listOf(
            JudgmentSubsystem(),
            DotConnectorSubsystem(),
            TeachingSubsystem(),
            CoderSubsystem(),
        )
    }

    fun get(name: String): Subsystem? =
        all.find { it.name.equals(name, ignoreCase = true) }
}
