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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pairpurge.app.R
import com.pairpurge.app.bluetooth.PairedDevice
import kotlinx.coroutines.launch

private const val BLUETOOTH_PERMISSION = Manifest.permission.BLUETOOTH_CONNECT

/** The two pages behind the hamburger menu. */
private enum class Destination { Main, Whitelist }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairedDevicesScreen(
    viewModel: PairedDevicesViewModel = viewModel(factory = PairedDevicesViewModel.Factory),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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

    LaunchedEffect(Unit) {
        viewModel.refresh(context.hasBluetoothPermission(), permanentlyDenied)
    }

    var destination by rememberSaveable { mutableStateOf(Destination.Main) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                DrawerItem(
                    label = stringResource(R.string.drawer_paired_devices),
                    selected = destination == Destination.Main,
                    onClick = {
                        destination = Destination.Main
                        scope.launch { drawerState.close() }
                    },
                )
                DrawerItem(
                    label = stringResource(R.string.drawer_whitelist),
                    selected = destination == Destination.Whitelist,
                    onClick = {
                        destination = Destination.Whitelist
                        scope.launch { drawerState.close() }
                    },
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(
                                when (destination) {
                                    Destination.Main -> R.string.app_name
                                    Destination.Whitelist -> R.string.whitelist_title
                                },
                            ),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = stringResource(R.string.action_open_menu),
                            )
                        }
                    },
                    actions = {
                        val devicesState = uiState as? PairedDevicesUiState.Devices
                        val hasSelection = devicesState?.selectedAddresses?.isNotEmpty() == true
                        if (destination == Destination.Main && hasSelection) {
                            IconButton(
                                onClick = {
                                    val failures = viewModel.unpairSelected()
                                    if (failures > 0) {
                                        Toast.makeText(
                                            context,
                                            context.resources.getQuantityString(
                                                R.plurals.unpair_selected_failed,
                                                failures,
                                                failures,
                                            ),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription =
                                        stringResource(R.string.action_unpair_selected),
                                )
                            }
                            IconButton(onClick = viewModel::whitelistSelected) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription =
                                        stringResource(R.string.action_whitelist_selected),
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                viewModel.refresh(context.hasBluetoothPermission(), permanentlyDenied)
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.action_refresh),
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            val contentModifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)

            when (val state = uiState) {
                PairedDevicesUiState.Loading -> Unit

                is PairedDevicesUiState.NeedsPermission -> MessageState(
                    icon = Icons.Default.Info,
                    headline = stringResource(R.string.permission_headline),
                    body = stringResource(
                        if (state.permanentlyDenied) {
                            R.string.permission_body_denied
                        } else {
                            R.string.permission_body
                        },
                    ),
                    buttonLabel = stringResource(
                        if (state.permanentlyDenied) {
                            R.string.action_open_app_settings
                        } else {
                            R.string.action_grant
                        },
                    ),
                    onButtonClick = {
                        if (state.permanentlyDenied) {
                            context.openAppSettings()
                        } else {
                            permissionLauncher.launch(BLUETOOTH_PERMISSION)
                        }
                    },
                    modifier = contentModifier,
                )

                PairedDevicesUiState.BluetoothUnsupported -> MessageState(
                    icon = Icons.Default.Warning,
                    headline = stringResource(R.string.unsupported_headline),
                    body = stringResource(R.string.unsupported_body),
                    buttonLabel = null,
                    onButtonClick = {},
                    modifier = contentModifier,
                )

                PairedDevicesUiState.BluetoothDisabled -> MessageState(
                    icon = Icons.Default.Settings,
                    headline = stringResource(R.string.disabled_headline),
                    body = stringResource(R.string.disabled_body),
                    buttonLabel = stringResource(R.string.action_open_bluetooth_settings),
                    onButtonClick = { context.openBluetoothSettings() },
                    modifier = contentModifier,
                )

                PairedDevicesUiState.Empty -> MessageState(
                    icon = Icons.Default.Info,
                    headline = stringResource(
                        when (destination) {
                            Destination.Main -> R.string.empty_headline
                            Destination.Whitelist -> R.string.whitelist_empty_headline
                        },
                    ),
                    body = stringResource(
                        when (destination) {
                            Destination.Main -> R.string.empty_body
                            Destination.Whitelist -> R.string.whitelist_empty_body
                        },
                    ),
                    buttonLabel = when (destination) {
                        Destination.Main -> stringResource(R.string.action_open_bluetooth_settings)
                        Destination.Whitelist -> null
                    },
                    onButtonClick = { context.openBluetoothSettings() },
                    modifier = contentModifier,
                )

                is PairedDevicesUiState.Devices -> when (destination) {
                    Destination.Main -> if (state.mainDevices.isEmpty()) {
                        MessageState(
                            icon = Icons.Default.Star,
                            headline = stringResource(R.string.all_whitelisted_headline),
                            body = stringResource(R.string.all_whitelisted_body),
                            buttonLabel = null,
                            onButtonClick = {},
                            modifier = contentModifier,
                        )
                    } else {
                        DeviceList(
                            state = state,
                            onToggleDevice = viewModel::toggleSelection,
                            onToggleAll = viewModel::setAllSelected,
                            onUnpairDevice = { device ->
                                if (!viewModel.unpair(device.address)) {
                                    Toast.makeText(
                                        context,
                                        R.string.unpair_failed,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                            modifier = contentModifier,
                        )
                    }

                    Destination.Whitelist -> if (state.whitelistedDevices.isEmpty()) {
                        MessageState(
                            icon = Icons.Default.Star,
                            headline = stringResource(R.string.whitelist_empty_headline),
                            body = stringResource(R.string.whitelist_empty_body),
                            buttonLabel = null,
                            onButtonClick = {},
                            modifier = contentModifier,
                        )
                    } else {
                        WhitelistList(
                            devices = state.whitelistedDevices,
                            onRemoveDevice = { viewModel.removeFromWhitelist(it.address) },
                            modifier = contentModifier,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}

@Composable
private fun DeviceList(
    state: PairedDevicesUiState.Devices,
    onToggleDevice: (String) -> Unit,
    onToggleAll: (Boolean) -> Unit,
    onUnpairDevice: (PairedDevice) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        item {
            SelectAllHeader(state = state, onToggleAll = onToggleAll)
            HorizontalDivider()
        }

        items(state.mainDevices, key = { it.address }) { device ->
            val selected = device.address in state.selectedAddresses
            ListItem(
                headlineContent = { Text(device.displayName) },
                supportingContent = {
                    Text(
                        text = device.address,
                        style = MaterialTheme.typography.bodyMedium,
                        // Monospace keeps the octets aligned down the column, which is
                        // what makes comparing against system settings practical.
                        fontFamily = FontFamily.Monospace,
                    )
                },
                // The checkbox takes no click of its own: the remainder of the row is
                // the selection target, separate from the Unpair button.
                leadingContent = { Checkbox(checked = selected, onCheckedChange = null) },
                trailingContent = {
                    TextButton(onClick = { onUnpairDevice(device) }) {
                        Text(stringResource(R.string.action_unpair))
                    }
                },
                modifier = Modifier.toggleable(
                    value = selected,
                    role = Role.Checkbox,
                    onValueChange = { onToggleDevice(device.address) },
                ),
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun SelectAllHeader(
    state: PairedDevicesUiState.Devices,
    onToggleAll: (Boolean) -> Unit,
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
            .triStateToggleable(
                state = toggleState,
                role = Role.Checkbox,
                // Anything short of "all ticked" means the useful action is select-all;
                // only a full selection collapses back to clearing it.
                onClick = { onToggleAll(!state.allSelected) },
            )
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        TriStateCheckbox(state = toggleState, onClick = null)
        Spacer(Modifier.width(16.dp))
        Text(
            text = if (selectedCount == 0) {
                pluralStringResource(
                    R.plurals.paired_device_count,
                    state.mainDevices.size,
                    state.mainDevices.size,
                )
            } else {
                stringResource(R.string.selected_count, selectedCount, state.mainDevices.size)
            },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WhitelistList(
    devices: List<PairedDevice>,
    onRemoveDevice: (PairedDevice) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(devices, key = { it.address }) { device ->
            ListItem(
                headlineContent = { Text(device.displayName) },
                supportingContent = {
                    Text(
                        text = device.address,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingContent = {
                    TextButton(onClick = { onRemoveDevice(device) }) {
                        Text(stringResource(R.string.action_remove_from_whitelist))
                    }
                },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun MessageState(
    icon: ImageVector,
    headline: String,
    body: String,
    buttonLabel: String?,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = headline,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (buttonLabel != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onButtonClick) { Text(buttonLabel) }
        }
    }
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
