package com.github.countryman.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.countryman.R
import com.github.countryman.broker.BrokerHelper
import com.github.countryman.ui.i18n.AppLanguage
import com.github.countryman.ui.i18n.AppStrings
import com.github.countryman.ui.i18n.stringsFor
import com.github.countryman.manager.CarrierConfigManager
import com.github.countryman.model.PhoneNumberSnapshot
import com.github.countryman.model.SimCardInfo
import com.github.countryman.profiles.CountryPresets
import com.github.countryman.profiles.PresetCarriers
import com.github.countryman.telephony.repository.OverrideDispatch
import com.github.countryman.ui.theme.CountrymanTheme

private enum class MainRoute {
    Main,
    CountryPicker,
    CarrierPicker,
}

private data class CountryGroup(
    val label: String,
    val countries: List<com.github.countryman.profiles.CountryPresets.CountryInfo>
)

private val AppBarIconSize = 22.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onShowAbout: () -> Unit,
    onClose: () -> Unit,
    language: AppLanguage,
    refreshNonce: Int,
    onLanguageChange: (AppLanguage) -> Unit
) {
    val context = LocalContext.current
    val s = stringsFor(language)

    var currentRoute by remember { mutableStateOf(MainRoute.Main) }
    var topMenuExpanded by remember { mutableStateOf(false) }
    var showSimDialog by remember { mutableStateOf(false) }
    var countrySearchQuery by remember { mutableStateOf("") }
    var carrierSearchQuery by remember { mutableStateOf("") }
    var selectedSimCard by remember { mutableStateOf<SimCardInfo?>(null) }
    var selectedCountryCode by remember { mutableStateOf("") }
    var customCountryCode by remember { mutableStateOf("") }
    var isCustomCountryCode by remember { mutableStateOf(false) }
    var selectedCarrier by remember { mutableStateOf<PresetCarriers.CarrierPreset?>(null) }
    var isCarrierOverrideEnabled by remember { mutableStateOf(false) }
    var phoneSnapshot by remember { mutableStateOf<PhoneNumberSnapshot?>(null) }
    var phoneDiagnostics by remember { mutableStateOf<String?>(null) }
    var showPhoneDetails by remember { mutableStateOf(false) }
    var phoneWriteInput by remember { mutableStateOf("") }
    var showPhoneWriteDialog by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }

    BackHandler(enabled = currentRoute != MainRoute.Main) {
        currentRoute = MainRoute.Main
    }

    val simCards = remember(context, refreshTrigger, refreshNonce) { CarrierConfigManager.getSimCards(context) }
    val simNumberSnapshots = remember(simCards, refreshTrigger, refreshNonce, context) {
        simCards.associate { simCard ->
            simCard.subId to CarrierConfigManager.getPhoneNumberSnapshot(context, simCard.subId)
        }
    }
    val overrideCountryCode = selectedSimCard?.currentConfig?.get("国家码").orEmpty().uppercase()
    val overrideCarrierName = selectedSimCard?.currentConfig?.get("运营商名称").orEmpty()
    val defaultCountrySummary = selectedSimCard?.countryCode
        ?.formatCountrySummary(language)
        .orEmpty()
        .ifBlank { s.notSet }
    val defaultCarrierSummary = selectedSimCard?.carrierName
        .orEmpty()
        .ifBlank { s.notSet }
    val defaultCountryInfo = remember(selectedSimCard?.countryCode) {
        selectedSimCard?.countryCode
            ?.takeIf { it.isNotBlank() }
            ?.let { code -> CountryPresets.countries.find { it.code.equals(code, ignoreCase = true) } }
    }
    val selectedCountryInfo = remember(selectedCountryCode) {
        CountryPresets.countries.find { it.code == selectedCountryCode }
    }
    val effectiveCountryInfo = selectedCountryInfo ?: defaultCountryInfo
    val countryDisplayValue = remember(selectedCountryCode, customCountryCode, isCustomCountryCode) {
        when {
            isCustomCountryCode && customCountryCode.isNotBlank() -> customCountryCode
            isCustomCountryCode -> s.custom
            selectedCountryCode.isBlank() -> ""
            else -> CountryPresets.countries.find { it.code == selectedCountryCode }
                ?.let { "${it.localizedName(language)} (${it.code})" }
                ?: selectedCountryCode
        }
    }
    val carrierDisplayValue = remember(selectedCarrier) {
        when {
            selectedCarrier == null -> ""
            else -> selectedCarrier?.displayName?.ifBlank { selectedCarrier?.name }.orEmpty()
        }
    }
    val filteredCountries = remember(countrySearchQuery, language) {
        val query = countrySearchQuery.trim()
        val filtered = if (query.isBlank()) {
            CountryPresets.countries
        } else {
            val normalized = query.uppercase()
            CountryPresets.countries.filter { country ->
                country.code.contains(normalized, ignoreCase = true) ||
                    country.name.contains(query, ignoreCase = true) ||
                    country.englishName.contains(query, ignoreCase = true)
            }
        }
        filtered.sortedWith(
            compareBy<com.github.countryman.profiles.CountryPresets.CountryInfo> {
                if (language == AppLanguage.EN) it.englishName else it.name
            }.thenBy { it.code }
        )
    }
    val groupedCountries = remember(filteredCountries, countrySearchQuery) {
        if (countrySearchQuery.isNotBlank()) {
            emptyList()
        } else {
            filteredCountries
                .groupBy { country ->
                    country.englishName.firstOrNull()
                        ?.uppercaseChar()
                        ?.takeIf { it.isLetter() }
                        ?.toString()
                        ?: "#"
                }
                .toSortedMap()
                .map { (label, countries) ->
                    CountryGroup(label = label, countries = countries)
                }
        }
    }
    val filteredCarrierGroups = remember(carrierSearchQuery) {
        val query = carrierSearchQuery.trim()
        PresetCarriers.presets
            .filter { carrier ->
                if (query.isBlank()) {
                    true
                } else {
                    val region = CountryPresets.countries.find { it.code == carrier.region }
                    val regionNameZh = region?.name.orEmpty()
                    val regionNameEn = region?.englishName.orEmpty()
                    carrier.name.contains(query, ignoreCase = true) ||
                        carrier.displayName.contains(query, ignoreCase = true) ||
                        carrier.region.contains(query, ignoreCase = true) ||
                        regionNameZh.contains(query, ignoreCase = true) ||
                        regionNameEn.contains(query, ignoreCase = true)
                }
            }
            .groupBy { it.region }
            .toList()
            .sortedBy { (region, _) ->
                CountryPresets.countries.find { it.code == region }?.englishName ?: region
            }
            .map { (region, carriers) ->
                region to carriers.sortedBy { it.name.lowercase() }
            }
    }

    LaunchedEffect(simCards, selectedSimCard) {
        when {
            simCards.isEmpty() -> selectedSimCard = null
            selectedSimCard != null -> {
                selectedSimCard = simCards.find { it.slot == selectedSimCard?.slot } ?: simCards.firstOrNull()
            }
            else -> selectedSimCard = simCards.firstOrNull()
        }
    }

    LaunchedEffect(selectedSimCard?.subId) {
        val simCard = selectedSimCard
        phoneSnapshot = if (simCard != null) {
            CarrierConfigManager.getPhoneNumberSnapshot(context, simCard.subId)
        } else {
            null
        }
    }

    LaunchedEffect(selectedSimCard?.subId, refreshTrigger, refreshNonce) {
        if (selectedSimCard == null) {
            selectedCountryCode = ""
            customCountryCode = ""
            isCustomCountryCode = false
            isCarrierOverrideEnabled = false
            selectedCarrier = null
        } else {
            val currentCountryCode = selectedSimCard?.currentConfig?.get("国家码").orEmpty().uppercase()
            val currentCarrierName = selectedSimCard?.currentConfig?.get("运营商名称").orEmpty()
            selectedCountryCode = currentCountryCode
            customCountryCode = if (currentCountryCode.length == 2) currentCountryCode else ""
            isCustomCountryCode = false
            isCarrierOverrideEnabled = currentCarrierName.isNotBlank()
            selectedCarrier = PresetCarriers.presets.find {
                it.displayName.equals(currentCarrierName, ignoreCase = true) ||
                    it.name.equals(currentCarrierName, ignoreCase = true)
            }
        }
    }

    val hasAppliedOverride = selectedSimCard != null && (overrideCountryCode.isNotBlank() || overrideCarrierName.isNotBlank())

    val refreshPhonePanel = {
        val simCard = selectedSimCard
        if (simCard != null) {
            phoneSnapshot = CarrierConfigManager.getPhoneNumberSnapshot(context, simCard.subId)
            phoneDiagnostics = CarrierConfigManager.getPhoneNumberDiagnostics(context)
        } else {
            phoneSnapshot = null
            phoneDiagnostics = null
        }
    }

    val applyCurrentOverride = { countryCode: String?, carrierPreset: PresetCarriers.CarrierPreset?, carrierEnabled: Boolean ->
        val simCard = selectedSimCard
        if (simCard == null || countryCode.isNullOrBlank()) {
            false
        } else {
            try {
                when (CarrierConfigManager.setCarrierConfig(
                    context = context,
                    subId = simCard.subId,
                    countryCode = countryCode,
                    carrierName = carrierPreset?.displayName?.takeIf { carrierEnabled }
                )) {
                    OverrideDispatch.COMPLETED -> {
                        Toast.makeText(context, s.saveDone, Toast.LENGTH_SHORT).show()
                        refreshTrigger += 1
                        true
                    }
                    OverrideDispatch.PENDING_BROKER -> {
                        if (!BrokerHelper.isReady(context)) {
                            if (BrokerHelper.openSetup(context)) {
                                Toast.makeText(context, "Open Countryman Broker once to grant Shizuku access.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Install or reopen Countryman Broker first.", Toast.LENGTH_LONG).show()
                            }
                        }
                        true
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, s.saveFailed(e.message), Toast.LENGTH_SHORT).show()
                false
            }
        }
    }

    when (currentRoute) {
        MainRoute.Main -> {
            val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = {
                    LargeTopAppBar(
                        title = {
                            val collapsedFraction = scrollBehavior.state.collapsedFraction.coerceIn(0f, 1f)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(text = s.appTitle)
                                if (collapsedFraction < 0.45f) {
                                    Text(
                                        text = s.appSubtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onClose) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_arrow_back),
                                    contentDescription = s.close,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        actions = {
                            TopMenu(
                                expanded = topMenuExpanded,
                                onExpandedChange = { topMenuExpanded = it },
                                s = s,
                                language = language,
                                onLanguageChange = {
                                    topMenuExpanded = false
                                    onLanguageChange(it)
                                },
                                onShowAbout = {
                                    topMenuExpanded = false
                                    onShowAbout()
                                }
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            scrolledContainerColor = MaterialTheme.colorScheme.background
                        ),
                        scrollBehavior = scrollBehavior
                    )
                },
                bottomBar = {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ProjectCreditsLine(
                                prefix = "\u00A9 Countryman",
                                prefixUrl = "https://github.com/cloudinstone/countryman",
                                middleLabel = "based on",
                                middleLinkLabel = "nrfr",
                                middleLinkUrl = "https://github.com/Ackites/Nrfr",
                                suffixLabel = "works with",
                                suffixLinkLabel = "Shizuku",
                                suffixLinkUrl = "https://shizuku.rikka.app/",
                                onOpenLink = { url ->
                                    context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                                }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 20.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    item {
                        SettingsGroupCard {
                            PreferenceRow(
                                iconRes = R.drawable.ic_setting_sim,
                                label = s.simCard,
                                value = selectedSimCard?.let { "SIM ${it.slot} · ${it.carrierName}" }.orEmpty(),
                                placeholder = if (simCards.isEmpty()) s.noActiveSubscription else s.chooseSubscription,
                                secondaryText = phoneSnapshot?.imsNumber
                                    ?.takeIf { it.isNotBlank() }
                                    ?: null,
                                onClick = { showSimDialog = true }
                            )
                            GroupDivider()
                            PreferenceRow(
                                iconRes = R.drawable.ic_setting_country,
                                leadingText = effectiveCountryInfo?.flagEmoji,
                                label = s.countryCode,
                                value = countryDisplayValue,
                                placeholder = defaultCountrySummary,
                                onClick = {
                                    countrySearchQuery = ""
                                    currentRoute = MainRoute.CountryPicker
                                }
                            )
                            GroupDivider()
                            PreferenceRow(
                                iconRes = R.drawable.ic_setting_carrier,
                                label = s.carrierDisplayName,
                                value = carrierDisplayValue,
                                placeholder = defaultCarrierSummary,
                                toggleChecked = isCarrierOverrideEnabled,
                                onToggleChange = { enabled ->
                                    isCarrierOverrideEnabled = enabled
                                    if (!enabled) {
                                        applyCurrentOverride(
                                            if (isCustomCountryCode) customCountryCode.takeIf { it.length == 2 } else selectedCountryCode.takeIf { it.isNotBlank() },
                                            selectedCarrier,
                                            false
                                        )
                                    } else if (selectedCarrier != null) {
                                        applyCurrentOverride(
                                            if (isCustomCountryCode) customCountryCode.takeIf { it.length == 2 } else selectedCountryCode.takeIf { it.isNotBlank() },
                                            selectedCarrier,
                                            true
                                        )
                                    }
                                },
                                onClick = {
                                    carrierSearchQuery = ""
                                    currentRoute = MainRoute.CarrierPicker
                                }
                            )
                            GroupDivider()
                            PreferenceRow(
                                iconRes = R.drawable.ic_setting_phone,
                                label = s.displayNumber,
                                value = phoneSnapshot?.displayNumber
                                    ?.takeIf { it.isNotBlank() }
                                    ?: phoneSnapshot?.imsNumber.orEmpty(),
                                placeholder = s.notSet,
                                onClick = {
                                    phoneWriteInput = phoneSnapshot?.displayNumber
                                        ?.takeIf { it.isNotBlank() }
                                        ?: phoneSnapshot?.imsNumber.orEmpty()
                                    showPhoneWriteDialog = true
                                }
                            )
                        }
                    }
                    if (hasAppliedOverride) {
                        item {
                            Spacer(modifier = Modifier.height(26.dp))
                        }
                        item {
                            ResetButton(
                                s = s,
                                selectedSimCard = selectedSimCard,
                                onReset = { simCard ->
                                    try {
                                        val resetDispatch = CarrierConfigManager.resetCarrierConfig(context, simCard.subId)
                                        CarrierConfigManager.restoreDisplayNumberDefault(context, simCard.subId)

                                        when (resetDispatch) {
                                            OverrideDispatch.COMPLETED -> {
                                                Toast.makeText(context, s.resetDone, Toast.LENGTH_SHORT).show()
                                                refreshTrigger += 1
                                            }
                                            OverrideDispatch.PENDING_BROKER -> {
                                                if (!BrokerHelper.isReady(context)) {
                                                    if (BrokerHelper.openSetup(context)) {
                                                        Toast.makeText(context, "Open Countryman Broker once to grant Shizuku access.", Toast.LENGTH_LONG).show()
                                                    } else {
                                                        Toast.makeText(context, "Install or reopen Countryman Broker first.", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                        }
                                        selectedCountryCode = ""
                                        customCountryCode = ""
                                        isCustomCountryCode = false
                                        isCarrierOverrideEnabled = false
                                        selectedCarrier = null
                                        phoneWriteInput = ""
                                    } catch (e: Exception) {
                                        Toast.makeText(context, s.resetFailed(e.message), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }
                }
            }
        }

        MainRoute.CountryPicker -> {
            SelectorPage(
                s = s,
                query = countrySearchQuery,
                onQueryChange = { countrySearchQuery = it },
                placeholder = s.searchCountry,
                onBack = { currentRoute = MainRoute.Main }
            ) {
                if (countrySearchQuery.isBlank()) {
                    items(
                        items = groupedCountries,
                        key = { group -> group.label }
                    ) { group ->
                        Text(
                            text = group.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 8.dp)
                        )
                        SettingsGroupCard {
                            group.countries.forEachIndexed { index, country ->
                                SelectorRow(
                                    title = country.localizedName(language),
                                    subtitle = if (language == AppLanguage.EN) {
                                        "${country.name} · ${country.code}"
                                    } else {
                                        "${country.englishName} · ${country.code}"
                                    },
                                    leadingText = country.flagEmoji,
                                    selected = !isCustomCountryCode && selectedCountryCode == country.code,
                                    onClick = {
                                        selectedCountryCode = country.code
                                        isCustomCountryCode = false
                                        currentRoute = MainRoute.Main
                                        applyCurrentOverride(country.code, selectedCarrier, isCarrierOverrideEnabled)
                                    }
                                )
                                if (index != group.countries.lastIndex) {
                                    GroupDivider()
                                }
                            }
                        }
                    }
                } else {
                    item(key = "country-search-results") {
                    SettingsGroupCard {
                        filteredCountries.forEachIndexed { index, country ->
                            SelectorRow(
                                title = country.localizedName(language),
                                subtitle = if (language == AppLanguage.EN) {
                                    "${country.name} · ${country.code}"
                                } else {
                                    "${country.englishName} · ${country.code}"
                                },
                                leadingText = country.flagEmoji,
                                selected = !isCustomCountryCode && selectedCountryCode == country.code,
                                onClick = {
                                    selectedCountryCode = country.code
                                    isCustomCountryCode = false
                                    currentRoute = MainRoute.Main
                                    applyCurrentOverride(country.code, selectedCarrier, isCarrierOverrideEnabled)
                                }
                            )
                            if (index != filteredCountries.lastIndex) {
                                GroupDivider()
                            }
                        }
                    }
                }
                }
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        MainRoute.CarrierPicker -> {
            SelectorPage(
                s = s,
                query = carrierSearchQuery,
                onQueryChange = { carrierSearchQuery = it },
                placeholder = s.searchCarrier,
                onBack = { currentRoute = MainRoute.Main }
            ) {
                filteredCarrierGroups.forEach { (region, carriers) ->
                    if (carriers.isEmpty()) {
                        return@forEach
                    }
                    item(key = "region-$region") {
                        if (region.isNotBlank()) {
                            Text(
                                text = CountryPresets.countries.find { it.code == region }?.localizedName(language) ?: region,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
                                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 8.dp)
                            )
                        }
                        SettingsGroupCard {
                            carriers.forEachIndexed { index, carrier ->
                                SelectorRow(
                                    title = carrier.name,
                                    subtitle = carrier.displayName.takeIf { it.isNotBlank() },
                                    selected = selectedCarrier?.name == carrier.name && selectedCarrier?.region == carrier.region,
                                    onClick = {
                                        selectedCarrier = carrier
                                        isCarrierOverrideEnabled = true
                                        currentRoute = MainRoute.Main
                                        applyCurrentOverride(
                                            if (isCustomCountryCode) customCountryCode.takeIf { it.length == 2 } else selectedCountryCode.takeIf { it.isNotBlank() },
                                            carrier,
                                            true
                                        )
                                    }
                                )
                                if (index != carriers.lastIndex) {
                                    GroupDivider()
                                }
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    if (showPhoneWriteDialog) {
        AlertDialog(
            onDismissRequest = { showPhoneWriteDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val simCard = selectedSimCard
                        if (simCard != null && phoneWriteInput.isNotBlank()) {
                            phoneDiagnostics = CarrierConfigManager.runPhoneNumberWriteExperiments(
                                context = context,
                                subId = simCard.subId,
                                value = phoneWriteInput
                            )
                            phoneSnapshot = CarrierConfigManager.getPhoneNumberSnapshot(context, simCard.subId)
                            showPhoneDetails = true
                            showPhoneWriteDialog = false
                        }
                    }
                ) {
                    Text(s.execute)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPhoneWriteDialog = false }) {
                    Text(s.cancel)
                }
            },
            title = { Text(s.writeDisplayNumberTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = selectedSimCard?.let { s.writeDialogMessage(it.slot) }
                            ?: s.chooseSubscription,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = phoneWriteInput,
                        onValueChange = { phoneWriteInput = it },
                        label = { Text(s.testNumber) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }

    if (showSimDialog) {
        AlertDialog(
            onDismissRequest = { showSimDialog = false },
            confirmButton = {
                TextButton(onClick = { showSimDialog = false }) {
                    Text(s.closeDialog)
                }
            },
            title = { Text(s.chooseSimTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    simCards.forEach { simCard ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedSimCard = simCard
                                    showSimDialog = false
                                },
                            shape = RoundedCornerShape(20.dp),
                            color = if (selectedSimCard?.subId == simCard.subId) {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        ) {
                            ListItem(
                                colors = ListItemDefaults.colors(
                                    containerColor = if (selectedSimCard?.subId == simCard.subId) {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    }
                                ),
                                headlineContent = {
                                    Text("SIM ${simCard.slot}")
                                },
                                supportingContent = {
                                    val number = simNumberSnapshots[simCard.subId]
                                        ?.imsNumber
                                        ?.takeIf { it.isNotBlank() }
                                    Text(
                                        if (number.isNullOrBlank()) {
                                            simCard.carrierName
                                        } else {
                                            "${simCard.carrierName} · $number"
                                        }
                                    )
                                },
                                trailingContent = {
                                    if (selectedSimCard?.subId == simCard.subId) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_check),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun TopMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    s: AppStrings,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onShowAbout: () -> Unit
) {
    Column {
        IconButton(onClick = { onExpandedChange(true) }) {
            Icon(
                painter = painterResource(id = R.drawable.ic_more_vert),
                contentDescription = s.more,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(AppBarIconSize)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            DropdownMenuItem(
                text = { Text(s.languageChinese) },
                trailingIcon = {
                    if (language == AppLanguage.ZH) {
                        Icon(painterResource(id = R.drawable.ic_check), contentDescription = null)
                    }
                },
                onClick = { onLanguageChange(AppLanguage.ZH) }
            )
            DropdownMenuItem(
                text = { Text(s.languageEnglish) },
                trailingIcon = {
                    if (language == AppLanguage.EN) {
                        Icon(painterResource(id = R.drawable.ic_check), contentDescription = null)
                    }
                },
                onClick = { onLanguageChange(AppLanguage.EN) }
            )
            DropdownMenuItem(
                text = { Text(s.about) },
                onClick = onShowAbout
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorPage(
    s: AppStrings,
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onBack: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SearchTopBar(
                s = s,
                value = query,
                onValueChange = onQueryChange,
                placeholder = placeholder,
                onBack = onBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

@Composable
private fun SectionBlock(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
            modifier = Modifier.padding(start = 8.dp)
        )
        content()
    }
}

@Composable
private fun SettingsGroupCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(content = content)
    }
}

@Composable
private fun GroupDivider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(MaterialTheme.colorScheme.background)
    )
}

private fun com.github.countryman.profiles.CountryPresets.CountryInfo.localizedName(language: AppLanguage): String {
    return if (language == AppLanguage.EN) englishName else name
}

private fun String.formatCountrySummary(language: AppLanguage): String {
    if (length != 2) return this
    val country = CountryPresets.countries.find { it.code.equals(this, ignoreCase = true) }
    return country?.let { "${it.localizedName(language)} (${it.code})" } ?: uppercase()
}

@Composable
private fun PreferenceRow(
    iconRes: Int,
    leadingText: String? = null,
    label: String,
    value: String,
    placeholder: String,
    secondaryText: String? = null,
    toggleChecked: Boolean? = null,
    onToggleChange: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        leadingContent = if (leadingText.isNullOrBlank()) {
            {
                Box(
                    modifier = Modifier.width(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            {
                Box(
                    modifier = Modifier.width(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = leadingText,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        },
        headlineContent = {
            Text(label)
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = value.ifBlank { placeholder },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                secondaryText?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        trailingContent = {
            if (toggleChecked != null && onToggleChange != null) {
                Switch(
                    checked = toggleChecked,
                    onCheckedChange = onToggleChange
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    s: AppStrings,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onBack: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_back),
                    contentDescription = s.back,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        title = {
            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                    Text(
                        text = placeholder,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                    disabledContainerColor = MaterialTheme.colorScheme.background,
                    focusedIndicatorColor = MaterialTheme.colorScheme.background,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.background,
                    disabledIndicatorColor = MaterialTheme.colorScheme.background,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        actions = {
            Box(modifier = Modifier.size(48.dp)) {
                if (value.isNotBlank()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = s.clear,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
private fun SelectorRow(
    title: String,
    subtitle: String? = null,
    leadingText: String? = null,
    selected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
        leadingContent = leadingText?.takeIf { it.isNotBlank() }?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        },
        headlineContent = {
            Text(
                text = title,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        },
        supportingContent = subtitle?.let {
            {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            if (selected) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    )
}

@Composable
private fun ResetButton(
    s: AppStrings,
    selectedSimCard: SimCardInfo?,
    onReset: (SimCardInfo) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        FilledTonalIconButton(
            onClick = { selectedSimCard?.let(onReset) },
            enabled = selectedSimCard != null,
            modifier = Modifier.size(64.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_reset),
                contentDescription = s.reset,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun MainScreenPreview() {
    CountrymanTheme(darkTheme = true) {
        MainScreen(onShowAbout = {}, onClose = {}, language = AppLanguage.ZH, refreshNonce = 0, onLanguageChange = {})
    }
}
