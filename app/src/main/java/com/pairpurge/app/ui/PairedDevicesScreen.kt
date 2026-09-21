package com.pairpurge.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pairpurge.app.settings.ConnectionCleanup
import com.pairpurge.app.settings.DeletionSettingsStore
import com.pairpurge.app.R
import com.pairpurge.app.bluetooth.PairedDevice
import com.pairpurge.app.ui.theme.MacAddressTextStyle
import com.pairpurge.app.ui.theme.PairPurgeIcons
import com.pairpurge.app.ui.theme.PairPurgeTheme

private const val BLUETOOTH_PERMISSION = Manifest.permission.BLUETOOTH_CONNECT

/** Pages behind the bottom navigation. */
private enum class Destination { Devices, Protected, Settings }

/** What an open confirmation dialog is about to unpair. */
private sealed interface UnpairRequest {
    data class One(val device: PairedDevice) : UnpairRequest
    data object Selected : UnpairRequest
}

@Composable
fun PairedDevicesScreen(
    viewModel: PairedDevicesViewModel = viewModel(factory = PairedDevicesViewModel.Factory),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settingsStore = remember { DeletionSettingsStore(context) }
    var deletionPolicy by remember { mutableStateOf(settingsStore.policy()) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // Only meaningful after a denial: on a fresh install shouldShowRationale is
        // false because the user has never been asked, so checking it earlier would
        // misreport a never-asked permission as permanently denied.
        permanentlyDenied = !granted && !context.shouldShowBluetoothRationale()
        viewModel.refresh(hasPermission = granted, permanentlyDenied = permanentlyDenied)
    }

    // Every state screen sends the user somewhere outside the app — Bluetooth settings,
    // app settings — and the design keeps Refresh out of the header, so coming back has
    // to be what re-reads the adapter. Without this the screens would be dead ends.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        ConnectionCleanup.run(context)
        ConnectionCleanup.schedule(context)
        viewModel.refresh(context.hasBluetoothPermission(), permanentlyDenied)
    }

    var destination by rememberSaveable { mutableStateOf(Destination.Devices) }
    var pendingUnpair by remember { mutableStateOf<UnpairRequest?>(null) }

    val devices = uiState as? PairedDevicesUiState.Devices
    val selectedCount = devices?.selectedAddresses?.size ?: 0

    val showsNav = true
    BackHandler(destination != Destination.Devices) { destination = Destination.Devices }

    val onDevicesPage = destination == Destination.Devices
    val selectionActive = onDevicesPage && selectedCount > 0

    Scaffold(
        containerColor = PairPurgeTheme.colors.background,
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            AppHeader(
                title = when (destination) {
                    Destination.Devices -> stringResource(R.string.app_name)
                    Destination.Protected -> stringResource(R.string.protected_title)
                    Destination.Settings -> stringResource(R.string.settings_title)
                },
            ) {
                when {
                    selectionActive -> TextAction(
                        text = stringResource(R.string.action_clear_selection),
                        color = PairPurgeTheme.colors.muted,
                        onClick = viewModel::clearSelection,
                    )

                    onDevicesPage && devices != null -> RefreshAction {
                        viewModel.refresh(context.hasBluetoothPermission(), permanentlyDenied)
                    }
                }
            }
        },
        bottomBar = {
            when {
                selectionActive -> BulkActionBar(
                    selectedCount = selectedCount,
                    onProtect = viewModel::whitelistSelected,
                    onUnpair = { pendingUnpair = UnpairRequest.Selected },
                )

                showsNav -> BottomNav(
                    destination = destination,
                    onSelect = { destination = it },
                )
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        // The design floats state-screen content slightly above centre, and leaves more
        // room to do it when no nav bar is taking up the bottom of the screen.
        val bottomBias = if (showsNav) 40.dp else 60.dp

        if (destination == Destination.Settings) {
            SettingsScreen(
                policy = deletionPolicy,
                onSave = {
                    settingsStore.save(it)
                    deletionPolicy = it
                    ConnectionCleanup.schedule(context)
                    context.toast(context.getString(R.string.settings_saved))
                },
                modifier = contentModifier,
            )
        } else when (val state = uiState) {
            PairedDevicesUiState.Loading -> LoadingSkeleton(contentModifier)

            is PairedDevicesUiState.NeedsPermission -> if (state.permanentlyDenied) {
                MessageState(
                    icon = PairPurgeIcons.Alert,
                    badge = BadgeTone.Neutral,
                    headline = stringResource(R.string.permission_denied_headline),
                    body = stringResource(R.string.permission_denied_body),
                    modifier = contentModifier,
                    bottomBias = bottomBias,
                ) {
                    OutlinedStateAction(
                        label = stringResource(R.string.action_open_app_settings),
                        onClick = context::openAppSettings,
                    )
                }
            } else {
                MessageState(
                    icon = PairPurgeIcons.Lock,
                    badge = BadgeTone.Accent,
                    headline = stringResource(R.string.permission_headline),
                    body = stringResource(R.string.permission_body),
                    modifier = contentModifier,
                    bottomBias = bottomBias,
                ) {
                    FilledStateAction(
                        label = stringResource(R.string.action_grant),
                        onClick = { permissionLauncher.launch(BLUETOOTH_PERMISSION) },
                    )
                }
            }

            PairedDevicesUiState.BluetoothUnsupported -> MessageState(
                icon = PairPurgeIcons.Bluetooth,
                badge = BadgeTone.Neutral,
                headline = stringResource(R.string.unsupported_headline),
                body = stringResource(R.string.unsupported_body),
                modifier = contentModifier,
                bottomBias = bottomBias,
            )

            PairedDevicesUiState.BluetoothDisabled -> MessageState(
                icon = PairPurgeIcons.BluetoothOff,
                badge = BadgeTone.Accent,
                headline = stringResource(R.string.disabled_headline),
                body = stringResource(R.string.disabled_body),
                modifier = contentModifier,
                bottomBias = bottomBias,
            ) {
                FilledStateAction(
                    label = stringResource(R.string.action_open_bluetooth_settings),
                    onClick = context::openBluetoothSettings,
                )
                // Turning Bluetooth on happens outside the app, so the way back in is
                // offered here as a second step rather than hidden in the header.
                TextAction(
                    text = stringResource(R.string.action_refresh),
                    color = PairPurgeTheme.colors.muted,
                    onClick = {
                        viewModel.refresh(context.hasBluetoothPermission(), permanentlyDenied)
                    },
                )
            }

            PairedDevicesUiState.Empty -> when (destination) {
                Destination.Devices -> MessageState(
                    icon = PairPurgeIcons.Bluetooth,
                    badge = BadgeTone.Neutral,
                    headline = stringResource(R.string.empty_headline),
                    body = stringResource(R.string.empty_body),
                    modifier = contentModifier,
                    bottomBias = bottomBias,
                ) {
                    FilledStateAction(
                        label = stringResource(R.string.action_open_bluetooth_settings),
                        onClick = context::openBluetoothSettings,
                    )
                }

                Destination.Settings -> Unit
                Destination.Protected -> ProtectedEmptyState(contentModifier, bottomBias)
            }

            is PairedDevicesUiState.Devices -> when (destination) {
                Destination.Devices -> if (state.mainDevices.isEmpty()) {
                    MessageState(
                        icon = PairPurgeIcons.StarFilled,
                        badge = BadgeTone.Protect,
                        headline = stringResource(R.string.all_whitelisted_headline),
                        body = pluralStringResource(
                            R.plurals.all_whitelisted_body,
                            state.devices.size,
                            state.devices.size,
                        ),
                        modifier = contentModifier,
                        bottomBias = bottomBias,
                    ) {
                        // The one empty state that points somewhere, so it carries a
                        // link across rather than leaving the user to find the tab.
                        OutlinedStateAction(
                            label = stringResource(R.string.action_view_protected),
                            contentColor = PairPurgeTheme.colors.protectStrong,
                            onClick = { destination = Destination.Protected },
                        )
                    }
                } else {
                    DeviceList(
                        state = state,
                        onToggleDevice = viewModel::toggleSelection,
                        onToggleAll = viewModel::setAllSelected,
                        onToggleSort = viewModel::toggleSortOrder,
                        onUnpairDevice = { pendingUnpair = UnpairRequest.One(it) },
                        modifier = contentModifier,
                    )
                }

                Destination.Settings -> Unit
                Destination.Protected -> if (state.whitelistedDevices.isEmpty()) {
                    ProtectedEmptyState(contentModifier, bottomBias)
                } else {
                    ProtectedList(
                        devices = state.whitelistedDevices,
                        onRemoveDevice = { viewModel.removeFromWhitelist(it.address) },
                        modifier = contentModifier,
                    )
                }
            }
        }
    }

    pendingUnpair?.let { request ->
        val count = when (request) {
            is UnpairRequest.One -> 1
            UnpairRequest.Selected -> selectedCount
        }
        UnpairConfirmationDialog(
            count = count,
            onDismiss = { pendingUnpair = null },
            onConfirm = {
                pendingUnpair = null
                when (request) {
                    is UnpairRequest.One -> if (!viewModel.unpair(request.device.address)) {
                        context.toast(context.getString(R.string.unpair_failed))
                    }

                    UnpairRequest.Selected -> {
                        val failures = viewModel.unpairSelected()
                        if (failures > 0) {
                            context.toast(
                                context.resources.getQuantityString(
                                    R.plurals.unpair_selected_failed,
                                    failures,
                                    failures,
                                ),
                            )
                        }
                    }
                }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Chrome
// ─────────────────────────────────────────────────────────────

@Composable
private fun AppHeader(
    title: String,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(PairPurgeTheme.colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = 56.dp)
            .padding(start = 18.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = PairPurgeTheme.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Composable
private fun RefreshAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = PairPurgeIcons.Refresh,
            contentDescription = stringResource(R.string.action_refresh),
            tint = PairPurgeTheme.colors.accentStrong,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun BottomNav(destination: Destination, onSelect: (Destination) -> Unit) {
    val colors = PairPurgeTheme.colors
    Column(Modifier.background(colors.background)) {
        HorizontalDivider(thickness = 1.dp, color = colors.divider)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
        ) {
            NavItem(
                icon = PairPurgeIcons.Bluetooth,
                label = stringResource(R.string.nav_devices),
                selected = destination == Destination.Devices,
                selectedContainer = colors.accentBadge,
                selectedContent = colors.accentStrong,
                onClick = { onSelect(Destination.Devices) },
            )
            NavItem(
                // Filled once this is the page you are on, outline while it is a place
                // to go — the same distinction the protected rows themselves make.
                icon = if (destination == Destination.Protected) {
                    PairPurgeIcons.StarFilled
                } else {
                    PairPurgeIcons.Star
                },
                label = stringResource(R.string.nav_protected),
                selected = destination == Destination.Protected,
                selectedContainer = colors.protectSoft,
                selectedContent = colors.protectStrong,
                onClick = { onSelect(Destination.Protected) },
            )
            NavItem(
                icon = PairPurgeIcons.Settings,
                label = stringResource(R.string.settings_title),
                selected = destination == Destination.Settings,
                selectedContainer = colors.surface,
                selectedContent = colors.ink,
                onClick = { onSelect(Destination.Settings) },
            )
        }
    }
}

@Composable
private fun RowScope.NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    selectedContainer: Color,
    selectedContent: Color,
    onClick: () -> Unit,
) {
    val content = if (selected) selectedContent else PairPurgeTheme.colors.muted
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .weight(1f)
            .clip(CircleShape)
            .background(if (selected) selectedContainer else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = 9.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = content)
    }
}

@Composable
private fun BulkActionBar(
    selectedCount: Int,
    onProtect: () -> Unit,
    onUnpair: () -> Unit,
) {
    val colors = PairPurgeTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 16.dp),
    ) {
        OutlinedButton(
            onClick = onProtect,
            shape = CircleShape,
            border = BorderStroke(1.5.dp, colors.divider),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = colors.background,
                contentColor = colors.protectStrong,
            ),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp),
            modifier = Modifier.weight(1f),
        ) {
            ActionButtonContent(
                icon = PairPurgeIcons.Star,
                label = stringResource(R.string.action_protect_count, selectedCount),
            )
        }
        Button(
            onClick = onUnpair,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = colors.onAccent,
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp),
            modifier = Modifier.weight(1f),
        ) {
            ActionButtonContent(
                icon = PairPurgeIcons.Trash,
                label = stringResource(R.string.action_unpair_count, selectedCount),
            )
        }
    }
}

