package com.example.ukfloodsdemographics

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ukfloodsdemographics.ui.theme.UKFloodsDemographicsTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.rememberCameraPositionState





class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AuthRoot()
        }
    }
}


@Composable
private fun AuthRoot() {
    var isDarkTheme by remember { mutableStateOf(false) }

    UKFloodsDemographicsTheme(darkTheme = isDarkTheme) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            AuthScreen(
                modifier = Modifier.padding(innerPadding),
                isDarkTheme = isDarkTheme,
                onThemeChange = { isDarkTheme = it }
            )
        }
    }
}


@Composable
private fun AuthScreen(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isLogin by remember { mutableStateOf(true) }
    var isAuthenticated by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf(MainTab.HOME) }
    var language by remember { mutableStateOf(Language.EN) }
    var notificationSettings by remember { mutableStateOf(FloodNotificationSettings()) }
    var inAppNotificationMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (FirebaseAuth.getInstance().currentUser != null) {
            isAuthenticated = true
        }
    }


    val logoutAction = {
        FirebaseAuthRepository.signOut()
        isAuthenticated = false
        isLogin = true
        currentTab = MainTab.HOME
    }


    if (isAuthenticated) {
        when (currentTab) {
            MainTab.HOME -> HomeScreen(
                modifier = modifier,
                language = language,
                isDarkTheme = isDarkTheme,
                onTabSelected = { selected -> currentTab = selected }
            )


            MainTab.ALERTS -> AlertsScreen(
                modifier = modifier,
                language = language,
                isDarkTheme = isDarkTheme,
                onTabSelected = { selected -> currentTab = selected },
                onFloodAdded = { newFlood ->
                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                    val recipientEmail = FirebaseAuth.getInstance().currentUser?.email
                    scope.launch {
                        runCatching {
                            FloodRealtimeRepository.publishFlood(newFlood, uid)
                        }.onFailure { error ->
                            val reason = error.message?.takeIf { it.isNotBlank() } ?: "Unknown error"
                            inAppNotificationMessage = when (language) {
                                Language.EN -> "Realtime publish failed: $reason"
                                Language.PL -> "Publikacja realtime nie powiodła się: $reason"
                            }
                        }

                        if (!recipientEmail.isNullOrBlank()) {
                            runCatching {
                                FloodRealtimeRepository.queueEmailNotification(
                                    to = recipientEmail,
                                    subject = if (language == Language.EN) {
                                        "Flood alert added successfully"
                                    } else {
                                        "Alert powodziowy dodany pomyślnie"
                                    },
                                    body = if (language == Language.EN) {
                                        "You added a flood alert.\nPostcode: ${newFlood.postcode}\nSeverity: ${newFlood.riskLevel}\nDate: ${newFlood.dateRecorded.orEmpty()}"
                                    } else {
                                        "Dodano przez Ciebie alert powodziowy.\nKod pocztowy: ${newFlood.postcode}\nPoziom: ${newFlood.riskLevel}\nData: ${newFlood.dateRecorded.orEmpty()}"
                                    }
                                )
                            }.onSuccess {
                                val message = when (language) {
                                    Language.EN -> "Email confirmation queued for $recipientEmail."
                                    Language.PL -> "Potwierdzenie e-mail dodane do kolejki dla: $recipientEmail."
                                }

                                inAppNotificationMessage = message
                            }.onFailure { error ->
                                val reason = error.message?.takeIf { it.isNotBlank() } ?: "Unknown error"
                                inAppNotificationMessage = when (language) {
                                    Language.EN -> "Confirmation email failed: $reason"
                                    Language.PL -> "Nie udało się wysłać e-maila potwierdzającego: $reason"
                                }
                            }

                        } else {
                            inAppNotificationMessage = when (language) {
                                Language.EN -> "No account email found for confirmation."
                                Language.PL -> "Brak adresu e-mail konta do potwierdzenia."
                            }
                        }

                        val cfg = notificationSettings
                        val newLat = newFlood.latitude
                        val newLon = newFlood.longitude
                        if (
                            cfg.enabled &&
                            cfg.radiusMiles != null &&
                            cfg.latitude != null &&
                            cfg.longitude != null &&
                            newLat != null &&
                            newLon != null

                        ) {
                            val isNear = distanceMiles(
                                cfg.latitude,
                                cfg.longitude,
                                newLat,
                                newLon
                            ) <= cfg.radiusMiles
                            if (isNear) {
                                val nearMessage = when (language) {
                                    Language.EN -> "Flood near your area: ${newFlood.postcode} (${newFlood.riskLevel})."
                                    Language.PL -> "Powódź blisko Twojego obszaru: ${newFlood.postcode} (${newFlood.riskLevel})."
                                }


                                inAppNotificationMessage = nearMessage
                            } else {
                                val notNearMessage = when (language) {
                                    Language.EN -> "Added flood is outside your saved alert radius."
                                    Language.PL -> "Dodana powódź jest poza zapisanym promieniem alertów."
                                }
                                inAppNotificationMessage = notNearMessage
                            }


                        } else {
                            val notConfiguredMessage = when (language) {
                                Language.EN -> "Set your alert postcode and miles in Profile to receive nearby in-app alerts."
                                Language.PL -> "Ustaw kod pocztowy i mile w Profilu, aby otrzymywać pobliskie alerty w aplikacji."
                            }
                            inAppNotificationMessage = notConfiguredMessage
                        }
                    }
                }
            )

            MainTab.SETTINGS -> SettingsScreen(
                modifier = modifier,
                language = language,
                isDarkTheme = isDarkTheme,
                onThemeChange = onThemeChange,
                onLanguageChange = { language = it },
                onTabSelected = { selected -> currentTab = selected },
                notificationSettings = notificationSettings,
                onNotificationSettingsChange = { notificationSettings = it },
                onLogout = logoutAction
            )

            MainTab.PROFILE -> ProfileScreen(
                modifier = modifier,
                language = language,
                isDarkTheme = isDarkTheme,
                onTabSelected = { selected -> currentTab = selected },
                notificationSettings = notificationSettings,
                onNotificationSettingsChange = { notificationSettings = it }
            )

            MainTab.CHAT -> ChatScreen(
                modifier = modifier,
                language = language,
                isDarkTheme = isDarkTheme,
                onTabSelected = { selected -> currentTab = selected }
            )
        }

        if (inAppNotificationMessage != null) {
            AlertDialog(
                onDismissRequest = { inAppNotificationMessage = null },
                title = {
                    Text(
                        when (language) {
                            Language.EN -> "Flood Notification"
                            Language.PL -> "Powiadomienie o Powodzi"
                        }
                    )
                },
                text = { Text(inAppNotificationMessage!!) },
                confirmButton = {
                    TextButton(onClick = { inAppNotificationMessage = null }) {
                        Text("OK")
                    }
                }
            )
        }

    } else {
        if (isLogin) {
            LoginScreen(
                modifier = modifier,
                language = language,
                isDarkTheme = isDarkTheme,
                onSwitchToSignUp = { isLogin = false },
                onLanguageChange = { language = it },
                onLoginSuccess = { isAuthenticated = true }
            )
        } else {
            SignUpScreen(
                modifier = modifier,
                language = language,
                isDarkTheme = isDarkTheme,
                onSwitchToLogin = { isLogin = true },
                onLanguageChange = { language = it },
                onSignUpSuccess = { isAuthenticated = true }
            )
        }
    }
}

// Bottom navigation after signing in.
private enum class MainTab { HOME, ALERTS, SETTINGS, PROFILE, CHAT }

