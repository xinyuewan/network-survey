@file:JvmName("ComposeFunctions")

package com.craxiom.networksurvey.ui.cellular

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.ui.theme.NsTheme

fun setContent(composeView: ComposeView, viewModel: CellularChartViewModel) {
    composeView.apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            NsTheme {
                CellularChartComponent(viewModel = viewModel)
            }
        }
    }
}

val Int.nonScaledSp
    @Composable
    get() = (this / LocalDensity.current.fontScale).sp