@Composable
private fun ActionButtonContent(icon: ImageVector, label: String) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
    Spacer(Modifier.width(8.dp))
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// ─────────────────────────────────────────────────────────────
// Paired devices
// ─────────────────────────────────────────────────────────────

@Composable
private fun DeviceList(
    state: PairedDevicesUiState.Devices,
    onToggleDevice: (String) -> Unit,
    onToggleAll: (Boolean) -> Unit,
    onToggleSort: () -> Unit,
    onUnpairDevice: (PairedDevice) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        item { CountRow(state = state, onToggleAll = onToggleAll, onToggleSort = onToggleSort) }

        items(state.mainDevices, key = { it.address }) { device ->
            val selected = device.address in state.selectedAddresses
            DeviceRow(
                device = device,
                selected = selected,
                onToggle = { onToggleDevice(device.address) },
                onUnpair = { onUnpairDevice(device) },
            )
        }
    }
}

@Composable
private fun CountRow(
    state: PairedDevicesUiState.Devices,
    onToggleAll: (Boolean) -> Unit,
    onToggleSort: () -> Unit,
) {
    val selectedCount = state.selectedAddresses.size
    val toggleState = when {
        selectedCount == 0 -> ToggleableState.Off
        state.allSelected -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .weight(1f)
                .triStateToggleable(
                    state = toggleState,
                    role = Role.Checkbox,
                    // Anything short of "all ticked" means the useful action is
                    // select-all; only a full selection collapses back to clearing it.
                    onClick = { onToggleAll(!state.allSelected) },
                )
                .heightIn(min = 48.dp),
        ) {
            SelectionBox(toggleState)
            Text(
                text = if (selectedCount == 0) {
                    pluralStringResource(
                        R.plurals.paired_device_count,
                        state.mainDevices.size,
                        state.mainDevices.size,
                    )
                } else {
                    stringResource(
                        R.string.selected_count,
                        selectedCount,
                        state.mainDevices.size,
                    )
                },
                style = MaterialTheme.typography.titleSmall,
                color = PairPurgeTheme.colors.ink,
            )
        }
        SortPill(order = state.sortOrder, onClick = onToggleSort)
    }
}

