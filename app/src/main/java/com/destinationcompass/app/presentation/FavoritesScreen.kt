package com.destinationcompass.app.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.destinationcompass.app.model.Destination
import com.destinationcompass.app.model.DestinationIcon
import java.util.Locale

@Composable
fun FavoritesScreen(
    favorites: List<Destination>,
    currentDestination: Destination?,
    onUse: (Destination) -> Unit,
    onAdd: (Destination) -> Unit,
    onUpdate: (Destination) -> Unit,
    onDelete: (String) -> Unit
) {
    var editing by remember { mutableStateOf<Destination?>(null) }
    var expandedId by remember { mutableStateOf<String?>(null) }

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        if (favorites.isEmpty()) {
            Column(Modifier.align(Alignment.Center).padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.BookmarkBorder, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text("还没有收藏地点", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("保存常用目的地，下次可以立即开始导航", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 16.dp, 20.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("收藏", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text("轻触卡片查看详情", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp, bottom = 8.dp))
                }
                items(favorites, key = { it.id }) { favorite ->
                    FavoriteContainerTransformCard(
                        favorite = favorite,
                        expanded = expandedId == favorite.id,
                        onToggle = { expandedId = if (expandedId == favorite.id) null else favorite.id },
                        onUse = { onUse(favorite) },
                        onEdit = { editing = favorite },
                        onDelete = { onDelete(favorite.id) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { currentDestination?.let(onAdd) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) { Icon(Icons.Filled.Add, "收藏当前目标") }
    }

    editing?.let { favorite ->
        EditFavoriteDialog(favorite, onDismiss = { editing = null }) {
            onUpdate(it)
            editing = null
        }
    }
}

@Composable
private fun FavoriteContainerTransformCard(
    favorite: Destination,
    expanded: Boolean,
    onToggle: () -> Unit,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val corner by animateDpAsState(
        targetValue = if (expanded) 24.dp else 16.dp,
        animationSpec = spring(dampingRatio = .86f, stiffness = Spring.StiffnessMediumLow),
        label = "container corner transform"
    )
    val containerColor by animateColorAsState(
        targetValue = if (expanded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = spring(dampingRatio = .9f, stiffness = Spring.StiffnessMediumLow),
        label = "container color transform"
    )
    Card(
        shape = RoundedCornerShape(corner),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth().animateContentSize(
            animationSpec = spring(dampingRatio = .86f, stiffness = Spring.StiffnessMediumLow)
        ).clickable(onClick = onToggle)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Surface(
                    shape = MaterialTheme.shapes.large,
                    color = if (expanded) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(favorite.icon.imageVector(), null, Modifier.padding(11.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(favorite.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(favorite.address.ifBlank { "已保存的位置" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (expanded) 3 else 1)
                }
            }

            AnimatedVisibility(
                expanded,
                enter = fadeIn() + scaleIn(initialScale = .96f),
                exit = fadeOut() + scaleOut(targetScale = .98f)
            ) {
                Column {
                    Text(
                        String.format(Locale.US, "%.4f, %.4f", favorite.latitude, favorite.longitude),
                        Modifier.padding(top = 16.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "删除") }
                        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "编辑") }
                        Spacer(Modifier.width(6.dp))
                        Button(onClick = onUse) {
                            Icon(Icons.Filled.Navigation, null)
                            Spacer(Modifier.width(8.dp))
                            Text("设为目标")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditFavoriteDialog(value: Destination, onDismiss: () -> Unit, onSave: (Destination) -> Unit) {
    var name by remember(value) { mutableStateOf(value.name) }
    var address by remember(value) { mutableStateOf(value.address) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑收藏") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true)
                OutlinedTextField(address, { address = it }, label = { Text("地址") }, minLines = 2)
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(value.copy(name = name.trim(), address = address.trim())) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun DestinationIcon.imageVector(): ImageVector = when (this) {
    DestinationIcon.HOME -> Icons.Filled.Home
    DestinationIcon.WORK -> Icons.Filled.Business
    DestinationIcon.PARKING -> Icons.Filled.DirectionsCar
    DestinationIcon.PLACE -> Icons.Filled.LocationOn
}
