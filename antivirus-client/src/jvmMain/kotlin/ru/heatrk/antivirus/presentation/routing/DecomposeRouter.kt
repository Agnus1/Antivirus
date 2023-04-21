@file:Suppress("OPT_IN_IS_NOT_ENABLED")

package ru.heatrk.antivirus.presentation.routing

import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.Children
import com.arkivanov.decompose.router.stack.*
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.parcelable.Parcelable
import org.kodein.di.DI
import org.kodein.di.instance
import ru.heatrk.antivirus.presentation.screens.antivirus_root.AntivirusRootComponent
import ru.heatrk.antivirus.presentation.screens.antivirus_root.AntivirusRootScreen
import ru.heatrk.antivirus.presentation.screens.scanner.more.ScanningMoreInfo
import ru.heatrk.antivirus.presentation.screens.scanner.more.ScanningMoreInfoComponent

class DecomposeRouter(
    componentContext: ComponentContext,
    private val di: DI
): Router {
    private val navigation = StackNavigation<Config>()

    private val stack =
        componentContext.childStack(
            source = navigation,
            initialConfiguration = Config.AntivirusRootScreen,
            handleBackButton = true,
            childFactory = ::createChild,
        )

    private val childStack: Value<ChildStack<*, @Composable () -> Unit>> get() = stack

    @Composable
    override fun Container() {
        Children(childStack) {
            it.instance()
        }
    }

    override fun navigateUp() {
        navigation.pop()
    }

    override fun openScanningMoreInfo() {
        navigation.push(Config.ScannerMoreInfoScreen)
    }

    private fun createChild(config: Config, componentContext: ComponentContext): @Composable () -> Unit =
        when (config) {
            is Config.AntivirusRootScreen -> createAntivirusRootScreen(componentContext)
            is Config.ScannerMoreInfoScreen -> createScannerMoreInfoScreen(componentContext)
        }

    private fun createAntivirusRootScreen(componentContext: ComponentContext): @Composable () -> Unit = {
        val component by di.instance<AntivirusRootComponent.Args, AntivirusRootComponent>(
            arg = AntivirusRootComponent.Args(
                componentContext = componentContext,
                router = this
            )
        )

        AntivirusRootScreen(component = component)
    }

    private fun createScannerMoreInfoScreen(componentContext: ComponentContext): @Composable () -> Unit = {
        val component by di.instance<ScanningMoreInfoComponent.Args, ScanningMoreInfoComponent>(
            arg = ScanningMoreInfoComponent.Args(
                componentContext = componentContext,
                router = this
            )
        )

        ScanningMoreInfo(component = component)
    }

    private sealed class Config: Parcelable {
        object AntivirusRootScreen: Config()
        object ScannerMoreInfoScreen : Config()
    }
}