@Composable
private fun SortPill(order: DeviceSortOrder, onClick: () -> Unit) {
    val colors = PairPurgeTheme.colors
    // The pill itself is 26dp tall by design; the tap target around it is not.
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .clickable(
                onClickLabel = stringResource(R.string.action_change_sort),
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .background(colors.surface, CircleShape)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            Icon(
                imageVector = PairPurgeIcons.Sort,
                contentDescription = null,
                tint = colors.muted,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = stringResource(
                    when (order) {
                        DeviceSortOrder.Name -> R.string.sort_by_name
                        DeviceSortOrder.Address -> R.string.sort_by_address
                    },
                ),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp),
                color = colors.muted,
            )
        }
    }
}

@Composable
private fun DeviceRow(
    device: PairedDevice,
    selected: Boolean,
    onToggle: () -> Unit,
    onUnpair: () -> Unit,
) {
    val colors = PairPurgeTheme.colors
    HorizontalDivider(thickness = 1.dp, color = colors.divider)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) colors.accentSoft else Color.Transparent)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 10.dp, top = 11.dp, bottom = 11.dp),
    ) {
        SelectionBox(if (selected) ToggleableState.On else ToggleableState.Off)
        DeviceIdentity(
            device = device,
            // A selected row leans on weight as well as tint, so the selection still
            // reads on a screen where the terracotta wash is hard to see.
            weight = if (selected) FontWeight.W700 else FontWeight.W600,
            modifier = Modifier.weight(1f),
        )
        TextAction(
            text = stringResource(R.string.action_unpair),
            color = colors.accentStrong,
            onClick = onUnpair,
        )
    }
}