// If this error or any Throwable.cause mentions Firebase it will say `CONFIGURATION_NOT_FOUND
private fun Throwable.authConfigurationMissingInCauseChain(): Boolean {
    var x: Throwable? = this
    while (x != null) {
        if (x.message.orEmpty().contains("CONFIGURATION_NOT_FOUND", ignoreCase = true)) return true
        x = x.cause
    }
    return false
}


 //User faces an auth error text, handles Firebase misconfiguration, If the user provides wrong credentials on the  login, then a message ofCONFIGURATION_NOT_FOUND` will appear

private fun authFailureMessage(t: Throwable, language: Language, forLogin: Boolean = false): String {
    if (t.authConfigurationMissingInCauseChain()) {
        return when (language) {
            Language.EN ->
                "Sign-in is currently unavailable. Please try again later."
            Language.PL ->
                "Logowanie jest obecnie niedostępne. Spróbuj ponownie później."
        }
    }
    if (forLogin && FirebaseAuthRepository.isInvalidLoginCredentials(t)) {
        return when (language) {
            Language.EN -> "Looks like you entered the wrong email or password."
            Language.PL -> "Wygląda na to, że podano nieprawidłowy adres e-mail lub hasło."
        }
    }
    return FirebaseAuthRepository.authErrorMessage(t).ifBlank {
        when (language) {
            Language.EN -> "Something went wrong."
            Language.PL -> "Coś poszło nie tak."
        }
    }
}

// Email/password registration with validation and [FirebaseAuthRepository] create-account.
@Composable
private fun SignUpScreen(
    modifier: Modifier = Modifier,
    language: Language,
    isDarkTheme: Boolean,
    onSwitchToLogin: () -> Unit,
    onLanguageChange: (Language) -> Unit,
    onSignUpSuccess: () -> Unit


) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var authLoading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()


    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                if (isDarkTheme) Color(0xFF102A43) else Color(0xFFE0F2FF)
            )
            .padding(24.dp)



    ) {
        LanguageToggleRow(
            language = language,
            onLanguageChange = onLanguageChange
        )


        Column(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Card(
                modifier = Modifier.widthIn(max = 400.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {

                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        text = when (language) {
                            Language.EN -> "UK Floods Demographics"
                            Language.PL -> "Demografia powodzi w Wielkiej Brytanii"
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = when (language) {
                            Language.EN -> "Create account"
                            Language.PL -> "Utwórz konto"
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; authError = null },
                        label = { Text("Full name") },
                        enabled = !authLoading
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; authError = null },
                        label = { Text("Email") },
                        enabled = !authLoading
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; authError = null },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = !authLoading
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; authError = null },
                        label = { Text("Confirm password") },
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = !authLoading
                    )

                    authError?.let { err ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = err,
                            color = Color(0xFFD32F2F),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            authError = null
                            when {
                                name.isBlank() || email.isBlank() || password.isBlank() -> {
                                    authError = when (language) {
                                        Language.EN -> "Fill in all fields."
                                        Language.PL -> "Wypełnij wszystkie pola."
                                    }
                                }
                                password != confirmPassword -> {
                                    authError = when (language) {
                                        Language.EN -> "Passwords do not match."
                                        Language.PL -> "Hasła nie są takie same."
                                    }
                                }
                                password.length < 6 -> {
                                    authError = when (language) {
                                        Language.EN -> "Password must be at least 6 characters."
                                        Language.PL -> "Hasło musi mieć co najmniej 6 znaków."
                                    }
                                }
                                else -> {
                                    scope.launch {
                                        authLoading = true
                                        try {
                                            FirebaseAuthRepository.createAccount(name, email, password)
                                                .onSuccess { onSignUpSuccess() }
                                                .onFailure { e ->
                                                    authError = authFailureMessage(e, language, forLogin = false)
                                                }
                                        } finally {
                                            authLoading = false
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !authLoading
                    ) {
                        Text(
                            text = when (language) {
                                Language.EN -> "Sign up"
                                Language.PL -> "Zarejestruj się"
                            }
                        )
                    }
                    if (authLoading) {
                        Spacer(modifier = Modifier.height(12.dp))
                        CircularProgressIndicator(modifier = Modifier.height(28.dp).width(28.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onSwitchToLogin, enabled = !authLoading) {
                        Text(
                            text = when (language) {
                                Language.EN -> "Already have an account? Login"
                                Language.PL -> "Masz już konto? Zaloguj się"
                            }
                        )
                    }
                }
            }
        }
    }
}

// Email/password sign-in  [FirebaseAuthRepository].
@Composable
private fun LoginScreen(
    modifier: Modifier = Modifier,
    language: Language,
    isDarkTheme: Boolean,
    onSwitchToSignUp: () -> Unit,
    onLanguageChange: (Language) -> Unit,
    onLoginSuccess: () -> Unit
) {

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var authLoading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                if (isDarkTheme) Color(0xFF102A43) else Color(0xFFE0F2FF)
            )
            .padding(24.dp)
    ) {
        LanguageToggleRow(
            language = language,
            onLanguageChange = onLanguageChange
        )

        Column(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.widthIn(max = 400.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {

                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        text = when (language) {
                            Language.EN -> "UK Floods Demographics"
                            Language.PL -> "Demografia powodzi w Wielkiej Brytanii"
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = when (language) {
                            Language.EN -> "Login"
                            Language.PL -> "Logowanie"
                        }
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; authError = null },
                        label = { Text("Email") },
                        enabled = !authLoading
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; authError = null },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = !authLoading
                    )
                    authError?.let { err ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = err,
                            color = Color(0xFFD32F2F),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            authError = null
                            if (isLoginFormMissingCredentials(email, password)) {
                                authError = loginMissingCredentialsMessage(language)
                                return@Button
                            }
                            scope.launch {
                                authLoading = true
                                try {
                                    FirebaseAuthRepository.signInWithEmail(email, password)
                                        .onSuccess { onLoginSuccess() }
                                        .onFailure { e ->
                                            authError = authFailureMessage(e, language, forLogin = true)
                                        }
                                } finally {
                                    authLoading = false
                                }
                            }
                        },
                        enabled = !authLoading
                    ) {
                        Text(
                            text = when (language) {
                                Language.EN -> "Login"
                                Language.PL -> "Zaloguj się"
                            }
                        )
                    }
                    if (authLoading) {
                        Spacer(modifier = Modifier.height(12.dp))
                        CircularProgressIndicator(modifier = Modifier.height(28.dp).width(28.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onSwitchToSignUp, enabled = !authLoading) {
                        Text(
                            text = when (language) {
                                Language.EN -> "Don't have an account? Sign up"
                                Language.PL -> "Nie masz konta? Zarejestruj się"
                            }
                        )
                    }
                }
            }
        }
    }
}

// Quick EN/PL switch for screens.
@Composable
private fun LanguageToggleRow(
    language: Language,
    onLanguageChange: (Language) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = { onLanguageChange(Language.EN) }) {
            Text(
                text = "EN",
                color = if (language == Language.EN) Color.Black else Color.Gray
            )
        }
        TextButton(onClick = { onLanguageChange(Language.PL) }) {
            Text(
                text = "PL",
                color = if (language == Language.PL) Color.Black else Color.Gray
            )
        }
    }
}


 //Dashboard: welcome copy, static “risk level” sample indicator, and a map of flood points from the CSV file
 //FloodRiskCsvLoader colored with marker clustering.

