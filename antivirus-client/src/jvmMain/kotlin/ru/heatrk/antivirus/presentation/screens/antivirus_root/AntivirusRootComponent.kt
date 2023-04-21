package ru.heatrk.antivirus.presentation.screens.antivirus_root

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.childContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.kodein.di.DI
import ru.heatrk.antivirus.data.models.ApiMessage
import ru.heatrk.antivirus.domain.repositories.MessagingRepository
import ru.heatrk.antivirus.presentation.common.Component
import ru.heatrk.antivirus.presentation.dialogs.MessageDialogState
import ru.heatrk.antivirus.presentation.routing.Router
import ru.heatrk.antivirus.presentation.screens.ProtectionListener
import ru.heatrk.antivirus.presentation.screens.ProtectionStatus
import ru.heatrk.antivirus.presentation.screens.scanner.ScannerComponent
import ru.heatrk.antivirus.presentation.screens.service_control.ProtectionControlComponent
import ru.heatrk.antivirus.presentation.values.strings.strings
import java.util.UUID

class AntivirusRootComponent(
    componentContext: ComponentContext,
    private val router: Router,
    private val messagingRepository: MessagingRepository,
    private val defaultDispatcher: CoroutineDispatcher,
    di: DI
): Component(componentContext) {

    val protectionControlComponent = ProtectionControlComponent(
        componentContext = childContext(key = UUID.randomUUID().toString())
    )

    val scannerComponent = ScannerComponent(
        componentContext = childContext(key = UUID.randomUUID().toString()),
        messagingRepository = messagingRepository,
        router = router
    )

    private val statusListeners = listOf<ProtectionListener>(
        protectionControlComponent
    )

    private val _state = MutableStateFlow<AntivirusRootViewState>(AntivirusRootViewState.Loading)
    val state = _state.asStateFlow()

    private val listenersJobs = mutableListOf<Job>()

    init {
        load()
    }

    fun onIntent(intent: AntivirusRootIntent) = componentScope.launch {
        when (intent) {
            AntivirusRootIntent.MessageDialogDismiss -> {
                val state = _state.value

                if (state !is AntivirusRootViewState.Ok) {
                    return@launch
                }

                _state.value = state.copy(messageDialogState = MessageDialogState.Gone)
            }

            AntivirusRootIntent.Reload -> {
                load()
            }
        }
    }

    private fun load() = componentScope.launch {
        _state.value = AntivirusRootViewState.Loading

        when (val result = messagingRepository.isProtectionEnabled()) {
            is ApiMessage.Ok -> {
                _state.value = AntivirusRootViewState.Ok()

                statusListeners.forEach {
                    it.onStatusReceived(
                        if (result.body) {
                            ProtectionStatus.ENABLED
                        } else {
                            ProtectionStatus.DISABLED
                        }
                    )
                }

                initListeners()
            }

            is ApiMessage.Fail -> {
                _state.value = AntivirusRootViewState.Error(
                    strings.errorMessage(
                        description = result.description,
                        errorCode = result.errorCode
                    )
                )
            }
        }
    }

    private fun initListeners() {
        listenersJobs.forEach(Job::cancel)
        listenersJobs.clear()

        protectionControlComponent.serviceStartEvents
            .onEach {
                statusListeners.forEach { it.onStatusReceived(ProtectionStatus.LOADING) }

                when (val result = messagingRepository.enableProtection()) {
                    is ApiMessage.Ok -> {
                        statusListeners.forEach { it.onStatusReceived(ProtectionStatus.ENABLED) }
                    }

                    is ApiMessage.Fail -> {
                        statusListeners.forEach { it.onStatusReceived(ProtectionStatus.DISABLED) }

                        _state.value = AntivirusRootViewState.Ok(
                            messageDialogState = MessageDialogState.Error(
                                title = strings.error,
                                message = strings.errorMessage(
                                    description = result.description,
                                    errorCode = result.errorCode
                                )
                            )
                        )
                    }
                }
            }
            .launchIn(componentScope)
            .also { listenersJobs.add(it) }

        protectionControlComponent.serviceStopEvents
            .onEach {
                statusListeners.forEach { it.onStatusReceived(ProtectionStatus.LOADING)}

                when (val result = messagingRepository.disableProtection()) {
                    is ApiMessage.Ok -> {
                        statusListeners.forEach { it.onStatusReceived(ProtectionStatus.DISABLED) }
                    }

                    is ApiMessage.Fail -> {
                        statusListeners.forEach { it.onStatusReceived(ProtectionStatus.ENABLED) }

                        _state.value = AntivirusRootViewState.Ok(
                            messageDialogState = MessageDialogState.Error(
                                title = strings.error,
                                message = strings.errorMessage(
                                    description = result.description,
                                    errorCode = result.errorCode
                                )
                            )
                        )
                    }
                }
            }
            .launchIn(componentScope)
            .also { listenersJobs.add(it) }
    }

    data class Args(
        val componentContext: ComponentContext,
        val router: Router
    )

    companion object {
        fun create(
            args: Args,
            messagingRepository: MessagingRepository,
            defaultDispatcher: CoroutineDispatcher,
            di: DI
        ) = AntivirusRootComponent(
            componentContext = args.componentContext,
            di = di,
            messagingRepository = messagingRepository,
            defaultDispatcher = defaultDispatcher,
            router = args.router
        )
    }
}