@Composable
private fun DeviceIdentity(
    device: PairedDevice,
    weight: FontWeight,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = device.displayName,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = weight),
            color = PairPurgeTheme.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = device.address,
            style = MacAddressTextStyle,
            color = PairPurgeTheme.colors.muted,
        )
    }
}

@Composable
private fun SelectionBox(state: ToggleableState) {
    val colors = PairPurgeTheme.colors
    val shape = RoundedCornerShape(7.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(22.dp)
            .then(
                if (state == ToggleableState.Off) {
                    Modifier.border(2.5.dp, colors.checkboxOutline, shape)
                } else {
                    Modifier.background(colors.accent, shape)
                },
            ),
    ) {
        when (state) {
            // Knocked out in the page ground rather than in onAccent: this is a hole
            // punched through the fill, not a label sitting on a button.
            ToggleableState.On -> Icon(
                imageVector = PairPurgeIcons.Check,
                contentDescription = null,
                tint = colors.background,
                modifier = Modifier.size(14.dp),
            )

            // A bar rather than a tick: some rows are selected, not all of them.
            ToggleableState.Indeterminate -> Box(
                Modifier
                    .size(width = 11.dp, height = 3.dp)
                    .background(colors.background, RoundedCornerShape(2.dp)),
            )

            ToggleableState.Off -> Unit
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Protected devices
// ─────────────────────────────────────────────────────────────

@Composable
private fun ProtectedList(
    devices: List<PairedDevice>,
    onRemoveDevice: (PairedDevice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PairPurgeTheme.colors
    LazyColumn(modifier = modifier) {
        item {
            Text(
                text = stringResource(R.string.protected_blurb),
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 14.dp),
            )
        }

        items(devices, key = { it.address }) { device ->
            HorizontalDivider(thickness = 1.dp, color = colors.divider)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 66.dp)
                    .padding(start = 16.dp, end = 10.dp, top = 11.dp, bottom = 11.dp),
            ) {
                // No checkbox anywhere on this page: nothing here can be swept up by a
                // bulk action, and offering a tick would suggest otherwise.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(34.dp)
                        .background(colors.protectSoft, CircleShape),
                ) {
                    Icon(
                        imageVector = PairPurgeIcons.StarFilled,
                        contentDescription = null,
                        tint = colors.protectStrong,
                        modifier = Modifier.size(17.dp),
                    )
                }
                DeviceIdentity(
                    device = device,
                    weight = FontWeight.W600,
                    modifier = Modifier.weight(1f),
                )
                TextAction(
                    text = stringResource(R.string.action_remove_from_whitelist),
                    color = colors.muted,
                    onClick = { onRemoveDevice(device) },
                )
            }
        }
    }
}

