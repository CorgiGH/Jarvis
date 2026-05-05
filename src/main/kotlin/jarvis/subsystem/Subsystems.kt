package jarvis.subsystem

object Subsystems {
    val all: List<Subsystem> by lazy {
        listOf(
            JudgmentSubsystem(),
            DotConnectorSubsystem(),
            TeachingSubsystem(),
        )
    }

    fun get(name: String): Subsystem? =
        all.find { it.name.equals(name, ignoreCase = true) }
}
