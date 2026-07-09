package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        MainScreen()
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
  val context = LocalContext.current
  val sharedPref = remember { context.getSharedPreferences("ParkingPrefs", Context.MODE_PRIVATE) }
  val initialUrl = remember { sharedPref.getString("custom_url", "https://parking.maldo.uk") ?: "https://parking.maldo.uk" }

  var webView by remember { mutableStateOf<WebView?>(null) }
  
  var canGoBack by remember { mutableStateOf(false) }
  var canGoForward by remember { mutableStateOf(false) }
  var isLoading by remember { mutableStateOf(true) }
  var progress by remember { mutableIntStateOf(0) }
  var hasError by remember { mutableStateOf(false) }
  var currentUrl by remember { mutableStateOf(initialUrl) }
  var showSettingsDialog by remember { mutableStateOf(false) }

  // Intercept system back press to navigate WebView history if possible
  BackHandler(enabled = canGoBack) {
    webView?.goBack()
  }

  Scaffold(
    modifier = Modifier.fillMaxSize()
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .background(MaterialTheme.colorScheme.background)
    ) {
      if (hasError) {
        OfflineScreen(
          onRetry = {
            hasError = false
            isLoading = true
            webView?.reload()
          }
        )
      } else {
        Box(modifier = Modifier.fillMaxSize()) {
          @SuppressLint("SetJavaScriptEnabled")
          AndroidView(
            factory = { ctx ->
              WebView(ctx).apply {
                webViewClient = object : WebViewClient() {
                  override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    isLoading = true
                    url?.let { currentUrl = it }
                  }

                  override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    isLoading = false
                    canGoBack = view?.canGoBack() ?: false
                    canGoForward = view?.canGoForward() ?: false
                    url?.let { currentUrl = it }
                  }

                  override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                  ) {
                    super.onReceivedError(view, request, error)
                    // Only show full-screen error if it's the main frame loading failing
                    if (request?.isForMainFrame == true) {
                      hasError = true
                      isLoading = false
                    }
                  }

                  override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                      return false
                    }
                    try {
                      val intent = if (url.startsWith("intent:")) {
                        Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                      } else {
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                      }
                      view?.context?.startActivity(intent)
                      return true
                    } catch (e: Exception) {
                      e.printStackTrace()
                    }
                    return true
                  }
                }

                webChromeClient = object : WebChromeClient() {
                  override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    progress = newProgress
                  }
                }

                settings.apply {
                  javaScriptEnabled = true
                  domStorageEnabled = true
                  useWideViewPort = true
                  loadWithOverviewMode = true
                  databaseEnabled = true
                  cacheMode = WebSettings.LOAD_DEFAULT
                }

                loadUrl(currentUrl)
                webView = this
              }
            },
            update = {
              // No-op or update webview configuration if needed
            },
            modifier = Modifier
              .fillMaxSize()
              .testTag("web_view_container")
          )

          // Thin elegant loading indicator at the absolute top of the screen
          AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
          ) {
            LinearProgressIndicator(
              progress = { progress / 100f },
              modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
              color = MaterialTheme.colorScheme.primary,
              trackColor = Color.Transparent
            )
          }

          // Subtle, discrete configuration button in the bottom-right corner
          Box(
            modifier = Modifier
              .align(Alignment.BottomEnd)
              .padding(16.dp)
          ) {
            IconButton(
              onClick = { showSettingsDialog = true },
              modifier = Modifier
                .size(36.dp)
                .background(
                  color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                  shape = RoundedCornerShape(50)
                )
                .border(
                  width = 1.dp,
                  color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                  shape = RoundedCornerShape(50)
                )
                .testTag("discrete_config_button")
            ) {
              Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Configurar URL",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }
      }
    }
  }

  // Settings Dialog to customize custom URL
  if (showSettingsDialog) {
    var inputUrl by remember { mutableStateOf(currentUrl) }
    
    AlertDialog(
      onDismissRequest = { showSettingsDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Configurar Dirección Web",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }
      },
      text = {
        Column(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = "Introduce la URL de la web que deseas embeber:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
          )
          
          OutlinedTextField(
            value = inputUrl,
            onValueChange = { inputUrl = it },
            placeholder = { Text("https://parking.maldo.uk") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("url_input_field"),
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = RoundedCornerShape(12.dp)
          )
          
          Spacer(modifier = Modifier.height(16.dp))
          
          Text(
            text = "Nota: La dirección debe comenzar con http:// o https://",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            var formattedUrl = inputUrl.trim()
            if (formattedUrl.isNotEmpty()) {
              if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
                formattedUrl = "https://$formattedUrl"
              }
              // Save URL to preferences
              sharedPref.edit().putString("custom_url", formattedUrl).apply()
              currentUrl = formattedUrl
              webView?.loadUrl(formattedUrl)
            }
            showSettingsDialog = false
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
          ),
          modifier = Modifier.testTag("save_url_button")
        ) {
          Text("Guardar", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        Row {
          TextButton(
            onClick = {
              val defaultUrl = "https://parking.maldo.uk"
              sharedPref.edit().putString("custom_url", defaultUrl).apply()
              currentUrl = defaultUrl
              webView?.loadUrl(defaultUrl)
              showSettingsDialog = false
            },
            modifier = Modifier.testTag("reset_url_button")
          ) {
            Text("Restablecer", color = MaterialTheme.colorScheme.error)
          }
          Spacer(modifier = Modifier.width(8.dp))
          TextButton(
            onClick = { showSettingsDialog = false },
            modifier = Modifier.testTag("cancel_button")
          ) {
            Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      },
      shape = RoundedCornerShape(24.dp),
      containerColor = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp
    )
  }
}

@Composable
fun OfflineScreen(onRetry: () -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Icon(
      imageVector = Icons.Default.SignalWifiOff,
      contentDescription = "Sin conexión",
      tint = MaterialTheme.colorScheme.error,
      modifier = Modifier.size(64.dp)
    )
    Spacer(modifier = Modifier.height(24.dp))
    Text(
      text = "Sin conexión a Internet",
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onBackground,
      textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
      text = "No se pudo cargar el portal de parking. Por favor, comprueba tu conexión e inténtalo de nuevo.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
      textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(32.dp))
    Button(
      onClick = onRetry,
      colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
      ),
      modifier = Modifier
        .fillMaxWidth(0.6f)
        .testTag("retry_button")
    ) {
      Text(
        text = "Reintentar",
        fontWeight = FontWeight.Medium
      )
    }
  }
}
