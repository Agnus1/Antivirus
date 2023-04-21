package ru.heatrk.antivirus.presentation.screens.scanner.more

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import kotlinx.collections.immutable.persistentListOf
import ru.heatrk.antivirus.domain.models.ScannerEntryStatus
import ru.heatrk.antivirus.presentation.values.dimens.ElementsDimens
import ru.heatrk.antivirus.presentation.values.dimens.InsetsDimens
import ru.heatrk.antivirus.presentation.values.images.Drawables
import ru.heatrk.antivirus.presentation.values.strings.strings
import ru.heatrk.antivirus.presentation.values.styles.ApplicationTheme

@Composable
fun ScanningMoreInfo(
    component: ScanningMoreInfoComponent
) {
    val state by component.state.collectAsState()

    ScanningMoreInfo(
        state = state,
        onIntent = component::onIntent
    )
}

@Composable
fun ScanningMoreInfo(
    state: ScanningMoreInfoViewState,
    onIntent: (ScanningMoreInfoIntent) -> Unit
) {
    when (state) {
        is ScanningMoreInfoViewState.Loading -> ScanningMoreInfoLoading(state)
        is ScanningMoreInfoViewState.Ok -> ScanningMoreInfoOk(state, onIntent)
    }
}

@Composable
fun ScanningMoreInfoLoading(
    state: ScanningMoreInfoViewState.Loading
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(InsetsDimens.ExtraLarge)
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun ScanningMoreInfoOk(
    state: ScanningMoreInfoViewState.Ok,
    onIntent: (ScanningMoreInfoIntent) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(InsetsDimens.ExtraLarge)
    ) {
        IconButton(
            onClick = { onIntent(ScanningMoreInfoIntent.Back) },
            modifier = Modifier
                .size(ElementsDimens.BackButtonIconSize)
        ) {
            Icon(
                painter = painterResource(Drawables.Back),
                contentDescription = strings.back
            )
        }

        Spacer(modifier = Modifier.height(InsetsDimens.ExtraLarge))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            itemsIndexed(
                items = state.items,
                key = { index, item -> item.path }
            ) { index, item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .background(
                            if (index % 2 == 0) {
                                ApplicationTheme.colors.lightBackground
                            } else {
                                Color.Transparent
                            }
                        )
                ) {
                    Text(
                        text = item.path,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = InsetsDimens.Default)
                            .wrapContentHeight()
                    )

                    Spacer(modifier = Modifier.width(InsetsDimens.Default))

                    when (item.status) {
                        ScannerEntryStatus.NOT_SCANNED -> {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(ElementsDimens.ScanningInfoItemIconSize)
                            )
                        }
                        ScannerEntryStatus.SCANNED_INFECTED -> {
                            Icon(
                                painter = painterResource(Drawables.ErrorIcon),
                                tint = ApplicationTheme.colors.error,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(ElementsDimens.ScanningInfoItemIconSize)
                            )
                        }
                        ScannerEntryStatus.SCANNED_NOT_INFECTED -> {
                            Icon(
                                painter = painterResource(Drawables.Ok),
                                tint = Color.Unspecified,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(ElementsDimens.ScanningInfoItemIconSize)
                            )
                        }
                    }

                    if (item.isCheckBoxVisible) {
                        Checkbox(
                            checked = item.isChecked,
                            onCheckedChange = {
                                onIntent(ScanningMoreInfoIntent.CheckedChange(item, it))
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(InsetsDimens.Default))

        Surface(
            shape = ApplicationTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(InsetsDimens.Default)
            ) {
                Button(
                    onClick = { onIntent(ScanningMoreInfoIntent.Quarantine) }
                ) {
                    Text(text = strings.quarantine)
                }

                Spacer(modifier = Modifier.weight(1f))

                if (!state.isScanned) {
                    Button(
                        onClick = {
                            if (state.isPaused) {
                                onIntent(ScanningMoreInfoIntent.Resume)
                            } else {
                                onIntent(ScanningMoreInfoIntent.Pause)
                            }
                        }
                    ) {
                        Text(
                            text = if (state.isPaused) {
                                strings.resume
                            } else {
                                strings.pause
                            }
                        )
                    }

                    Spacer(modifier = Modifier.width(InsetsDimens.Default))

                    Button(
                        onClick = { onIntent(ScanningMoreInfoIntent.Stop) }
                    ) {
                        Text(text = strings.stop)
                    }
                }
            }
        }
    }
}

@Composable
@Preview
fun ScanningMoreInfoOkPreview() {
    ApplicationTheme {
        ScanningMoreInfoOk(
            state = ScanningMoreInfoViewState.Ok(
                isScanned = false,
                isPaused = true,
                items = persistentListOf(
                    ScanningMoreInfoItem(
                        path = "D://Programs/first.exe",
                        status = ScannerEntryStatus.NOT_SCANNED,
                        isChecked = false,
                        isCheckBoxVisible = false
                    ),

                    ScanningMoreInfoItem(
                        path = "D://Programs/second.exe",
                        status = ScannerEntryStatus.SCANNED_NOT_INFECTED,
                        isChecked = false,
                        isCheckBoxVisible = false
                    ),

                    ScanningMoreInfoItem(
                        path = "D://Programs/third.exe",
                        status = ScannerEntryStatus.SCANNED_INFECTED,
                        isChecked = false,
                        isCheckBoxVisible = false
                    ),
                )
            ),
            onIntent = {}
        )
    }
}