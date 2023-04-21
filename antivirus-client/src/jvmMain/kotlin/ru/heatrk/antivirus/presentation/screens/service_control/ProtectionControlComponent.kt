package ru.heatrk.antivirus.presentation.screens.service_control

import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ru.heatrk.antivirus.presentation.common.Component
import ru.heatrk.antivirus.presentation.dialogs.MessageDialogState
import ru.heatrk.antivirus.presentation.screens.ProtectionStatus
import ru.heatrk.antivirus.presentation.screens.ProtectionListener
import ru.heatrk.antivirus.presentation.values.strings.strings

class ProtectionControlComponent(
    componentContext: ComponentContext
) : Component(componentContext), ProtectionListener {
    private val _state = MutableStateFlow<ProtectionControlViewState>(ProtectionControlViewState.Loading)
    val state = _state.asStateFlow()

    private val _serviceStartEvents = Channel<Unit>(Channel.BUFFERED)
    val serviceStartEvents = _serviceStartEvents.receiveAsFlow()

    private val _serviceStopEvents = Channel<Unit>(Channel.BUFFERED)
    val serviceStopEvents = _serviceStopEvents.receiveAsFlow()

    fun onIntent(intent: ProtectionControlIntent) = componentScope.launch {
        when (intent) {
            is ProtectionControlIntent.EnabledChange -> {
                if (intent.isEnabled) {
                    _serviceStartEvents.send(Unit)
                } else {
                    _serviceStopEvents.send(Unit)
                }
            }

            ProtectionControlIntent.ShowInfo -> {
                val state = _state.value

                if (state !is ProtectionControlViewState.Ok) {
                    return@launch
                }

                _state.value = state.copy(
                    messageDialogState = MessageDialogState.Info(
                        title = strings.info,
                        message = strings.serviceStatusInfoDialogMessage
                    )
                )
            }

            ProtectionControlIntent.DialogDismiss -> {
                val state = _state.value

                if (state !is ProtectionControlViewState.Ok) {
                    return@launch
                }

                _state.value = state.copy(messageDialogState = MessageDialogState.Gone)
            }
        }
    }

    override fun onStatusReceived(status: ProtectionStatus) {
        _state.value = ProtectionControlViewState.Ok(
            isServiceEnabled = status == ProtectionStatus.ENABLED
        )
    }

    data class Args(
        val componentContext: ComponentContext
    )

    companion object {
        fun create(
            args: Args
        ) = ProtectionControlComponent(
            componentContext = args.componentContext
        )
    }
}