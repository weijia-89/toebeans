package app.toebeans.core.navigation

import com.arkivanov.decompose.Child
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value

/**
 * The five top-level tabs, ordered as the bottom navigation strip renders them.
 *
 * Kept in shared/commonMain so the Android shell and a future iOS shell read one
 * definition. This is the iOS-sharing goal that ADR-0023 names as decision D3.
 */
public enum class ToebeansTab {
    TODAY,
    MED_LIST,
    PETS,
    SETTINGS,
    HISTORY,
}

/**
 * Where a dose-logging tap resolves. This is the shared counterpart of the Android
 * `resolvePetDetailMedicationDestination` helper, so the decision lives in one place.
 */
public enum class DoseDestination {
    MEDICATION_EDIT,
    SCHEDULE_CREATE,
}

/** A destination held in a tab's Decompose child stack. */
public sealed interface ToebeansConfig {
    public data class Tab(
        val tab: ToebeansTab,
    ) : ToebeansConfig

    public data class DoseLogging(
        val petId: String,
        val medicationId: String,
        val scheduleId: String?,
        val destination: DoseDestination,
    ) : ToebeansConfig
}

/**
 * The child instance paired with a config. [scrollOffset] carries the restored scroll
 * position so a tab can be left and re-entered without losing the owner's place.
 */
public data class ToebeansScreen(
    val config: ToebeansConfig,
    val scrollOffset: Int = 0,
)

/** Decompose-backed navigation model consumed by every platform shell. */
public interface ToebeansRootComponent {
    public val tabs: List<ToebeansTab>
    public val selectedTab: Value<ToebeansTab>
    public val childStack: Value<ChildStack<ToebeansConfig, ToebeansScreen>>

    public fun selectTab(tab: ToebeansTab)

    public fun openDoseLogging(
        petId: String,
        medicationId: String,
        scheduleId: String?,
    )

    public fun saveScrollOffset(offset: Int)

    public fun back(): Boolean

    public fun activeDoseDestination(): DoseDestination?
}

/**
 * Default [ToebeansRootComponent].
 *
 * Each tab owns a child stack. Leaving a tab saves its stack and scroll offset; returning
 * restores both. A Decompose [StackNavigation] emits stack changes and [ChildStack] is the
 * observable result. No Android type and no platform clock is referenced, so the model
 * runs on any KMP target.
 */
public class DefaultToebeansRootComponent : ToebeansRootComponent {
    private val navigation = StackNavigation<ToebeansConfig>()
    private val stacks = LinkedHashMap<ToebeansTab, List<ToebeansConfig>>()
    private val scrollOffsets = HashMap<ToebeansTab, Int>()
    private var configs: List<ToebeansConfig> = listOf(ToebeansConfig.Tab(ToebeansTab.TODAY))

    private val selected = MutableValue(ToebeansTab.TODAY)
    override val selectedTab: Value<ToebeansTab> get() = selected

    private val stack = MutableValue(configs.toChildStack())
    override val childStack: Value<ChildStack<ToebeansConfig, ToebeansScreen>> get() = stack

    override val tabs: List<ToebeansTab> = ToebeansTab.entries.toList()

    init {
        stacks[ToebeansTab.TODAY] = configs
        navigation.subscribe { event ->
            val previous = configs
            val next = event.transformer(previous)
            configs = next
            stack.value = next.toChildStack()
            event.onComplete(next, previous)
        }
    }

    override fun selectTab(tab: ToebeansTab) {
        if (tab == selected.value) return
        stacks[selected.value] = configs
        val restored = stacks[tab] ?: listOf(ToebeansConfig.Tab(tab))
        selected.value = tab
        navigation.navigate(transformer = { restored }, onComplete = { _, _ -> })
        stacks[tab] = restored
    }

    override fun openDoseLogging(
        petId: String,
        medicationId: String,
        scheduleId: String?,
    ) {
        val destination =
            if (scheduleId == null) {
                DoseDestination.SCHEDULE_CREATE
            } else {
                DoseDestination.MEDICATION_EDIT
            }
        navigation.navigate(
            transformer = { current ->
                current + ToebeansConfig.DoseLogging(petId, medicationId, scheduleId, destination)
            },
            onComplete = { _, _ -> },
        )
    }

    override fun saveScrollOffset(offset: Int) {
        scrollOffsets[selected.value] = offset
        stack.value = configs.toChildStack()
    }

    override fun back(): Boolean {
        if (configs.size <= 1) return false
        navigation.navigate(transformer = { it.dropLast(1) }, onComplete = { _, _ -> })
        return true
    }

    override fun activeDoseDestination(): DoseDestination? =
        (stack.value.active.configuration as? ToebeansConfig.DoseLogging)?.destination

    private fun List<ToebeansConfig>.toChildStack(): ChildStack<ToebeansConfig, ToebeansScreen> {
        val offset = scrollOffsets[selected.value] ?: 0
        return ChildStack(
            active = last().toCreated(offset),
            backStack = dropLast(1).map { it.toCreated(offset) },
        )
    }

    private fun ToebeansConfig.toCreated(offset: Int): Child.Created<ToebeansConfig, ToebeansScreen> =
        Child.Created(
            configuration = this,
            instance = ToebeansScreen(config = this, scrollOffset = offset),
            key = childKey(),
        )

    private fun ToebeansConfig.childKey(): String =
        when (this) {
            is ToebeansConfig.Tab -> "tab-$tab"
            is ToebeansConfig.DoseLogging -> "dose-$petId-$medicationId"
        }
}
