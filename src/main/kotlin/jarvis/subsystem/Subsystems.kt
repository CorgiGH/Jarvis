package jarvis.subsystem

object Subsystems {
    val all: List<Subsystem> by lazy {
        listOf(
            JudgmentSubsystem(),
            DotConnectorSubsystem(),
            TeachingSubsystem(),
            TimingSubsystem(),
            ContextModelSubsystem(),
            CoderSubsystem(),
        )
    }

    fun get(name: String): Subsystem? =
        all.find { it.name.equals(name, ignoreCase = true) }
}
