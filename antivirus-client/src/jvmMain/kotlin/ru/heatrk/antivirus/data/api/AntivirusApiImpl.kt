package ru.heatrk.antivirus.data.api

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Advapi32
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinBase.OVERLAPPED
import com.sun.jna.platform.win32.WinNT.*
import com.sun.jna.platform.win32.Winsvc
import com.sun.jna.ptr.IntByReference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ru.heatrk.antivirus.data.models.ApiMessage
import ru.heatrk.antivirus.data.models.MessageMethod
import ru.heatrk.antivirus.data.models.MessageStatus
import ru.heatrk.antivirus.data.models.ScannerCacheData
import ru.heatrk.antivirus.data.models.structs.MessageBodyScanStartStruct
import ru.heatrk.antivirus.data.models.structs.MessageStruct
import ru.heatrk.antivirus.utils.DynamicByteBuffer
import ru.heatrk.antivirus.utils.app_directory_provider.AppDirectoryProvider
import java.io.File
import java.nio.ByteBuffer

class AntivirusApiImpl(
    appDirectoryProvider: AppDirectoryProvider
) : AntivirusApi {

    private val kernel32 = Kernel32.INSTANCE
    private val advapi32 = Advapi32.INSTANCE

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var pipeListeningJob: Job? = null

    private val _incomingMessages = MutableSharedFlow<ApiMessage<MessageStruct>>()
    override val incomingMessages = _incomingMessages.asSharedFlow()

    private val _scannerCacheData = MutableStateFlow<ScannerCacheData?>(null)
    override val scannerCacheData = _scannerCacheData.asStateFlow()

    private var outputPipe: HANDLE = INVALID_HANDLE_VALUE
    private var inputPipe: HANDLE = INVALID_HANDLE_VALUE

    private val appDirectory = appDirectoryProvider.providePath()

    init {
        coroutineScope.launch {
            updateScannerCacheData()
            println(appDirectory)
            val file = kernel32.CreateFile(
                appDirectory,
                FILE_LIST_DIRECTORY,
                FILE_SHARE_READ,
                WinBase.SECURITY_ATTRIBUTES(),
                OPEN_EXISTING,
                FILE_FLAG_BACKUP_SEMANTICS,
                HANDLE(Pointer.NULL)
            )

            if (file == INVALID_HANDLE_VALUE) {
                println("Cant CreateFile for changes listening")
                return@launch
            }

            while (isActive) {
                var fileNotifyInformation: FILE_NOTIFY_INFORMATION?
                        = FILE_NOTIFY_INFORMATION(FILE_NOTIFY_INFORMATION_SIZE)

                val isReadChangesSuccess = kernel32.ReadDirectoryChangesW(
                    file,
                    fileNotifyInformation,
                    FILE_NOTIFY_INFORMATION_SIZE,
                    true,
                    FILE_NOTIFY_CHANGE_LAST_WRITE or FILE_NOTIFY_CHANGE_FILE_NAME,
                    IntByReference(),
                    OVERLAPPED()
                ) { _, _, _ -> /* Empty */ }

                if (!isReadChangesSuccess) {
                    println("Error while read directory changes (code ${kernel32.GetLastError()})")
                    delay(1000)
                    continue
                }

                while (fileNotifyInformation != null) {
                    val isActionValid =
                        fileNotifyInformation.Action == FILE_ACTION_MODIFIED ||
                            fileNotifyInformation.Action == FILE_ACTION_REMOVED ||
                            fileNotifyInformation.Action == FILE_ACTION_ADDED

                    if (!isActionValid) {
                        fileNotifyInformation = fileNotifyInformation.next()
                        continue
                    }

                    val isFileValid =
                        String(fileNotifyInformation.FileName) == SCANNER_DATA_FILE_NAME

                    if (!isFileValid) {
                        fileNotifyInformation = fileNotifyInformation.next()
                        continue
                    }

                    updateScannerCacheData()

                    fileNotifyInformation = null
                }
            }

        }
    }

    override suspend fun startScan(path: String): ApiMessage<Unit> {
        val requestMessage = MessageStruct().apply {
            MessageMethod.SCAN_START.id.copyInto(this.method)
            status = MessageStatus.REQUEST.id
            val bodyBytesBuffer = DynamicByteBuffer()
            MessageBodyScanStartStruct().apply {
                this.path = Native.toByteArray(path)
            }.write(bodyBytesBuffer)
            bodyBytesBuffer.position(0)
            bodySize = bodyBytesBuffer.capacity()
            body = ByteArray(bodySize)
            bodyBytesBuffer.get(body)
        }

        return send(requestMessage)
    }

    override suspend fun pauseScan(): ApiMessage<Unit> {
        val requestMessage = MessageStruct().apply {
            MessageMethod.SCAN_PAUSE.id.copyInto(this.method)
            status = MessageStatus.REQUEST.id
        }

        return send(requestMessage)
    }

    override suspend fun resumeScan(): ApiMessage<Unit> {
        val requestMessage = MessageStruct().apply {
            MessageMethod.SCAN_RESUME.id.copyInto(this.method)
            status = MessageStatus.REQUEST.id
        }

        return send(requestMessage)
    }

    override suspend fun stopScan(): ApiMessage<Unit> {
        val requestMessage = MessageStruct().apply {
            MessageMethod.SCAN_STOP.id.copyInto(this.method)
            status = MessageStatus.REQUEST.id
        }

        return send(requestMessage)
    }

    override suspend fun isProtectionEnabled(): ApiMessage<MessageStruct> {
        val pipeConnectionResult = connectPipes()

        if (pipeConnectionResult !is ApiMessage.Ok) {
            return ApiMessage.Fail(description = "Pipes connection failed [Protection enabled check]")
        }

        val requestMessage = MessageStruct().apply {
            MessageMethod.IS_PROTECTED.id.copyInto(this.method)
            status = MessageStatus.REQUEST.id
        }

        withTimeoutOrNull(REQUEST_TIMEOUT) {
            send(requestMessage)
        }

        val responseMessage = withTimeoutOrNull(RESPONSE_TIMEOUT) {
            incomingMessages.transformWhile { message ->
                if (message is ApiMessage.Ok && message.body.isUuidEquals(requestMessage)) {
                    emit(message)
                    return@transformWhile false
                }
                return@transformWhile true
            }.first()
        }

        return responseMessage ?: ApiMessage.Fail("Request timeout")
    }

    override suspend fun enableProtection(): ApiMessage<Unit> {
        val requestMessage = MessageStruct().apply {
            MessageMethod.ENABLE_PROTECTION.id.copyInto(this.method)
            status = MessageStatus.REQUEST.id
        }

        return send(requestMessage)
    }

    override suspend fun disableProtection(): ApiMessage<Unit> {
        val requestMessage = MessageStruct().apply {
            MessageMethod.DISABLE_PROTECTION.id.copyInto(this.method)
            status = MessageStatus.REQUEST.id
        }

        return send(requestMessage)
    }

    private fun reset() {
        pipeListeningJob?.cancel()
        pipeListeningJob = null
        inputPipe = INVALID_HANDLE_VALUE
        outputPipe = INVALID_HANDLE_VALUE
    }

    private fun connectPipes(): ApiMessage<Unit> {
        if (inputPipe != INVALID_HANDLE_VALUE && outputPipe != INVALID_HANDLE_VALUE) {
            return ApiMessage.Ok(Unit)
        }

        outputPipe = kernel32.CreateFile(
            PIPE_SERVICE_INPUT_PATH,
            GENERIC_WRITE,
            0,
            WinBase.SECURITY_ATTRIBUTES(),
            OPEN_EXISTING,
            0,
            HANDLE(Pointer.NULL)
        )

        if (outputPipe == INVALID_HANDLE_VALUE) {
            return ApiMessage.Fail(
                description = "Output pipe connecting",
                errorCode = kernel32.GetLastError()
            )
        }

        inputPipe = kernel32.CreateFile(
            PIPE_SERVICE_OUTPUT_PATH,
            GENERIC_READ,
            0,
            WinBase.SECURITY_ATTRIBUTES(),
            OPEN_EXISTING,
            0,
            HANDLE(Pointer.NULL)
        )

        if (inputPipe == INVALID_HANDLE_VALUE) {
            return ApiMessage.Fail(
                description = "Input pipe connecting",
                errorCode = kernel32.GetLastError()
            )
        }

        pipeListeningJob = coroutineScope.launch {
            val buffer = ByteArray(PIPE_BUFFSIZE)

            while (inputPipe != INVALID_HANDLE_VALUE) {
                val isSuccess = kernel32.ReadFile(
                    inputPipe,
                    buffer,
                    PIPE_BUFFSIZE,
                    IntByReference(),
                    OVERLAPPED()
                )

                if (isSuccess) {
                    println("Message bytes:\n${buffer.contentToString()}\n-------------")

                    _incomingMessages.emit(ApiMessage.Ok(
                        MessageStruct.create(buffer).also {
                            println("Mapped message:\n${it}\n------------")
                        }
                    ))
                } else {
                    _incomingMessages.emit(ApiMessage.Fail(
                        description = "Input pipe reading",
                        errorCode = kernel32.GetLastError()
                    ))
                }
            }
        }

        return ApiMessage.Ok(Unit)
    }

    private fun updateScannerCacheData() {
        val scannerDataFilePath = appDirectory + SCANNER_DATA_FILE_NAME
        val scannerDataFile = File(scannerDataFilePath)

        if (!scannerDataFile.exists()) {
            _scannerCacheData.value = null
            return
        }

        val scannerDataFileBytes = scannerDataFile.readBytes()

        if (scannerDataFileBytes.isEmpty()) {
            _scannerCacheData.value = null
            return
        }

        val byteBuffer = ByteBuffer.wrap(scannerDataFileBytes)

        val scannerStatus = byteBuffer.get()

        val entries = mutableListOf<Pair<String, Byte>>()

        while (byteBuffer.hasRemaining()) {
            val entryStatus = byteBuffer.get()
            val entryLength = byteBuffer.int
            val entry = ByteArray(entryLength)
            byteBuffer.get(entry)
            entries.add(Native.toString(entry) to entryStatus)
        }

        _scannerCacheData.value = ScannerCacheData(
            scannerStatus = scannerStatus,
            entries = entries
        )
    }

    private fun send(struct: MessageStruct): ApiMessage<Unit> {
        val bytes = ByteArray(PIPE_BUFFSIZE).also { struct.write(it) }

        return if (
            kernel32.WriteFile(
                outputPipe,
                bytes,
                PIPE_BUFFSIZE,
                IntByReference(),
                null
            )
        ) {
            ApiMessage.Ok(Unit)
        } else {
            ApiMessage.Fail(
                description = "Output pipe writing",
                errorCode = kernel32.GetLastError()
            )
        }
    }

    companion object {
        private const val PIPE_SERVICE_INPUT_PATH = "\\\\.\\pipe\\antivirus-pipe-service-input"
        private const val PIPE_SERVICE_OUTPUT_PATH = "\\\\.\\pipe\\antivirus-pipe-service-output"
        private const val PIPE_BUFFSIZE = 4096
        private const val REQUEST_TIMEOUT = 5000L
        private const val RESPONSE_TIMEOUT = 5000L
        private const val FILE_NOTIFY_INFORMATION_SIZE = 1024
        private const val SCANNER_DATA_FILE_NAME = "scanner-data"
    }
}