@Composable
private fun HomeScreen(
    modifier: Modifier = Modifier,
    language: Language,
    isDarkTheme: Boolean,
    onTabSelected: (MainTab) -> Unit
) {
    val context = LocalContext.current
    var mapClusterItems by remember { mutableStateOf<List<FloodMapClusterItem>>(emptyList()) }
    var mapLoading by remember { mutableStateOf(true) }
    var mapError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        mapLoading = true
        mapError = null
        try {
            val rows = withContext(Dispatchers.IO) {
                FloodRiskCsvLoader.loadFromAssets(context)
            }
            mapClusterItems = rows.mapNotNull { row ->
                if (row.latitude != null && row.longitude != null) FloodMapClusterItem(row) else null
            }
        } catch (e: Exception) {
            mapError = e.message ?: "Unknown error"
        } finally {
            mapLoading = false
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(54.0, -2.5), 5.9f)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            BottomNavBar(
                language = language,
                selectedTab = MainTab.HOME,
                onHomeClick = { onTabSelected(MainTab.HOME) },
                onAlertsClick = { onTabSelected(MainTab.ALERTS) },
                onSettingsClick = { onTabSelected(MainTab.SETTINGS) },
                onProfileClick = { onTabSelected(MainTab.PROFILE) },
                onChatClick = { onTabSelected(MainTab.CHAT) }
            )
        }

    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(
                    if (isDarkTheme) Color(0xFF001F3F) else Color(0xFFE0F2FF)
                )
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = when (language) {
                    Language.EN -> "Welcome to UK Floods Demographics"
                    Language.PL -> "Witamy w aplikacji Demografia powodzi w Wielkiej Brytanii"
                }
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = when (language) {
                        Language.EN -> "Current flood risk level"
                        Language.PL -> "Aktualny poziom ryzyka powodzi"
                    },
                    color = if (isDarkTheme) Color.White else Color.Black
                )
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(24.dp)
                        .background(
                            color = Color(0xFFFFC107),
                            shape = CardDefaults.shape
                        )
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = when (language) {
                    Language.EN -> "Flood risk map"
                    Language.PL -> "Mapa ryzyka powodziowego"
                },
                color = if (isDarkTheme) Color.White else Color.Black
            )
            Spacer(modifier = Modifier.height(12.dp))
            when {
                mapLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {

                        CircularProgressIndicator()
                    }
                }
                mapError != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {

                        Text(
                            text = mapError!!,
                            color = Color(0xFFD32F2F),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .heightIn(min = 280.dp)
                            .background(Color(0xFFDDE8F0))
                    ) {

                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState,
                            properties = MapProperties(
                                mapType = MapType.NORMAL,
                                isMyLocationEnabled = false,
                                minZoomPreference = 3.5f,
                                maxZoomPreference = 20f
                            ),
                            uiSettings = MapUiSettings(
                                zoomControlsEnabled = true,
                                compassEnabled = true,
                                mapToolbarEnabled = true,
                                tiltGesturesEnabled = true,
                                rotationGesturesEnabled = true,
                                scrollGesturesEnabled = true,
                                zoomGesturesEnabled = true
                            ),
                            onMapLoaded = {
                                cameraPositionState.move(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(54.0, -2.5),
                                        5.9f
                                    )
                                )
                            }
                        ) {
                            Clustering(
                                items = mapClusterItems,
                                onClusterClick = { false },
                                onClusterItemClick = { false }
                            )
                        }
                    }
                }
            }
        }
    }
}

// Distance filter choice for alerts:
private data class RadiusFilterOption(
    val miles: Double?,
    val labelEn: String,
    val labelPl: String
)


private data class CityFilterOption(
    val name: String,
    val latitude: Double?,
    val longitude: Double?
)

//Stable id for user severity ratings in session


private fun FloodRiskEntry.alertRatingKey(): String =
    listOf(postcode, latitude?.toString(), longitude?.toString(), riskLevel).joinToString("|")

//Color for user severity 1–5 (and unrated) on alerts


private fun severityIconColor(level: Int?, isDarkTheme: Boolean): Color {
    if (level == null) {
        return if (isDarkTheme) Color.White.copy(alpha = 0.45f) else Color(0xFF9E9E9E)
    }
    return when (level) {
        1 -> Color(0xFF43A047)
        2 -> Color(0xFF7CB342)
        3 -> Color(0xFFFFC107)
        4 -> Color(0xFFFF6F00)
        5 -> Color(0xFFC62828)
        else -> if (isDarkTheme) Color.White.copy(alpha = 0.45f) else Color(0xFF9E9E9E)
    }
}

//How rows in the alerts list are ordered once radius filtering is utlised.

private enum class AlertSortMode(val labelEn: String, val labelPl: String) {
    Distance("Nearest first (by distance)", "Według odległości (najbliższe)"),
    Postcode("Postcode A–Z", "Kod pocztowy A–Z")
}

// Preset radius (miles) for within X miles filtering when user location is available.
private val radiusFilterOptions = listOf(
    RadiusFilterOption(null, "Include all", "Uwzględnij wszystko"),
    RadiusFilterOption(5.0, "Within 5 miles", "W promieniu 5 mil"),
    RadiusFilterOption(10.0, "Within 10 miles", "W promieniu 10 mil"),
    RadiusFilterOption(25.0, "Within 25 miles", "W promieniu 25 mil"),
    RadiusFilterOption(40.0, "Within 40 miles", "W promieniu 40 mil"),
    RadiusFilterOption(100.0, "Within 100 miles", "W promieniu 100 mil"),
    RadiusFilterOption(200.0, "Within 200 miles", "W promieniu 200 mil"),
    RadiusFilterOption(300.0, "Within 300 miles", "W promieniu 300 mil")
)

private val cityFilterOptions = listOf(
    CityFilterOption("Bradford", 53.7938, -1.7524),
    CityFilterOption("London", 51.5074, -0.1278),
    CityFilterOption("Essex", 51.7356, 0.4685),
    CityFilterOption("Leeds", 53.8008, -1.5491),
    CityFilterOption("Wakefield", 53.6833, -1.4977)
)

private data class NewFloodInput(
    val postcode: String,
    val dateRecorded: String,
    val severity: String
)

private data class FloodNotificationSettings(
    val enabled: Boolean = false,
    val postcode: String = "",
    val radiusMiles: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)


