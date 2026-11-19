package me.luiscruzandresgomezrodolfohernandez.geolux.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.luiscruzandresgomezrodolfohernandez.geolux.R
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.catalog.ChipFamily
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.catalog.colors
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.format.splitValueUnit
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxDimens
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxShape

// Componentes de DESIGN.md §8 que comparten las pantallas.

/** Chip de estado (§3.2): `labelMedium`, radio 8 dp. Siempre lleva texto, nunca solo color. */
@Composable
fun StatusChip(
    text: String,
    family: ChipFamily,
    modifier: Modifier = Modifier,
) {
    val colors = family.colors()
    Surface(
        modifier = modifier,
        shape = GeoLuxShape.chip,
        color = colors.container,
        contentColor = colors.content,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Tarjeta plana con borde `outline` (§5); clicable si hay [onClick]. */
@Composable
fun GeoLuxCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val border = BorderStroke(GeoLuxDimens.cardBorder, MaterialTheme.colorScheme.outline)
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = if (contentPadding) Modifier.padding(GeoLuxDimens.cardPadding) else Modifier,
            content = content,
        )
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = GeoLuxShape.card, colors = colors, border = border, content = inner)
    } else {
        Card(modifier = modifier, shape = GeoLuxShape.card, colors = colors, border = border, content = inner)
    }
}

/**
 * Tarjeta de dato clave (§8): etiqueta (`labelLarge`, `onSurfaceVariant`),
 * cifra (`displaySmall`) y unidad. [value] llega ya formateado («245.30 ha»).
 */
@Composable
fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val (number, unit) = splitValueUnit(value)
    GeoLuxCard(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(GeoLuxDimens.spacingUnit))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = number,
                // Un texto («No especificado») no usa la cifra grande.
                style = if (unit.isEmpty() && number.toDoubleOrNull() == null) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.displaySmall
                },
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(GeoLuxDimens.spacingUnit))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
    }
}

/** Cuadrícula de 2 columnas de datos clave (§8), con las tarjetas a la misma altura. */
@Composable
fun MetricRow(
    first: Pair<String, String>,
    second: Pair<String, String>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(GeoLuxDimens.cardSpacing),
    ) {
        MetricCard(first.first, first.second, Modifier.weight(1f).fillMaxHeight())
        MetricCard(second.first, second.second, Modifier.weight(1f).fillMaxHeight())
    }
}

/** Título de sección (`titleMedium`) con un texto secundario opcional a la derecha. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (trailing != null) {
            Text(text = trailing, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Punto de color de una fila de lista (§8). */
@Composable
fun ColorDot(color: Color, modifier: Modifier = Modifier, size: Int = 12) {
    Box(modifier.size(size.dp).background(color, CircleShape))
}

/**
 * Fila de lista (§8): 56–72 dp, punto de color, título y subtítulo, y al
 * final un chip, un texto o una flecha si es clicable.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    subtitle: String? = null,
    leadingColor: Color? = null,
    trailing: @Composable (() -> Unit)? = null,
    showChevron: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = GeoLuxDimens.listRowMinHeight)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingColor != null) {
            ColorDot(leadingColor)
            Spacer(Modifier.width(GeoLuxDimens.cardPadding))
        }
        Column(Modifier.weight(1f)) {
            if (overline != null) {
                Text(
                    text = overline.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(GeoLuxDimens.spacingUnit * 2))
            trailing()
        }
        if (showChevron) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Botón principal: 12 dp de radio (§5), ancho completo, 48 dp de alto mínimo. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        shape = GeoLuxShape.button,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = GeoLuxDimens.minTouchTarget),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Mensaje centrado para una lista o pantalla vacía. */
@Composable
fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = GeoLuxDimens.spacingUnit * 2),
    )
}
