package ru.heatrk.antivirus.presentation.screens.scanner.more

import com.arkivanov.decompose.ComponentContext
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.heatrk.antivirus.data.models.ApiMessage
import ru.heatrk.antivirus.domain.models.ScanState
import ru.heatrk.antivirus.domain.models.ScannerEntryStatus
import ru.heatrk.antivirus.domain.repositories.MessagingRepository
import ru.heatrk.antivirus.presentation.common.Component
import ru.heatrk.antivirus.presentation.dialogs.MessageDialogState
import ru.heatrk.antivirus.presentation.routing.Router
import ru.heatrk.antivirus.presentation.values.strings.strings

class ScanningMoreInfoComponent(
    componentContext: ComponentContext,
    private val router: Router,
    private val messagingRepository: MessagingRepository
) : Component(componentContext) {
    private val _state = MutableStateFlow<ScanningMoreInfoViewState>(ScanningMoreInfoViewState.Loading)
    val state = _state.asStateFlow()

    private val _scanState = messagingRepository.scanState(componentScope)

    init {
        _scanState.onEach { updateState() }.launchIn(componentScope)
    }

    fun onIntent(intent: ScanningMoreInfoIntent) {
        when (intent) {
            is ScanningMoreInfoIntent.Back -> router.navigateUp()
            is ScanningMoreInfoIntent.Pause -> applyPauseScanIntent(intent)
            is ScanningMoreInfoIntent.Resume -> applyResumeScanIntent(intent)
            is ScanningMoreInfoIntent.Stop -> applyStopScanIntent(intent)
            is ScanningMoreInfoIntent.Quarantine -> Unit
            is ScanningMoreInfoIntent.CheckedChange -> Unit
        }
    }

    private fun applyPauseScanIntent(intent: ScanningMoreInfoIntent.Pause) = componentScope.launch {
        handleRequestResult(messagingRepository.pauseScan())
    }

    private fun applyResumeScanIntent(intent: ScanningMoreInfoIntent.Resume) = componentScope.launch {
        handleRequestResult(messagingRepository.resumeScan())
    }

    private fun applyStopScanIntent(intent: ScanningMoreInfoIntent.Stop) = componentScope.launch {
        handleRequestResult(messagingRepository.stopScan())
    }

    private fun updateState() = when (val scanState = _scanState.value) {
        ScanState.Empty -> {
            updateOkState(
                isScanned = true,
                isPaused = false,
                entries = persistentListOf()
            )
        }
        is ScanState.Running -> {
            updateOkState(
                isScanned = false,
                isPaused = scanState.isPaused,
                entries = scanState.entries
            )
        }
        is ScanState.VirusesDetected -> {
            updateOkState(
                isScanned = true,
                isPaused = false,
                entries = scanState.viruses.map {
                    it to ScannerEntryStatus.SCANNED_INFECTED
                }
            )
        }
    }

    private fun updateOkState(
        isScanned: Boolean,
        isPaused: Boolean,
        entries: List<Pair<String, ScannerEntryStatus>>
    ) {
        val state = state.value

        _state.value = if (state is ScanningMoreInfoViewState.Ok) {
            state.copy(
                isScanned = isScanned,
                isPaused = isPaused,
                items = entries.map { entry ->
                    ScanningMoreInfoItem(
                        path = entry.first,
                        status = entry.second,
                        isChecked = state.items.find { it.path == entry.first }?.isChecked ?: false,
                        isCheckBoxVisible = isScanned
                    )
                }.toImmutableList()
            )
        } else {
            ScanningMoreInfoViewState.Ok(
                isScanned = isScanned,
                isPaused = isPaused,
                items = entries.map { entry ->
                    ScanningMoreInfoItem(
                        path = entry.first,
                        status = entry.second,
                        isChecked = false,
                        isCheckBoxVisible = isScanned
                    )
                }.toImmutableList()
            )
        }
    }

    private fun <T> handleRequestResult(result: ApiMessage<T>, onSuccess: ((T) -> Unit)? = null) {
        when (result) {
            is ApiMessage.Ok -> {
                onSuccess?.invoke(result.body)
            }
            is ApiMessage.Fail -> {
                _state.value = when (val state = _state.value) {
                    is ScanningMoreInfoViewState.Ok -> {
                        state.copy(
                            messageDialogState = MessageDialogState.Error(
                                title = strings.error,
                                message = strings.errorMessage(
                                    description = result.description,
                                    errorCode = result.errorCode
                                )
                            )
                        )
                    }
                    else -> state
                }
            }
        }
    }

    data class Args(
        val componentContext: ComponentContext,
        val router: Router
    )

    companion object {
        fun create(
            args: Args,
            messagingRepository: MessagingRepository,
        ) = ScanningMoreInfoComponent(
            componentContext = args.componentContext,
            messagingRepository = messagingRepository,
            router = args.router
        )
    }
}