@Composable
private fun AlertsScreen(
    modifier: Modifier = Modifier,
    language: Language,
    isDarkTheme: Boolean,
    onTabSelected: (MainTab) -> Unit,
    onFloodAdded: (FloodRiskEntry) -> Unit = {}
) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var floodRows by remember { mutableStateOf<List<FloodRiskEntry>>(emptyList()) }
    var loadingCsv by remember { mutableStateOf(true) }
    var csvError by remember { mutableStateOf<String?>(null) }
    var addFloodError by remember { mutableStateOf<String?>(null) }


    var selectedRadiusIndex by remember { mutableStateOf(0) }
    var selectedCityIndex by remember { mutableStateOf(0) }
    var userLat by remember { mutableStateOf<Double?>(null) }
    var userLon by remember { mutableStateOf<Double?>(null) }
    var fetchingLocation by remember { mutableStateOf(false) }
    var locationRefreshEpoch by remember { mutableStateOf(0) }


    var radiusMenuExpanded by remember { mutableStateOf(false) }
    var cityMenuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var alertSortMode by remember { mutableStateOf(AlertSortMode.Distance) }
    var showAddFloodDialog by remember { mutableStateOf(false) }


    var alertSeverityRatings by remember { mutableStateOf(mapOf<String, Int>()) }


    val hasLocationPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) {
            locationRefreshEpoch++
        }
    }


    LaunchedEffect(Unit) {
        loadingCsv = true
        csvError = null
        try {
            floodRows = withContext(Dispatchers.IO) {
                FloodRiskCsvLoader.loadFromAssets(context)
            }
        } catch (e: Exception) {
            csvError = e.message ?: "Unknown error"
        } finally {
            loadingCsv = false
        }
    }


    val selectedFilter = radiusFilterOptions[selectedRadiusIndex]
    val selectedCity = cityFilterOptions[selectedCityIndex]
    val activeRadiusMiles = selectedFilter.miles
    val needsLocationForFilter = activeRadiusMiles != null
    val usingCityLocation = selectedCity.latitude != null && selectedCity.longitude != null
    val effectiveLat = selectedCity.latitude ?: userLat
    val effectiveLon = selectedCity.longitude ?: userLon

    LaunchedEffect(hasLocationPermission, loadingCsv, locationRefreshEpoch, activeRadiusMiles) {
        if (!hasLocationPermission || loadingCsv || activeRadiusMiles == null || usingCityLocation) return@LaunchedEffect
        while (isActive) {
            fetchingLocation = true
            try {

                val loc = UserLocationProvider.fetchBestLocation(context)
                userLat = loc?.latitude
                userLon = loc?.longitude
            } finally {
                fetchingLocation = false
            }
            delay(30_000L)
        }
    }

    val displayedAlerts = remember(
        floodRows,
        selectedRadiusIndex,
        selectedCityIndex,
        userLat,
        userLon,
        alertSortMode
    ) {

        val optMiles = radiusFilterOptions[selectedRadiusIndex].miles
        val rawPairs: List<Pair<FloodRiskEntry, Double?>> =
            if (optMiles == null) {
                floodRows.map { it to null as Double? }
            } else if (effectiveLat == null || effectiveLon == null) {
                emptyList()
            } else {
                val uLat = effectiveLat
                val uLon = effectiveLon
                val maxMiles = optMiles
                floodRows.mapNotNull { row ->
                    val lat = row.latitude ?: return@mapNotNull null
                    val lon = row.longitude ?: return@mapNotNull null
                    val d = distanceMiles(uLat, uLon, lat, lon)
                    if (d <= maxMiles) row to d else null
                }
            }

        when (alertSortMode) {
            AlertSortMode.Postcode -> rawPairs.sortedWith(
                compareBy { it.first.postcode.trim().uppercase(Locale.UK) }
            )
            AlertSortMode.Distance -> {
                val hasAnyDistance = rawPairs.any { it.second != null }
                if (hasAnyDistance) {
                    rawPairs.sortedWith(
                        compareBy<Pair<FloodRiskEntry, Double?>>(
                            { it.second == null },
                            { it.second ?: Double.POSITIVE_INFINITY }
                        )
                    )
                } else {
                    rawPairs
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            BottomNavBar(
                language = language,
                selectedTab = MainTab.ALERTS,
                onHomeClick = { onTabSelected(MainTab.HOME) },
                onAlertsClick = { onTabSelected(MainTab.ALERTS) },
                onSettingsClick = { onTabSelected(MainTab.SETTINGS) },
                onProfileClick = { onTabSelected(MainTab.PROFILE) },
                onChatClick = { onTabSelected(MainTab.CHAT) }
            )
        }

    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(
                    if (isDarkTheme) Color(0xFF001F3F) else Color(0xFFE0F2FF)
                )
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = when (language) {
                        Language.EN -> "Alerts"
                        Language.PL -> "Alerty"
                    },

                    color = if (isDarkTheme) Color.White else Color.Black
                )
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = "Filter alerts",
                    tint = if (isDarkTheme) Color.White else Color.Black
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { showAddFloodDialog = true }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when (language) {
                        Language.EN -> "Add new flood"
                        Language.PL -> "Dodaj nową powódź"
                    }
                )
            }

            addFloodError?.let { saveError ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = saveError,
                    color = Color(0xFFD32F2F),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = when (language) {
                    Language.EN -> "Distance filter"
                    Language.PL -> "Filtr odległości"
                },

                color = if (isDarkTheme) Color.White else Color.Black,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { radiusMenuExpanded = true }
                ) {

                    Text(
                        modifier = Modifier.weight(1f),
                        text = when (language) {
                            Language.EN -> selectedFilter.labelEn
                            Language.PL -> selectedFilter.labelPl
                        }
                    )
                }

                DropdownMenu(
                    expanded = radiusMenuExpanded,
                    onDismissRequest = { radiusMenuExpanded = false }
                ) {

                    radiusFilterOptions.forEachIndexed { index, option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (language) {
                                        Language.EN -> option.labelEn
                                        Language.PL -> option.labelPl
                                    }
                                )
                            },

                            onClick = {
                                selectedRadiusIndex = index
                                radiusMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { cityMenuExpanded = true }
                ) {

                    Text(
                        modifier = Modifier.weight(1f),
                        text = when (language) {
                            Language.EN -> "Cities: ${selectedCity.name}"
                            Language.PL -> "Miasta: ${selectedCity.name}"
                        }
                    )
                }

                DropdownMenu(
                    expanded = cityMenuExpanded,
                    onDismissRequest = { cityMenuExpanded = false }
                ) {
                    cityFilterOptions.forEachIndexed { index, option ->
                        DropdownMenuItem(
                            text = { Text(option.name) },
                            onClick = {
                                selectedCityIndex = index
                                cityMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = when (language) {
                    Language.EN -> "Sort order"
                    Language.PL -> "Kolejność sortowania"
                },
                color = if (isDarkTheme) Color.White else Color.Black,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { sortMenuExpanded = true }
                ) {

                    Text(
                        modifier = Modifier.weight(1f),
                        text = when (language) {
                            Language.EN -> alertSortMode.labelEn
                            Language.PL -> alertSortMode.labelPl
                        }
                    )
                }

                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    AlertSortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (language) {
                                        Language.EN -> mode.labelEn
                                        Language.PL -> mode.labelPl
                                    }
                                )
                            },

                            onClick = {
                                alertSortMode = mode
                                sortMenuExpanded = false
                            }
                        )
                    }
                }
            }

            if (needsLocationForFilter) {
                Spacer(modifier = Modifier.height(8.dp))
                when {
                    usingCityLocation -> {
                        Text(
                            text = when (language) {
                                Language.EN -> "Filtering around ${selectedCity.name}"
                                Language.PL -> "Filtrowanie wokół: ${selectedCity.name}"
                            },
                            color = if (isDarkTheme) Color.White else Color.Black,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    !hasLocationPermission -> {
                        TextButton(
                            onClick = {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        ) {

                            Text(
                                when (language) {
                                    Language.EN -> "Allow location"
                                    Language.PL -> "Zezwól na lokalizację"
                                }
                            )
                        }
                    }

                    fetchingLocation -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp))
                            Text(
                                text = when (language) {
                                    Language.EN -> "Getting your location…"
                                    Language.PL -> "Pobieranie lokalizacji…"
                                },
                                color = if (isDarkTheme) Color.White else Color.Black,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    userLat == null || userLon == null -> {
                        TextButton(onClick = { locationRefreshEpoch++ }) {
                            Text(
                                when (language) {
                                    Language.EN -> "Retry"
                                    Language.PL -> "Spróbuj ponownie"
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            when {
                loadingCsv -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                csvError != null -> {
                    Text(
                        text = csvError!!,
                        color = Color(0xFFD32F2F)
                    )
                }

                else -> {
                    if (displayedAlerts.isEmpty()) {
                        Text(
                            text = when {
                                needsLocationForFilter && (effectiveLat == null || effectiveLon == null) -> when (language) {
                                    Language.EN -> "Choose a city or enable live location."
                                    Language.PL -> "Wybierz miasto lub włącz lokalizację na żywo."
                                }
                                else -> when (language) {
                                    Language.EN -> "No floods found within the selected radius."
                                    Language.PL -> "Brak powodzi w wybranym promieniu."
                                }
                            },

                            color = if (isDarkTheme) Color.White else Color.Black
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(
                                displayedAlerts,
                                key = { index, pair -> "$index-${pair.first.postcode}-${pair.first.riskLevel}-${pair.second}" }
                            ) { _, pair ->
                                val entry = pair.first
                                val ratingKey = entry.alertRatingKey()
                                FloodRiskAlertCard(
                                    row = entry,
                                    language = language,
                                    isDarkTheme = isDarkTheme,
                                    distanceMiles = pair.second,
                                    userSeverity = alertSeverityRatings[ratingKey],
                                    onSeverityChosen = { level ->
                                        alertSeverityRatings = alertSeverityRatings + (ratingKey to level)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddFloodDialog) {
        AddFloodDialog(
            language = language,
            onDismiss = { showAddFloodDialog = false },
            onAddFlood = { input ->
                scope.launch {
                    try {
                        val coordinates = withContext(Dispatchers.IO) {
                            StreetGeocoder.coordinatesFromAddress(
                                context = context,
                                postcode = input.postcode,
                                addressLine = ""
                            )
                        }
                        if (coordinates == null) {
                            addFloodError = when (language) {
                                Language.EN -> "Could not locate this address/postcode."
                                Language.PL -> "Nie udało się znaleźć tej lokalizacji."
                            }
                            return@launch
                        }
                        val newEntry = FloodRiskEntry(
                            postcode = input.postcode.uppercase(Locale.UK),
                            riskLevel = input.severity,
                            detail = null,
                            dateRecorded = input.dateRecorded,
                            latitude = coordinates.first,
                            longitude = coordinates.second
                        )
                        withContext(Dispatchers.IO) {
                            FloodRiskCsvLoader.appendUserFlood(context, newEntry)
                        }
                        addFloodError = null
                        floodRows = listOf(newEntry) + floodRows
                        onFloodAdded(newEntry)
                        showAddFloodDialog = false
                    } catch (_: Exception) {
                        addFloodError = when (language) {
                            Language.EN -> "Could not save flood to CSV."
                            Language.PL -> "Nie udało się zapisać powodzi do CSV."
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun AddFloodDialog(
    language: Language,
    onDismiss: () -> Unit,
    onAddFlood: (NewFloodInput) -> Unit
) {

    var postcode by remember { mutableStateOf("") }
    var severity by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    val calendar = remember { Calendar.getInstance() }



    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (language) {
                    Language.EN -> "Add flood alert"
                    Language.PL -> "Dodaj alert powodziowy"
                }
            )
        },



        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = postcode,
                    onValueChange = { postcode = it },
                    label = {
                        Text(
                            when (language) {
                                Language.EN -> "Postcode *"
                                Language.PL -> "Kod pocztowy *"
                            }
                        )
                    },



                    singleLine = true
                )
                OutlinedTextField(
                    value = severity,
                    onValueChange = { severity = it },
                    label = {
                        Text(
                            when (language) {
                                Language.EN -> "Severity * (Low/Medium/High)"
                                Language.PL -> "Poziom zagrożenia * (Niski/Średni/Wysoki)"
                            }
                        )
                    },


                    singleLine = true
                )
                validationError?.let { err ->
                    Text(
                        text = err,
                        color = Color(0xFFD32F2F),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },


        confirmButton = {
            TextButton(
                onClick = {
                    val trimmedPostcode = postcode.trim()
                    val trimmedSeverity = severity.trim()
                    val currentDate = String.format(
                        Locale.UK,
                        "%04d-%02d-%02d",
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH) + 1,
                        calendar.get(Calendar.DAY_OF_MONTH)
                    )

                    when {

                        isAddFloodFormMissingRequiredFields(trimmedPostcode, trimmedSeverity) -> {
                            validationError = addFloodMissingFieldsMessage(language)
                        }

                        else -> {
                            validationError = null
                            onAddFlood(
                                NewFloodInput(
                                    postcode = trimmedPostcode,
                                    dateRecorded = currentDate,
                                    severity = trimmedSeverity
                                )
                            )
                        }
                    }
                }
            ) {

                Text(
                    when (language) {
                        Language.EN -> "Add"
                        Language.PL -> "Dodaj"
                    }
                )
            }
        },

        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    when (language) {
                        Language.EN -> "Cancel"
                        Language.PL -> "Anuluj"
                    }
                )
            }
        }
    )
}


@Composable
private fun FloodRiskAlertCard(
    row: FloodRiskEntry,
    language: Language,
    isDarkTheme: Boolean,
    distanceMiles: Double? = null,
    userSeverity: Int? = null,
    onSeverityChosen: (Int) -> Unit = {}
) {



    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var showSeverityDialog by remember(row.alertRatingKey()) { mutableStateOf(false) }
    var streetLine by remember(row.latitude, row.longitude, row.postcode) {
        mutableStateOf<String?>(null)
    }



    var streetResolved by remember(row.latitude, row.longitude, row.postcode) {
        mutableStateOf(false)
    }


    LaunchedEffect(row.latitude, row.longitude, row.postcode) {
        if (row.latitude == null || row.longitude == null) {
            streetLine = null
            streetResolved = true
            return@LaunchedEffect
        }



        streetResolved = false
        streetLine = StreetGeocoder.streetFromCoordinates(context, row.latitude, row.longitude)
        streetResolved = true
    }




    val mapsUrl = remember(row.latitude, row.longitude, row.postcode) {
        googleMapsSearchUrl(row.latitude, row.longitude, row.postcode)
    }




    val riskColor = when (row.riskLevel.lowercase()) {
        "high" -> Color(0xFFD32F2F)
        "medium" -> Color(0xFFFF9800)
        "low" -> Color(0xFFFFC107)
        else -> if (isDarkTheme) Color(0xFFB0BEC5) else Color(0xFF546E7A)
    }

    val muted = if (isDarkTheme) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.8f)
    val mutedSmall = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.6f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showSeverityDialog = true },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {


            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {


                Text(
                    text = row.postcode,
                    color = if (isDarkTheme) Color.White else Color.Black,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = when (language) {
                        Language.EN -> "Your severity rating for this alert"
                        Language.PL -> "Twoja ocena powagi alertu"
                    },
                    tint = severityIconColor(userSeverity, isDarkTheme)
                )
            }

            distanceMiles?.let { miles ->
                Text(
                    text = when (language) {
                        Language.EN -> "About %.1f mi from you".format(miles)
                        Language.PL -> "Około %.1f mil od Ciebie".format(miles)
                    },
                    color = mutedSmall,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            when {
                row.latitude != null && row.longitude != null && !streetResolved -> {
                    Text(
                        text = when (language) {
                            Language.EN -> "Looking up street…"
                            Language.PL -> "Wyszukiwanie ulicy…"
                        },
                        color = mutedSmall,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                else -> {
                    Text(
                        text = when (language) {
                            Language.EN -> "Street"
                            Language.PL -> "Ulica"
                        } + ": " + when {
                            !streetLine.isNullOrBlank() -> streetLine!!
                            row.latitude != null && row.longitude != null -> when (language) {
                                Language.EN -> "Not available"
                                Language.PL -> "Niedostępna"
                            }
                            else -> when (language) {
                                Language.EN -> "No coordinates in data"
                                Language.PL -> "Brak współrzędnych"
                            }
                        },
                        color = muted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Text(
                text = row.riskLevel,
                color = riskColor,
                style = MaterialTheme.typography.bodyLarge
            )
            row.detail?.let { detail ->
                Text(
                    text = detail,
                    color = muted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            row.dateRecorded?.let { date ->
                Text(
                    text = date,
                    color = mutedSmall,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            TextButton(onClick = { uriHandler.openUri(mapsUrl) }) {
                Text(
                    text = when (language) {
                        Language.EN -> "Open in Google Maps"
                        Language.PL -> "Otwórz w Mapach Google"
                    }
                )
            }
        }
    }

    if (showSeverityDialog) {
        AlertDialog(
            onDismissRequest = { showSeverityDialog = false },
            title = {
                Text(
                    when (language) {
                        Language.EN -> "How severe is this alert for you?"
                        Language.PL -> "Jak poważny jest dla Ciebie ten alert?"
                    }
                )
            },

            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = when (language) {
                            Language.EN -> "1 = harmless · 5 = very bad"
                            Language.PL -> "1 = nieszkodliwy · 5 = bardzo poważny"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDarkTheme) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.85f)
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (n in 1..5) {
                            val sevColor = severityIconColor(n, isDarkTheme)
                            Button(
                                onClick = {
                                    onSeverityChosen(n)
                                    showSeverityDialog = false
                                },

                                colors = ButtonDefaults.buttonColors(
                                    containerColor = sevColor,
                                    contentColor = when (n) {
                                        1, 2, 5 -> Color.White
                                        else -> Color.Black
                                    }
                                )
                            ) {

                                Text(
                                    text = n.toString(),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }
            },

            confirmButton = {
                TextButton(onClick = { showSeverityDialog = false }) {
                    Text(
                        when (language) {
                            Language.EN -> "Close"
                            Language.PL -> "Zamknij"
                        }
                    )
                }
            }
        )
    }
}

//Location permission (including link to system app settings when permanently denied), dark mode,
// EN/PL language, notification and vibration switches  and sign-out.

@Composable
private fun SettingsScreen(
    modifier: Modifier = Modifier,
    language: Language,
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onLanguageChange: (Language) -> Unit,
    onTabSelected: (MainTab) -> Unit,
    notificationSettings: FloodNotificationSettings,
    onNotificationSettingsChange: (FloodNotificationSettings) -> Unit,
    onLogout: () -> Unit
) {

    Scaffold(
        bottomBar = {
            BottomNavBar(
                language = language,
                selectedTab = MainTab.SETTINGS,
                onHomeClick = { onTabSelected(MainTab.HOME) },
                onAlertsClick = { onTabSelected(MainTab.ALERTS) },
                onSettingsClick = { onTabSelected(MainTab.SETTINGS) },
                onProfileClick = { onTabSelected(MainTab.PROFILE) },
                onChatClick = { onTabSelected(MainTab.CHAT) }
            )
        }



    ) { innerPadding ->
        var notificationsEnabled by remember(notificationSettings.enabled) { mutableStateOf(notificationSettings.enabled) }
        var notificationSettingsMessage by remember { mutableStateOf<String?>(null) }
        var vibrationEnabled by remember { mutableStateOf(true) }

        val context = LocalContext.current
        val activity = context as? Activity
        var fineLocationGranted by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            )
        }


        var coarseLocationGranted by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            )
        }

        var hasRequestedLocation by remember { mutableStateOf(false) }

        val locationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            hasRequestedLocation = true
            fineLocationGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
            coarseLocationGranted = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        }

        Column(
            modifier = modifier
                .fillMaxSize()
                .background(
                    if (isDarkTheme) Color(0xFF001F3F) else Color(0xFFE0F2FF)
                )
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {

            Text(
                text = when (language) {
                    Language.EN -> "Settings"
                    Language.PL -> "Ustawienia"
                },
                color = if (isDarkTheme) Color.White else Color.Black
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = when (language) {
                    Language.EN -> "Location"
                    Language.PL -> "Lokalizacja"
                },
                color = if (isDarkTheme) Color.White else Color.Black,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when (language) {
                    Language.EN ->
                        "Allow access so the app can use your current position for flood-related features."
                    Language.PL ->
                        "Zezwól na dostęp, aby aplikacja mogła używać Twojej pozycji w funkcjach związanych z powodzią."
                },

                color = if (isDarkTheme) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when {
                    fineLocationGranted -> when (language) {
                        Language.EN -> "Precise location allowed"
                        Language.PL -> "Dokładna lokalizacja dozwolona"
                    }
                    coarseLocationGranted -> when (language) {
                        Language.EN -> "Approximate location allowed"
                        Language.PL -> "Przybliżona lokalizacja dozwolona"
                    }
                    else -> when (language) {
                        Language.EN -> "Location not allowed"
                        Language.PL -> "Lokalizacja niedozwolona"
                    }
                },

                color = if (isDarkTheme) Color.White else Color.Black,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (!fineLocationGranted && !coarseLocationGranted) {
                Button(
                    onClick = {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                ) {

                    Text(
                        text = when (language) {
                            Language.EN -> "Allow location access"
                            Language.PL -> "Zezwól na dostęp do lokalizacji"
                        }
                    )
                }
            }
            val showOpenAppSettings = activity != null &&
                hasRequestedLocation &&
                !fineLocationGranted &&
                !coarseLocationGranted &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            if (showOpenAppSettings) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }

                ) {
                    Text(
                        text = when (language) {
                            Language.EN -> "Open app settings"
                            Language.PL -> "Otwórz ustawienia aplikacji"
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                Text(
                    text = when (language) {
                        Language.EN -> "Dark mode"
                        Language.PL -> "Tryb ciemny"
                    },
                    color = if (isDarkTheme) Color.White else Color.Black
                )
                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = { onThemeChange(it) }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when (language) {
                        Language.EN -> "Email confirmations"
                        Language.PL -> "Potwierdzenia e-mail"
                    },
                    color = if (isDarkTheme) Color.White else Color.Black
                )
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = { enabled ->
                        notificationsEnabled = enabled
                        onNotificationSettingsChange(
                            notificationSettings.copy(
                                enabled = enabled,
                                latitude = if (enabled) notificationSettings.latitude else null,
                                longitude = if (enabled) notificationSettings.longitude else null
                            )
                        )
                        notificationSettingsMessage = if (enabled) {
                            when (language) {
                                Language.EN -> "Notifications enabled."
                                Language.PL -> "Powiadomienia włączone."
                            }
                        } else {
                            when (language) {
                                Language.EN -> "Notifications disabled."
                                Language.PL -> "Powiadomienia wyłączone."
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            notificationSettingsMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = msg,
                    color = if (isDarkTheme) Color.White else Color.Black,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when (language) {
                        Language.EN -> "Vibration"
                        Language.PL -> "Wibracje"
                    },
                    color = if (isDarkTheme) Color.White else Color.Black
                )
                Switch(
                    checked = vibrationEnabled,
                    onCheckedChange = { vibrationEnabled = it }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when (language) {
                        Language.EN -> "Language"
                        Language.PL -> "Język"
                    },
                    color = if (isDarkTheme) Color.White else Color.Black
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { onLanguageChange(Language.EN) }) {
                        Text(
                            text = "EN",
                            color = if (language == Language.EN) {
                                if (isDarkTheme) Color.White else Color.Black
                            } else {
                                Color.Gray
                            }
                        )
                    }
                    TextButton(onClick = { onLanguageChange(Language.PL) }) {
                        Text(
                            text = "PL",
                            color = if (language == Language.PL) {
                                if (isDarkTheme) Color.White else Color.Black
                            } else {
                                Color.Gray
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onLogout) {
                Text(
                    text = when (language) {
                        Language.EN -> "Log out"
                        Language.PL -> "Wyloguj się"
                    },
                    color = if (isDarkTheme) Color.White else Color.Black
                )
            }
        }
    }
}


@Composable
private fun ProfileScreen(
    modifier: Modifier = Modifier,
    language: Language,
    isDarkTheme: Boolean,
    onTabSelected: (MainTab) -> Unit,
    notificationSettings: FloodNotificationSettings,
    onNotificationSettingsChange: (FloodNotificationSettings) -> Unit
) {

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentUser = FirebaseAuth.getInstance().currentUser
    val emailText = currentUser?.email ?: when (language) {
        Language.EN -> "Not available"
        Language.PL -> "Niedostępne"
    }


    val uidText = currentUser?.uid ?: when (language) {
        Language.EN -> "Not available"
        Language.PL -> "Niedostępne"
    }


    var userFloodHistory by remember { mutableStateOf<List<FloodRiskEntry>>(emptyList()) }
    var userFloodHistoryError by remember { mutableStateOf<String?>(null) }
    var alertPostcode by remember(notificationSettings.postcode) { mutableStateOf(notificationSettings.postcode) }
    var alertMilesText by remember(notificationSettings.radiusMiles) {
        mutableStateOf(notificationSettings.radiusMiles?.toString().orEmpty())
    }


    var alertAreaMessage by remember { mutableStateOf<String?>(null) }
    var savingAlertArea by remember { mutableStateOf(false) }
    var floodsInAreaCount by remember { mutableStateOf<Int?>(null) }
    var countingFloodsInArea by remember { mutableStateOf(false) }
    var floodsInAreaError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            userFloodHistory = withContext(Dispatchers.IO) {
                FloodRiskCsvLoader.loadUserAddedFloods(context)
            }
            userFloodHistoryError = null
        } catch (e: Exception) {
            userFloodHistoryError = e.message?.takeIf { it.isNotBlank() } ?: "Unknown error"
        }
    }

    LaunchedEffect(
        notificationSettings.latitude,
        notificationSettings.longitude,
        notificationSettings.radiusMiles
    ) {


        val lat = notificationSettings.latitude
        val lon = notificationSettings.longitude
        val radius = notificationSettings.radiusMiles
        if (lat == null || lon == null || radius == null || radius <= 0.0) {
            floodsInAreaCount = null
            floodsInAreaError = null
            return@LaunchedEffect
        }


        countingFloodsInArea = true
        floodsInAreaError = null
        try {
            val count = withContext(Dispatchers.IO) {
                FloodRiskCsvLoader.loadFromAssets(context).count { row ->
                    val rowLat = row.latitude ?: return@count false
                    val rowLon = row.longitude ?: return@count false
                    distanceMiles(lat, lon, rowLat, rowLon) <= radius
                }
            }
            floodsInAreaCount = count
        } catch (e: Exception) {
            floodsInAreaError = e.message ?: "Unknown error"
            floodsInAreaCount = null
        } finally {
            countingFloodsInArea = false
        }
    }

    Scaffold(
        bottomBar = {
            BottomNavBar(
                language = language,
                selectedTab = MainTab.PROFILE,
                onHomeClick = { onTabSelected(MainTab.HOME) },
                onAlertsClick = { onTabSelected(MainTab.ALERTS) },
                onSettingsClick = { onTabSelected(MainTab.SETTINGS) },
                onProfileClick = { onTabSelected(MainTab.PROFILE) },
                onChatClick = { onTabSelected(MainTab.CHAT) }
            )
        }


    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(
                    if (isDarkTheme) Color(0xFF001F3F) else Color(0xFFE0F2FF)
                )
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = when (language) {
                    Language.EN -> "Profile"
                    Language.PL -> "Profil"
                },
                color = if (isDarkTheme) Color.White else Color.Black
            )
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDarkTheme) Color(0xFF12385E) else Color.White
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = when (language) {
                            Language.EN -> "Account details"
                            Language.PL -> "Szczegóły konta"
                        },
                        color = if (isDarkTheme) Color.White else Color.Black,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Email: $emailText",
                        color = if (isDarkTheme) Color.White else Color.Black,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "UID: $uidText",
                        color = if (isDarkTheme) Color.White else Color.Black,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }


            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDarkTheme) Color(0xFF12385E) else Color.White
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = when (language) {
                            Language.EN -> "My alert area"
                            Language.PL -> "Moj obszar alertow"
                        },
                        color = if (isDarkTheme) Color.White else Color.Black,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = alertPostcode,
                        onValueChange = {
                            alertPostcode = it
                            alertAreaMessage = null
                        },
                        label = {
                            Text(
                                when (language) {
                                    Language.EN -> "Postcode"
                                    Language.PL -> "Kod pocztowy"
                                }
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = alertMilesText,
                        onValueChange = {
                            alertMilesText = it
                            alertAreaMessage = null
                        },
                        label = {
                            Text(
                                when (language) {
                                    Language.EN -> "Within miles"
                                    Language.PL -> "W promieniu mil"
                                }
                            )
                        },

                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val postcode = alertPostcode.trim()
                            val miles = alertMilesText.trim().toDoubleOrNull()
                            if (postcode.isBlank() || miles == null || miles <= 0.0) {
                                alertAreaMessage = when (language) {
                                    Language.EN -> "Enter a valid postcode and miles radius."
                                    Language.PL -> "Wpisz poprawny kod pocztowy i promien w milach."
                                }
                                return@Button
                            }
                            scope.launch {
                                savingAlertArea = true
                                try {
                                    val coords = withContext(Dispatchers.IO) {
                                        StreetGeocoder.coordinatesFromAddress(
                                            context = context,
                                            postcode = postcode,
                                            addressLine = ""
                                        )
                                    }
                                    if (coords == null) {
                                        alertAreaMessage = when (language) {
                                            Language.EN -> "Could not resolve postcode location."
                                            Language.PL -> "Nie udalo sie znalezc lokalizacji kodu."
                                        }
                                    } else {
                                        onNotificationSettingsChange(
                                            notificationSettings.copy(
                                                enabled = true,
                                                postcode = postcode.uppercase(Locale.UK),
                                                radiusMiles = miles,
                                                latitude = coords.first,
                                                longitude = coords.second
                                            )
                                        )
                                        alertAreaMessage = when (language) {
                                            Language.EN -> "Alert area saved. You'll get in-app alerts for nearby floods."
                                            Language.PL -> "Obszar alertow zapisany. Otrzymasz alerty o pobliskich powodziach."
                                        }
                                    }
                                } finally {
                                    savingAlertArea = false
                                }
                            }
                        }
                    ) {

                        Text(
                            when (language) {
                                Language.EN -> "Save alert area"
                                Language.PL -> "Zapisz obszar alertow"
                            }
                        )
                    }

                    if (savingAlertArea) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when (language) {
                                Language.EN -> "Saving..."
                                Language.PL -> "Zapisywanie..."
                            },
                            color = if (isDarkTheme) Color.White else Color.Black,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    alertAreaMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = msg,
                            color = if (isDarkTheme) Color.White else Color.Black,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    when {
                        countingFloodsInArea -> {
                            Text(
                                text = when (language) {
                                    Language.EN -> "Counting floods in selected area..."
                                    Language.PL -> "Zliczanie powodzi w wybranym obszarze..."
                                },
                                color = if (isDarkTheme) Color.White else Color.Black,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        floodsInAreaError != null -> {
                            Text(
                                text = when (language) {
                                    Language.EN -> "Could not count floods in area: ${floodsInAreaError!!}"
                                    Language.PL -> "Nie udalo sie zliczyc powodzi w obszarze: ${floodsInAreaError!!}"
                                },
                                color = Color(0xFFD32F2F),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        floodsInAreaCount != null -> {
                            val areaPostcode = notificationSettings.postcode.ifBlank { alertPostcode.trim() }
                            val areaMiles = notificationSettings.radiusMiles
                            Text(
                                text = when (language) {
                                    Language.EN -> "Flood alerts within ${areaMiles ?: 0.0} miles of $areaPostcode: $floodsInAreaCount"
                                    Language.PL -> "Alerty powodziowe w promieniu ${areaMiles ?: 0.0} mil od $areaPostcode: $floodsInAreaCount"
                                },
                                color = if (isDarkTheme) Color.White else Color.Black,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDarkTheme) Color(0xFF12385E) else Color.White
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = when (language) {
                            Language.EN -> "Floods added by you"
                            Language.PL -> "Powodzie dodane przez Ciebie"
                        },
                        color = if (isDarkTheme) Color.White else Color.Black,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (language) {
                            Language.EN -> "Total added: ${userFloodHistory.size}"
                            Language.PL -> "Lacznie dodane: ${userFloodHistory.size}"
                        },
                        color = if (isDarkTheme) Color.White else Color.Black,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    userFloodHistoryError?.let { error ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when (language) {
                                Language.EN -> "History unavailable: $error"
                                Language.PL -> "Historia niedostepna: $error"
                            },
                            color = Color(0xFFD32F2F),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (userFloodHistory.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        userFloodHistory.take(5).forEach { item ->
                            Text(
                                text = "${item.postcode} (${item.riskLevel})",
                                color = if (isDarkTheme) Color.White else Color.Black,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatScreen(
    modifier: Modifier = Modifier,
    language: Language,
    isDarkTheme: Boolean,
    onTabSelected: (MainTab) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val userId = FirebaseAuth.getInstance().currentUser?.uid
    var chatMessages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var chatError by remember { mutableStateOf<String?>(null) }
    var isSending by remember { mutableStateOf(false) }
    var predictionRows by remember { mutableStateOf<List<FloodRiskEntry>>(emptyList()) }
    var predictionDataLoading by remember { mutableStateOf(true) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.UK) }

    LaunchedEffect(Unit) {
        predictionDataLoading = true
        runCatching {
            withContext(Dispatchers.IO) {
                FloodRiskCsvLoader.loadFromAssets(context)
            }
        }.onSuccess {
            predictionRows = it
        }.onFailure {
            predictionRows = emptyList()
        }
        predictionDataLoading = false
    }

    DisposableEffect(userId) {
        if (userId.isNullOrBlank()) {
            chatError = when (language) {
                Language.EN -> "You need to sign in to use chat."
                Language.PL -> "Aby korzystac z czatu, zaloguj sie."
            }
            onDispose { }
        } else {
            val registration = ChatRepository.observeMessages(
                userId = userId,
                onUpdate = { chatMessages = it; chatError = null },
                onError = { error ->
                    chatError = if (
                        error is FirebaseFirestoreException &&
                        error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                    ) {
                        when (language) {
                            Language.EN -> "Chat permission denied. Update Firestore security rules for users/{uid}/chatMessages."
                            Language.PL -> "Brak uprawnien czatu. Zaktualizuj reguly Firestore dla users/{uid}/chatMessages."
                        }
                    } else {
                        error.message?.ifBlank { null } ?: when (language) {
                            Language.EN -> "Could not load chat."
                            Language.PL -> "Nie mozna zaladowac czatu."
                        }
                    }
                }
            )
            onDispose { registration.remove() }
        }
    }

    Scaffold(
        bottomBar = {
            BottomNavBar(
                language = language,
                selectedTab = MainTab.CHAT,
                onHomeClick = { onTabSelected(MainTab.HOME) },
                onAlertsClick = { onTabSelected(MainTab.ALERTS) },
                onSettingsClick = { onTabSelected(MainTab.SETTINGS) },
                onProfileClick = { onTabSelected(MainTab.PROFILE) },
                onChatClick = { onTabSelected(MainTab.CHAT) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(if (isDarkTheme) Color(0xFF001F3F) else Color(0xFFE0F2FF))
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(
                text = when (language) {
                    Language.EN -> "Flood Assistant"
                    Language.PL -> "Asystent Powodziowy"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = if (isDarkTheme) Color.White else Color.Black
            )
            chatError?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    color = Color(0xFFD32F2F),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(chatMessages) { message ->
                    val isUser = message.sender == "user"
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isUser) {
                                if (isDarkTheme) Color(0xFF0D47A1) else Color(0xFFBBDEFB)
                            } else {
                                if (isDarkTheme) Color(0xFF263238) else Color.White
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = if (isUser) {
                                    if (language == Language.EN) "You" else "Ty"
                                } else {
                                    if (language == Language.EN) "Assistant" else "Asystent"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDarkTheme) Color(0xFFCFD8DC) else Color(0xFF455A64)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = message.text,
                                color = if (isDarkTheme) Color.White else Color.Black
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = timeFormat.format(message.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDarkTheme) Color(0xFF90A4AE) else Color(0xFF607D8B)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            TextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSending && !userId.isNullOrBlank(),
                placeholder = {
                    Text(
                        when (language) {
                            Language.EN -> "Ask about flood safety or your alerts..."
                            Language.PL -> "Zapytaj o bezpieczenstwo powodziowe lub alerty..."
                        }
                    )
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val uid = userId ?: return@Button
                    val messageText = inputText.trim()
                    if (messageText.isBlank()) return@Button
                    val previousBotReply = chatMessages.lastOrNull { it.sender == "bot" }?.text
                    val previousUserMessage = chatMessages.lastOrNull { it.sender == "user" }?.text
                    inputText = ""
                    isSending = true
                    scope.launch {
                        runCatching {
                            ChatRepository.sendMessage(uid, "user", messageText)
                            delay(500)
                            ChatRepository.sendMessage(
                                uid,
                                "bot",
                                buildBotReply(
                                    question = messageText,
                                    language = language,
                                    predictionRows = predictionRows,
                                    predictionDataLoading = predictionDataLoading,
                                    lastBotReply = previousBotReply,
                                    lastUserMessage = previousUserMessage
                                )
                            )
                        }.onFailure { error ->
                            chatError = error.message?.ifBlank { null } ?: when (language) {
                                Language.EN -> "Message failed to send."
                                Language.PL -> "Nie udalo sie wyslac wiadomosci."
                            }
                        }
                        isSending = false
                    }
                },
                enabled = !isSending && inputText.isNotBlank() && !userId.isNullOrBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (isSending) {
                        if (language == Language.EN) "Sending..." else "Wysylanie..."
                    } else {
                        if (language == Language.EN) "Send" else "Wyslij"
                    }
                )
            }
        }
    }
}

private fun buildBotReply(
    question: String,
    language: Language,
    predictionRows: List<FloodRiskEntry>,
    predictionDataLoading: Boolean,
    lastBotReply: String?,
    lastUserMessage: String?
): String {
    return ChatFloodPredictor.buildReply(
        question = question,
        language = language,
        floodRows = predictionRows,
        isDataLoading = predictionDataLoading,
        lastBotReply = lastBotReply,
        lastUserMessage = lastUserMessage
    )
}

// Navigation Bar:  Home, Alerts, Settings, Profile, and Chat.

@Composable
private fun BottomNavBar(
    language: Language,
    selectedTab: MainTab,
    onHomeClick: () -> Unit,
    onAlertsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onProfileClick: () -> Unit,
    onChatClick: () -> Unit
) {

    NavigationBar {
        NavigationBarItem(
            selected = selectedTab == MainTab.HOME,
            onClick = onHomeClick,
            icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
            label = {
                Text(
                    text = when (language) {
                        Language.EN -> "Home"
                        Language.PL -> "Strona główna"
                    }
                )
            }
        )
        NavigationBarItem(
            selected = selectedTab == MainTab.ALERTS,
            onClick = onAlertsClick,
            icon = { Icon(Icons.Filled.Notifications, contentDescription = "Alerts") },
            label = {
                Text(
                    text = when (language) {
                        Language.EN -> "Alerts"
                        Language.PL -> "Alerty"
                    }
                )
            }
        )
        NavigationBarItem(
            selected = selectedTab == MainTab.SETTINGS,
            onClick = onSettingsClick,
            icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
            label = {
                Text(
                    text = when (language) {
                        Language.EN -> "Settings"
                        Language.PL -> "Ustawienia"
                    }
                )
            }
        )
        NavigationBarItem(
            selected = selectedTab == MainTab.PROFILE,
            onClick = onProfileClick,
            icon = { Icon(Icons.Filled.Person, contentDescription = "Profile") },
            label = {
                Text(
                    text = when (language) {
                        Language.EN -> "Profile"
                        Language.PL -> "Profil"
                    }
                )
            }
        )
        NavigationBarItem(
            selected = selectedTab == MainTab.CHAT,
            onClick = onChatClick,
            icon = { Text("AI") },
            label = {
                Text(
                    text = when (language) {
                        Language.EN -> "Chatbot"
                        Language.PL -> "Czat"
                    }
                )
            }
        )
    }
}