@Composable
private fun ProtectedEmptyState(modifier: Modifier, bottomBias: Dp) {
    MessageState(
        icon = PairPurgeIcons.Star,
        badge = BadgeTone.Neutral,
        headline = stringResource(R.string.whitelist_empty_headline),
        body = stringResource(R.string.whitelist_empty_body),
        modifier = modifier,
        bottomBias = bottomBias,
    )
}

// ─────────────────────────────────────────────────────────────
// State screens
// ─────────────────────────────────────────────────────────────

/** Which pair of colours a state badge wears. */
private enum class BadgeTone { Accent, Neutral, Protect }

@Composable
private fun MessageState(
    icon: ImageVector,
    badge: BadgeTone,
    headline: String,
    body: String,
    modifier: Modifier = Modifier,
    bottomBias: Dp = 60.dp,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = PairPurgeTheme.colors
    val (container, content) = when (badge) {
        BadgeTone.Accent -> colors.accentBadge to colors.accentStrong
        BadgeTone.Neutral -> colors.surface to colors.muted
        BadgeTone.Protect -> colors.protectSoft to colors.protectStrong
    }

    Column(
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(start = 34.dp, end = 34.dp, bottom = bottomBias),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(76.dp)
                .background(container, CircleShape),
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(26.dp))
        Text(
            text = headline,
            style = MaterialTheme.typography.displaySmall,
            color = colors.ink,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.muted,
        )
        Spacer(Modifier.height(28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = actions)
    }
}

