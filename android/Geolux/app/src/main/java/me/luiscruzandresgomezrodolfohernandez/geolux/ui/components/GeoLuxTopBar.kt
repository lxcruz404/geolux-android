package me.luiscruzandresgomezrodolfohernandez.geolux.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import me.luiscruzandresgomezrodolfohernandez.geolux.R
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.catalog.ChipFamily
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.map.rememberIsOnline
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxDimens
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxTheme

/**
 * Barra superior (DESIGN.md §8): «GEOLUX», nombre de la pantalla y chip con el
 * estado real de la red («Con Internet» o «Sin conexión»: la finca se consulta y
 * se edita igual; solo el mapa base depende de Internet). Sin avatar ni nube. Con [onBack] muestra la flecha de volver.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoLuxTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    TopAppBar(
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            }
        },
        title = {
            Column {
                Text(
                    text = stringResource(R.string.brand),
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp),
                    // Verde de texto de §3.2: legible en claro y en oscuro (§11).
                    color = GeoLuxTheme.colors.chipGreen.content,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        actions = {
            val online = rememberIsOnline()
            StatusChip(
                text = stringResource(if (online) R.string.chip_con_internet else R.string.chip_sin_conexion),
                family = if (online) ChipFamily.Green else ChipFamily.Neutral,
                modifier = Modifier.padding(end = GeoLuxDimens.screenMargin),
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}
