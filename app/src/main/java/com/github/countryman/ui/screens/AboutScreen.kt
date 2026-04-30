package com.github.countryman.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.github.countryman.R
import com.github.countryman.ui.i18n.AppLanguage
import com.github.countryman.ui.i18n.stringsFor

private val AboutAppBarIconSize = 22.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AboutScreen(onBack: () -> Unit, language: AppLanguage) {
    val s = stringsFor(language)
    val context = LocalContext.current

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(s.aboutTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_back),
                            contentDescription = s.back,
                            modifier = Modifier.size(AboutAppBarIconSize)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Countryman",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = s.aboutSubtitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = s.aboutBody,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(18.dp))
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
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

@Composable
fun ProjectCreditsLine(
    prefix: String,
    prefixUrl: String?,
    middleLabel: String,
    middleLinkLabel: String,
    middleLinkUrl: String,
    suffixLabel: String,
    suffixLinkLabel: String,
    suffixLinkUrl: String,
    onOpenLink: (String) -> Unit,
    textAlign: TextAlign = TextAlign.Center
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant
    val annotated = buildAnnotatedString {
        if (prefixUrl != null) {
            withLink(
                LinkAnnotation.Url(
                    url = prefixUrl,
                    styles = TextLinkStyles(style = SpanStyle(color = linkColor)),
                    linkInteractionListener = { onOpenLink(prefixUrl) }
                )
            ) { append(prefix) }
        } else {
            append(prefix)
        }
        append(" ")
        append(middleLabel)
        append(" ")
        withLink(
            LinkAnnotation.Url(
                url = middleLinkUrl,
                styles = TextLinkStyles(style = SpanStyle(color = linkColor)),
                linkInteractionListener = { onOpenLink(middleLinkUrl) }
            )
        ) { append(middleLinkLabel) }
        append(" ")
        append(suffixLabel)
        append(" ")
        withLink(
            LinkAnnotation.Url(
                url = suffixLinkUrl,
                styles = TextLinkStyles(style = SpanStyle(color = linkColor)),
                linkInteractionListener = { onOpenLink(suffixLinkUrl) }
            )
        ) { append(suffixLinkLabel) }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodySmall,
        color = bodyColor,
        textAlign = textAlign,
        modifier = Modifier.fillMaxWidth()
    )
}