@Composable
private fun FilledStateAction(label: String, onClick: () -> Unit) {
    val colors = PairPurgeTheme.colors
    Button(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = colors.onAccent,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun OutlinedStateAction(
    label: String,
    onClick: () -> Unit,
    contentColor: Color = PairPurgeTheme.colors.ink,
) {
    val colors = PairPurgeTheme.colors
    OutlinedButton(
        onClick = onClick,
        shape = CircleShape,
        border = BorderStroke(1.5.dp, colors.divider),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.background,
            contentColor = contentColor,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun LoadingSkeleton(modifier: Modifier = Modifier) {
    val colors = PairPurgeTheme.colors
    // Placeholder rows in surface tint rather than a spinner on a blank screen: the
    // shape of what is coming is more use than the fact that something is coming.
    Column(modifier) {
        repeat(6) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Box(
                    Modifier
                        .size(22.dp)
                        .background(colors.surface, RoundedCornerShape(7.dp)),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    SkeletonBar(widthFraction = 0.58f, height = 12.dp, color = colors.surface)
                    SkeletonBar(
                        widthFraction = 0.38f,
                        height = 10.dp,
                        color = colors.surface.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SkeletonBar(widthFraction: Float, height: Dp, color: Color) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .background(color, CircleShape),
    )
}

// ─────────────────────────────────────────────────────────────
// Shared bits
// ─────────────────────────────────────────────────────────────

@Composable
private fun UnpairConfirmationDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val colors = PairPurgeTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.dialogSurface,
        shape = RoundedCornerShape(28.dp),
        title = {
            // Left-aligned, so the badge, the title and the body all start on the same
            // line — M3's own icon slot would centre the lot.
            Column {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .background(colors.accentBadge, CircleShape),
                ) {
                    Icon(
                        imageVector = PairPurgeIcons.Trash,
                        contentDescription = null,
                        tint = colors.accentStrong,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = pluralStringResource(R.plurals.unpair_dialog_title, count, count),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.ink,
                )
            }
        },
        text = {
            Text(
                text = stringResource(R.string.unpair_dialog_body),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.action_unpair_count, count),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        dismissButton = {
            TextAction(
                text = stringResource(R.string.action_cancel),
                color = colors.muted,
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        },
    )
}

/** A pill-shaped text button: Unpair, Remove, Clear, Cancel, Refresh. */
@Composable
private fun TextAction(
    text: String,
    color: Color,
    onClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(horizontal = 13.dp, vertical = 7.dp),
    style: TextStyle = MaterialTheme.typography.labelMedium,
) {
    TextButton(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.textButtonColors(contentColor = color),
        contentPadding = contentPadding,
    ) {
        Text(text, style = style, maxLines = 1)
    }
}

// ─────────────────────────────────────────────────────────────
// Platform plumbing
// ─────────────────────────────────────────────────────────────

private fun Context.toast(message: String) =
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

// ─────────────────────────────────────────────────────────────
// Previews
//
// The real screen needs a ViewModel, a live adapter and a granted permission, so the
// states worth looking at are the ones hardest to reach on a device. These assemble
// the same chrome around fixed data — the sample devices are the design's own.
// ─────────────────────────────────────────────────────────────

private val PreviewDevices = listOf(
    PairedDevice("Anker Soundcore 2", "5C:C6:D0:2B:71:04"),
    PairedDevice("Getaround Clio", "A0:14:3D:9F:22:8B"),
    PairedDevice("Hertz Corolla 2022", "C8:3D:D4:11:0A:92"),
    PairedDevice("JBL Flip 5", "04:FE:A1:77:3C:10"),
    PairedDevice("Sensor rig 04", "7C:2E:BD:00:19:44"),
    PairedDevice("VW UP! 4711", "E8:07:BF:6A:D3:21"),
)

private fun previewState(selected: Int = 0) = PairedDevicesUiState.Devices(
    devices = PreviewDevices,
    selectedAddresses = PreviewDevices.take(selected).mapTo(mutableSetOf()) { it.address },
)

@Composable
private fun PreviewScreen(
    dark: Boolean,
    title: String,
    trailing: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    PairPurgeTheme(darkTheme = dark) {
        Scaffold(
            containerColor = PairPurgeTheme.colors.background,
            contentWindowInsets = WindowInsets.navigationBars,
            topBar = { AppHeader(title, trailing) },
            bottomBar = bottomBar,
        ) { padding ->
            content(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }
    }
}

@Preview(name = "1a Paired list", widthDp = 360, heightDp = 720)
@Composable
private fun PairedListPreview() = PreviewScreen(
    dark = false,
    title = stringResource(R.string.app_name),
    trailing = { RefreshAction {} },
    bottomBar = { BottomNav(Destination.Devices) {} },
) { modifier ->
    DeviceList(previewState(), {}, {}, {}, {}, modifier)
}

@Preview(name = "1b Selection active", widthDp = 360, heightDp = 720)
@Composable
private fun SelectionPreview() = PreviewScreen(
    dark = false,
    title = stringResource(R.string.app_name),
    trailing = {
        TextAction(
            text = stringResource(R.string.action_clear_selection),
            color = PairPurgeTheme.colors.muted,
            onClick = {},
        )
    },
    bottomBar = { BulkActionBar(selectedCount = 4, onProtect = {}, onUnpair = {}) },
) { modifier ->
    DeviceList(previewState(selected = 4), {}, {}, {}, {}, modifier)
}

@Preview(name = "2a Selection, dark", widthDp = 360, heightDp = 720)
@Composable
private fun SelectionDarkPreview() = PreviewScreen(
    dark = true,
    title = stringResource(R.string.app_name),
    trailing = {
        TextAction(
            text = stringResource(R.string.action_clear_selection),
            color = PairPurgeTheme.colors.muted,
            onClick = {},
        )
    },
    bottomBar = { BulkActionBar(selectedCount = 4, onProtect = {}, onUnpair = {}) },
) { modifier ->
    DeviceList(previewState(selected = 4), {}, {}, {}, {}, modifier)
}

@Preview(name = "1d Protected devices", widthDp = 360, heightDp = 720)
@Composable
private fun ProtectedListPreview() = PreviewScreen(
    dark = false,
    title = stringResource(R.string.protected_title),
    bottomBar = { BottomNav(Destination.Protected) {} },
) { modifier ->
    ProtectedList(PreviewDevices.take(5), {}, modifier)
}

@Preview(name = "1i Permission needed", widthDp = 360, heightDp = 720)
@Composable
private fun PermissionStatePreview() = PreviewScreen(
    dark = false,
    title = stringResource(R.string.app_name),
) { modifier ->
    MessageState(
        icon = PairPurgeIcons.Lock,
        badge = BadgeTone.Accent,
        headline = stringResource(R.string.permission_headline),
        body = stringResource(R.string.permission_body),
        modifier = modifier,
    ) {
        FilledStateAction(label = stringResource(R.string.action_grant), onClick = {})
    }
}

@Preview(name = "1c Unpair confirmation", widthDp = 360, heightDp = 720)
@Composable
private fun UnpairDialogPreview() = PairPurgeTheme {
    UnpairConfirmationDialog(count = 4, onDismiss = {}, onConfirm = {})
}

@Preview(name = "Loading", widthDp = 360, heightDp = 720)
@Composable
private fun LoadingPreview() = PreviewScreen(
    dark = false,
    title = stringResource(R.string.app_name),
) { modifier ->
    LoadingSkeleton(modifier)
}

private fun Context.hasBluetoothPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, BLUETOOTH_PERMISSION) ==
        PackageManager.PERMISSION_GRANTED

private fun Context.shouldShowBluetoothRationale(): Boolean {
    val activity = findActivity() ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, BLUETOOTH_PERMISSION)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.openBluetoothSettings() {
    startActivity(
        Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun Context.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
