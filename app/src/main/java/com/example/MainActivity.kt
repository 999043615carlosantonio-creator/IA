package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.model.CharacterInventory
import com.example.model.ChatMessage
import com.example.model.MemoryEntry
import com.example.model.UserProfile
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.CharacterChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
  private var textToSpeech: TextToSpeech? = null
  private var ttsReady = false
  private val viewModel: CharacterChatViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    textToSpeech = TextToSpeech(this) { status ->
      if (status == TextToSpeech.SUCCESS) {
        val result = textToSpeech?.setLanguage(Locale("pt", "BR"))
        ttsReady = (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED)
      }
    }

    setContent {
      MyApplicationTheme {
        CharacterAIChatScreen(
          viewModel = viewModel,
          speakText = { text ->
            if (ttsReady) {
              textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "VoiceChatUtterance")
            }
          },
          stopSpeaking = {
            textToSpeech?.stop()
          }
        )
      }
    }
  }

  override fun onDestroy() {
    textToSpeech?.stop()
    textToSpeech?.shutdown()
    super.onDestroy()
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterAIChatScreen(
  viewModel: CharacterChatViewModel,
  speakText: (String) -> Unit,
  stopSpeaking: () -> Unit
) {
  val context = LocalContext.current
  val listState = rememberLazyListState()
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  var inputText by remember { mutableStateOf("") }

  var hasAudioPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
      ) == PackageManager.PERMISSION_GRANTED
    )
  }

  // Handle TTS Speak events from ViewModel
  LaunchedEffect(Unit) {
    viewModel.ttsSpeakEvent.collect { textToSpeak ->
      speakText(textToSpeak)
    }
  }

  // Handle Toasts from ViewModel
  LaunchedEffect(Unit) {
    viewModel.toastEvent.collect { toastMsg ->
      Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
    }
  }

  // Android Gallery File Picker using ActivityResultContracts.GetContent
  val galleryImagePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri ->
    if (uri != null) {
      viewModel.onCharacterImageSelected(uri)
    }
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    hasAudioPermission = isGranted
  }

  // SpeechRecognizer instance
  var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

  DisposableEffect(Unit) {
    onDispose {
      speechRecognizer?.destroy()
    }
  }

  fun triggerListening() {
    if (!hasAudioPermission) {
      permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
      return
    }

    if (uiState.isListeningVoice) {
      speechRecognizer?.stopListening()
      viewModel.setListeningVoice(false)
    } else {
      if (!SpeechRecognizer.isRecognitionAvailable(context)) {
        viewModel.onUserMessage("Gere um prompt do personagem em memória")
        return
      }

      speechRecognizer?.destroy()
      speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
        setRecognitionListener(object : RecognitionListener {
          override fun onReadyForSpeech(params: Bundle?) {
            viewModel.setListeningVoice(true)
          }
          override fun onBeginningOfSpeech() {}
          override fun onRmsChanged(rmsdB: Float) {}
          override fun onBufferReceived(buffer: ByteArray?) {}
          override fun onEndOfSpeech() {
            viewModel.setListeningVoice(false)
          }
          override fun onError(error: Int) {
            viewModel.setListeningVoice(false)
          }
          override fun onResults(results: Bundle?) {
            viewModel.setListeningVoice(false)
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognizedText = matches?.firstOrNull() ?: ""
            if (recognizedText.isNotBlank()) {
              viewModel.onUserMessage(recognizedText)
            }
          }
          override fun onPartialResults(partialResults: Bundle?) {}
          override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
          putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
          putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
        }
        startListening(intent)
      }
    }
  }

  LaunchedEffect(uiState.messages.size) {
    if (uiState.messages.isNotEmpty()) {
      listState.animateScrollToItem(uiState.messages.size - 1)
    }
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primaryContainer,
              modifier = Modifier.size(38.dp)
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Default.SmartToy,
                  contentDescription = "Assistente",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(22.dp)
                )
              }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = stringResource(R.string.title_character_ai),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = if (uiState.isAnalyzingImage) "Analisando com Gemini..."
                else if (uiState.isListeningVoice) "Ouvindo sua voz..."
                else "Google Gemini Ativo",
                style = MaterialTheme.typography.bodySmall,
                color = if (uiState.isListeningVoice) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        },
        actions = {
          IconButton(
            onClick = { viewModel.setProfileSheetVisibility(true) },
            modifier = Modifier.testTag("btn_open_profiles")
          ) {
            Icon(
              imageVector = Icons.Default.AccountCircle,
              contentDescription = stringResource(R.string.profile_options),
              tint = MaterialTheme.colorScheme.primary
            )
          }
          IconButton(
            onClick = { viewModel.setSettingsSheetVisibility(true) },
            modifier = Modifier.testTag("btn_open_settings")
          ) {
            Icon(
              imageVector = Icons.Default.Settings,
              contentDescription = stringResource(R.string.settings_title),
              tint = MaterialTheme.colorScheme.primary
            )
          }
          IconButton(
            onClick = { viewModel.setMemorySheetVisibility(true) },
            modifier = Modifier.testTag("btn_open_memory")
          ) {
            Icon(
              imageVector = Icons.Default.FolderShared,
              contentDescription = stringResource(R.string.view_inventory)
            )
          }
          IconButton(
            onClick = {
              viewModel.toggleTts()
              if (!uiState.isTtsEnabled) stopSpeaking()
            },
            modifier = Modifier.testTag("btn_toggle_tts")
          ) {
            Icon(
              imageVector = if (uiState.isTtsEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
              contentDescription = "Voz da assistente",
              tint = if (uiState.isTtsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
          }
          IconButton(
            onClick = {
              stopSpeaking()
              viewModel.clearChat()
            },
            modifier = Modifier.testTag("btn_clear_chat")
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = stringResource(R.string.clear_chat)
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface,
          titleContentColor = MaterialTheme.colorScheme.onSurface
        )
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .background(MaterialTheme.colorScheme.background)
    ) {
      // Banner do Perfil Ativo com atalho rápido para troca
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 7.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
          ) {
            Icon(
              imageVector = Icons.Default.AccountCircle,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = uiState.currentProfile.name,
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface
                )
                if (uiState.currentProfile.isDefault) {
                  Spacer(modifier = Modifier.width(6.dp))
                  Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                  ) {
                    Text(
                      text = "Padrão",
                      fontSize = 9.5.sp,
                      fontWeight = FontWeight.SemiBold,
                      color = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                  }
                }
              }
              Text(
                text = if (uiState.currentProfile.customStory != null) {
                  "📖 ${uiState.currentProfile.storyTitle}"
                } else {
                  "📖 Especialista em A Ordem dos Sem Olhos"
                },
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
          OutlinedButton(
            onClick = { viewModel.setProfileSheetVisibility(true) },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
            modifier = Modifier
              .height(30.dp)
              .testTag("btn_switch_profile_header")
          ) {
            Text(
              text = stringResource(R.string.switch_profile),
              fontSize = 11.sp,
              fontWeight = FontWeight.Medium
            )
          }
        }
      }
      // Chat History
      LazyColumn(
        state = listState,
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        items(uiState.messages, key = { it.id }) { message ->
          ChatBubbleRow(
            message = message,
            onCopyPrompt = { prompt -> viewModel.copyToClipboard(prompt) },
            onCopyResponse = { text ->
              val isPrompt = message.generatedPrompt != null
              val target = message.generatedPrompt ?: text
              viewModel.copyMessageText(target, isPrompt)
            },
            onSpeak = { speakText(message.text) },
            onOpenMemory = { viewModel.setMemorySheetVisibility(true) }
          )
        }

        if (uiState.isAnalyzingImage) {
          item {
            GeminiAnalyzingIndicator(text = "Analisando traços com Gemini...")
          }
        }

        if (uiState.isOptimizingPrompt) {
          item {
            GeminiAnalyzingIndicator(text = stringResource(R.string.optimizing_prompt))
          }
        }
      }

      // Voice Status Bar if listening
      AnimatedVisibility(visible = uiState.isListeningVoice) {
        VoiceListeningIndicator()
      }

      // Fixed Bottom Input Area with Message Field & Prominent Mic Button
      Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Photo Picker Button from Gallery
          IconButton(
            onClick = {
              galleryImagePickerLauncher.launch("image/*")
            },
            modifier = Modifier
              .size(44.dp)
              .testTag("btn_attach_character_image"),
            colors = IconButtonDefaults.iconButtonColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
          ) {
            Icon(
              imageVector = Icons.Default.AddPhotoAlternate,
              contentDescription = stringResource(R.string.attach_image),
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(24.dp)
            )
          }

          Spacer(modifier = Modifier.width(8.dp))

          // Text Input Field
          OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            placeholder = { Text(stringResource(R.string.input_placeholder), fontSize = 13.5.sp) },
            modifier = Modifier
              .weight(1f)
              .testTag("input_message_field"),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = MaterialTheme.colorScheme.primary,
              unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
              focusedContainerColor = MaterialTheme.colorScheme.surface,
              unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            maxLines = 4
          )

          Spacer(modifier = Modifier.width(8.dp))

          if (inputText.isNotBlank()) {
            // Send Button when text is typed
            IconButton(
              onClick = {
                viewModel.onUserMessage(inputText)
                inputText = ""
              },
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .testTag("btn_send_message"),
              colors = IconButtonDefaults.iconButtonColors(
                contentColor = Color.White
              )
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.send),
                modifier = Modifier.size(22.dp)
              )
            }
          } else {
            // Prominent Fixed Mic Button for Voice Input
            IconButton(
              onClick = { triggerListening() },
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (uiState.isListeningVoice) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primaryContainer)
                .testTag("btn_voice_mic"),
              colors = IconButtonDefaults.iconButtonColors(
                contentColor = if (uiState.isListeningVoice) Color.White else MaterialTheme.colorScheme.primary
              )
            ) {
              Icon(
                imageVector = if (uiState.isListeningVoice) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = stringResource(R.string.press_to_speak),
                modifier = Modifier.size(24.dp)
              )
            }
          }
        }
      }
    }

    // Modal Sheet for Character Visual Memory
    if (uiState.showMemorySheet) {
      ModalBottomSheet(
        onDismissRequest = { viewModel.setMemorySheetVisibility(false) },
        sheetState = rememberModalBottomSheetState()
      ) {
        CharacterMemorySheetContent(
          characters = uiState.savedCharacters,
          onAddCharacterPhoto = { character, uri ->
            viewModel.addPhotoToCharacter(character, uri)
          },
          onRemoveCharacterPhoto = { character, index ->
            viewModel.removePhotoFromCharacter(character, index)
          },
          onCopyPrompt = { prompt ->
            viewModel.copyToClipboard(prompt)
          },
          onOptimizePrompt = { char ->
            viewModel.optimizeCharacterPromptWithGemini(char)
          },
          onClose = { viewModel.setMemorySheetVisibility(false) }
        )
      }
    }

    // Modal Sheet for Settings and Specific Permanent Memory
    if (uiState.showSettingsSheet) {
      ModalBottomSheet(
        onDismissRequest = { viewModel.setSettingsSheetVisibility(false) },
        sheetState = rememberModalBottomSheetState()
      ) {
        SettingsMemorySheetContent(
          charactersCount = uiState.savedCharacters.size,
          memories = uiState.savedMemories,
          onOpenCharactersMemory = {
            viewModel.setSettingsSheetVisibility(false)
            viewModel.setMemorySheetVisibility(true)
          },
          onOpenProfiles = {
            viewModel.setSettingsSheetVisibility(false)
            viewModel.setProfileSheetVisibility(true)
          },
          onAddMemoryEntry = { content ->
            viewModel.addManualMemoryEntry(content)
          },
          onDeleteMemoryEntry = { id ->
            viewModel.deleteMemoryEntry(id)
          },
          onClose = { viewModel.setSettingsSheetVisibility(false) }
        )
      }
    }

    // Modal Sheet for Profile Management & Story Switching
    if (uiState.showProfileSheet) {
      ModalBottomSheet(
        onDismissRequest = { viewModel.setProfileSheetVisibility(false) },
        sheetState = rememberModalBottomSheetState()
      ) {
        ProfileOptionsSheetContent(
          currentProfile = uiState.currentProfile,
          profiles = uiState.allProfiles,
          onSwitchProfile = { profileId ->
            viewModel.switchProfile(profileId)
          },
          onCreateProfile = { name ->
            viewModel.createProfile(name)
          },
          onDeleteProfile = { profileId ->
            viewModel.deleteProfile(profileId)
          },
          onClose = { viewModel.setProfileSheetVisibility(false) }
        )
      }
    }
  }
}

@Composable
fun GeminiAnalyzingIndicator(text: String = "Analisando traços com Gemini...") {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    ),
    modifier = Modifier.widthIn(max = 280.dp)
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      CircularProgressIndicator(
        modifier = Modifier.size(20.dp),
        strokeWidth = 2.5.dp,
        color = MaterialTheme.colorScheme.primary
      )
      Spacer(modifier = Modifier.width(10.dp))
      Text(
        text = text,
        fontSize = 13.5.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onPrimaryContainer
      )
    }
  }
}

@Composable
fun VoiceListeningIndicator() {
  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 0.95f,
    targetValue = 1.15f,
    animationSpec = infiniteRepeatable(
      animation = tween(600, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulseScale"
  )

  Surface(
    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      Box(
        modifier = Modifier
          .size(12.dp)
          .scale(pulseScale)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.error)
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = "Ouvindo... Toque no microfone para enviar",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onErrorContainer
      )
    }
  }
}

@Composable
fun ChatBubbleRow(
  message: ChatMessage,
  onCopyPrompt: (String) -> Unit,
  onCopyResponse: (String) -> Unit,
  onSpeak: () -> Unit,
  onOpenMemory: () -> Unit = {}
) {
  val alignment = if (message.isUser) Alignment.End else Alignment.Start
  val bubbleColor = if (message.isUser) {
    MaterialTheme.colorScheme.primary
  } else {
    MaterialTheme.colorScheme.surface
  }
  val textColor = if (message.isUser) {
    MaterialTheme.colorScheme.onPrimary
  } else {
    MaterialTheme.colorScheme.onSurface
  }
  val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = alignment
  ) {
    Card(
      shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (message.isUser) 18.dp else 4.dp,
        bottomEnd = if (message.isUser) 4.dp else 18.dp
      ),
      colors = CardDefaults.cardColors(containerColor = bubbleColor),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      modifier = Modifier
        .widthIn(max = 330.dp)
        .testTag(if (message.isUser) "user_bubble" else "assistant_bubble")
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        if (!message.isUser) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 6.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Gemini Character AI",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
              )
            }

            // Quick Copy Action in Assistant Header
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable {
                  val textToCopy = message.generatedPrompt ?: message.text
                  onCopyResponse(textToCopy)
                }
                .testTag("btn_copy_response_header")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.ContentCopy,
                  contentDescription = stringResource(R.string.copy_response),
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                  text = stringResource(R.string.copy_response),
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.primary
                )
              }
            }
          }
        }

        // Display user uploaded image preview
        if (message.imageUri != null) {
          AsyncImage(
            model = message.imageUri,
            contentDescription = "Foto do Personagem",
            modifier = Modifier
              .fillMaxWidth()
              .height(180.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
          )
          Spacer(modifier = Modifier.height(8.dp))
        }

        // Text Message
        Text(
          text = message.text,
          color = textColor,
          fontSize = 14.5.sp,
          lineHeight = 20.sp
        )

        // Generated Prompt Section with 1-click copy
        if (message.generatedPrompt != null) {
          Spacer(modifier = Modifier.height(12.dp))
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            modifier = Modifier
              .fillMaxWidth()
              .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                RoundedCornerShape(10.dp)
              )
          ) {
            Column(modifier = Modifier.padding(10.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = "Prompt Gerado (Fidelidade Estrita)",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                  onClick = { onCopyPrompt(message.generatedPrompt) },
                  modifier = Modifier
                    .size(28.dp)
                    .testTag("btn_copy_prompt")
                ) {
                  Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.copy_prompt),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                  )
                }
              }

              Spacer(modifier = Modifier.height(4.dp))

              Text(
                text = message.generatedPrompt,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 16.sp
              )

              Spacer(modifier = Modifier.height(6.dp))

              OutlinedButton(
                onClick = { onCopyPrompt(message.generatedPrompt) },
                modifier = Modifier
                  .fillMaxWidth()
                  .height(34.dp)
                  .testTag("btn_copy_prompt_full"),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.ContentCopy,
                  contentDescription = null,
                  modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Copiar para Gerador Gratuito", fontSize = 11.sp)
              }
            }
          }
        }

        // Timestamp, Copy & Audio Play
        if (message.suggestAccessMemoryButton) {
          Spacer(modifier = Modifier.height(10.dp))
          Button(
            onClick = onOpenMemory,
            modifier = Modifier
              .fillMaxWidth()
              .height(38.dp)
              .testTag("btn_suggested_access_memory"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
          ) {
            Icon(
              imageVector = Icons.Default.FolderShared,
              contentDescription = null,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Acessar Memória",
              fontSize = 12.5.sp,
              fontWeight = FontWeight.SemiBold
            )
          }
        }

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (!message.isUser) {
            IconButton(
              onClick = {
                val textToCopy = message.generatedPrompt ?: message.text
                onCopyResponse(textToCopy)
              },
              modifier = Modifier
                .size(24.dp)
                .testTag("btn_copy_response_footer")
            ) {
              Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.copy_response),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp)
              )
            }
            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
              onClick = onSpeak,
              modifier = Modifier.size(24.dp)
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Ouvir resposta",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
              )
            }
            Spacer(modifier = Modifier.width(6.dp))
          }

          Text(
            text = timeFormat.format(Date(message.timestamp)),
            fontSize = 10.sp,
            color = if (message.isUser) textColor.copy(alpha = 0.75f) else MaterialTheme.colorScheme.outline
          )
        }
      }
    }
  }
}

@Composable
fun CharacterMemorySheetContent(
  characters: List<CharacterInventory>,
  onAddCharacterPhoto: (CharacterInventory, String) -> Unit,
  onRemoveCharacterPhoto: (CharacterInventory, Int) -> Unit,
  onCopyPrompt: (String) -> Unit,
  onOptimizePrompt: (CharacterInventory) -> Unit,
  onClose: () -> Unit
) {
  var pendingPhotoCharacter by remember { mutableStateOf<CharacterInventory?>(null) }

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    val char = pendingPhotoCharacter
    if (char != null && uri != null) {
      onAddCharacterPhoto(char, uri.toString())
    }
    pendingPhotoCharacter = null
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp, vertical = 12.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column {
        Text(
          text = "Inventário de Personagens",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary
        )
        Text(
          text = "${characters.size} personagem(ns) catalogado(s)",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      IconButton(
        onClick = onClose,
        modifier = Modifier.size(36.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Fechar inventário"
        )
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    Text(
      text = "Abaixo de cada nome, veja as fotos do personagem com botões laterais para passar (até 5 fotos). Toque no botão '+' para adicionar mais fotos.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(bottom = 10.dp)
    )

    if (characters.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(200.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "Nenhum personagem registrado no inventário ainda.",
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .height(480.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
      ) {
        items(characters) { char ->
          CharacterInventoryCard(
            character = char,
            onAddPhoto = {
              if (char.photos.size < 5) {
                pendingPhotoCharacter = char
                photoPickerLauncher.launch(
                  androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              }
            },
            onRemovePhoto = { index ->
              onRemoveCharacterPhoto(char, index)
            },
            onOptimizePrompt = { onOptimizePrompt(char) },
            onCopyPrompt = { onCopyPrompt(char.generateImagePrompt()) }
          )
        }
      }
    }
  }
}

@Composable
fun CharacterInventoryCard(
  character: CharacterInventory,
  onAddPhoto: () -> Unit,
  onRemovePhoto: (Int) -> Unit,
  onOptimizePrompt: () -> Unit,
  onCopyPrompt: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      // 1. Nome do personagem
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = character.name,
          fontWeight = FontWeight.Bold,
          fontSize = 17.sp,
          color = MaterialTheme.colorScheme.primary
        )
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = MaterialTheme.colorScheme.primaryContainer
        ) {
          Text(
            text = character.role,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // 2. Carrossel de fotos abaixo do nome com botões laterais e botão de '+' (até 5 fotos)
      CharacterPhotoCarousel(
        photos = character.photos,
        maxPhotos = 5,
        onAddPhoto = onAddPhoto,
        onRemovePhoto = onRemovePhoto
      )

      Spacer(modifier = Modifier.height(10.dp))

      // 3. Informações de cada personagem após as fotos
      if (character.age.isNotBlank()) {
        Text(
          text = "Idade: ${character.age}",
          fontSize = 12.5.sp,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
      }

      if (character.notes.isNotBlank()) {
        Text(
          text = character.notes,
          fontSize = 12.5.sp,
          lineHeight = 17.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
      }

      TraitsSection(title = "Traços Físicos:", traits = character.physicalTraits)
      TraitsSection(title = "Vestimentas:", traits = character.clothingTraits)
      TraitsSection(title = "Acessórios:", traits = character.accessories)

      Spacer(modifier = Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Button(
          onClick = onOptimizePrompt,
          modifier = Modifier
            .weight(1f)
            .testTag("btn_optimize_character_prompt_${character.name}"),
          shape = RoundedCornerShape(8.dp),
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
          Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(15.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "Otimizar Prompt",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
          )
        }

        OutlinedButton(
          onClick = onCopyPrompt,
          modifier = Modifier
            .weight(1f)
            .testTag("btn_copy_character_prompt_${character.name}"),
          shape = RoundedCornerShape(8.dp)
        ) {
          Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = null,
            modifier = Modifier.size(14.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "Copiar Prompt",
            fontSize = 12.sp
          )
        }
      }
    }
  }
}

@Composable
fun CharacterPhotoCarousel(
  photos: List<String>,
  maxPhotos: Int = 5,
  onAddPhoto: () -> Unit,
  onRemovePhoto: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  var currentIndex by remember(photos.size) { mutableStateOf(0) }

  // Garante que currentIndex seja válido
  val safeIndex = if (photos.isEmpty()) 0 else currentIndex.coerceIn(0, photos.size - 1)

  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(150.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      // Botão para passar para a esquerda / foto anterior
      IconButton(
        onClick = {
          if (photos.isNotEmpty()) {
            currentIndex = if (safeIndex > 0) safeIndex - 1 else photos.size - 1
          }
        },
        enabled = photos.size > 1,
        modifier = Modifier
          .size(38.dp)
          .background(
            color = if (photos.size > 1) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = CircleShape
          )
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "Foto anterior",
          tint = if (photos.size > 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
      }

      // Área Central da Foto Atual
      Box(
        modifier = Modifier
          .weight(1f)
          .height(150.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
          .border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            shape = RoundedCornerShape(12.dp)
          ),
        contentAlignment = Alignment.Center
      ) {
        if (photos.isNotEmpty()) {
          val currentPhotoUri = photos[safeIndex]
          AsyncImage(
            model = currentPhotoUri,
            contentDescription = "Foto ${safeIndex + 1} de ${photos.size}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )

          // Indicador de foto atual (ex: 1/3)
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.65f),
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .padding(bottom = 6.dp)
          ) {
            Text(
              text = "Foto ${safeIndex + 1} de ${photos.size} (máx: $maxPhotos)",
              color = Color.White,
              fontSize = 11.sp,
              fontWeight = FontWeight.Medium,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
          }

          // Botão para excluir a foto atual
          IconButton(
            onClick = {
              onRemovePhoto(safeIndex)
              if (currentIndex > 0) currentIndex--
            },
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(4.dp)
              .size(26.dp)
              .background(Color.Black.copy(alpha = 0.65f), CircleShape)
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Remover foto",
              tint = Color.White,
              modifier = Modifier.size(15.dp)
            )
          }
        } else {
          // Nenhuma foto ainda
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
              .fillMaxSize()
              .clickable { onAddPhoto() }
              .padding(12.dp)
          ) {
            Icon(
              imageVector = Icons.Default.AddPhotoAlternate,
              contentDescription = "Adicionar primeira foto",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Adicionar foto inicial",
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.primary
            )
          }
        }
      }

      // Botão para passar para a direita / próxima foto
      IconButton(
        onClick = {
          if (photos.isNotEmpty()) {
            currentIndex = (safeIndex + 1) % photos.size
          }
        },
        enabled = photos.size > 1,
        modifier = Modifier
          .size(38.dp)
          .background(
            color = if (photos.size > 1) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = CircleShape
          )
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowForward,
          contentDescription = "Próxima foto",
          tint = if (photos.size > 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
      }

      // Botão de '+' para adicionar nova foto (até 5 fotos)
      if (photos.size < maxPhotos) {
        IconButton(
          onClick = onAddPhoto,
          modifier = Modifier
            .size(38.dp)
            .background(
              color = MaterialTheme.colorScheme.primary,
              shape = CircleShape
            )
        ) {
          Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Adicionar nova foto (${photos.size}/$maxPhotos)",
            tint = MaterialTheme.colorScheme.onPrimary
          )
        }
      }
    }

    // Miniaturas indicadoras abaixo do carrossel
    if (photos.size > 1) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
      ) {
        photos.forEachIndexed { idx, _ ->
          Box(
            modifier = Modifier
              .padding(horizontal = 3.dp)
              .size(if (idx == safeIndex) 8.dp else 6.dp)
              .background(
                color = if (idx == safeIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape
              )
              .clickable { currentIndex = idx }
          )
        }
      }
    }
  }
}

@Composable
fun TraitsSection(title: String, traits: List<String>) {
  if (traits.isNotEmpty()) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
      Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
      )
      traits.forEach { trait ->
        Text(
          text = "• $trait",
          fontSize = 11.5.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(start = 4.dp, top = 1.dp)
        )
      }
    }
  }
}

@Composable
fun SettingsMemorySheetContent(
  charactersCount: Int,
  memories: List<MemoryEntry>,
  onOpenCharactersMemory: () -> Unit,
  onOpenProfiles: () -> Unit,
  onAddMemoryEntry: (String) -> Unit,
  onDeleteMemoryEntry: (String) -> Unit,
  onClose: () -> Unit
) {
  var newMemoryText by remember { mutableStateOf("") }
  val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp, vertical = 12.dp)
  ) {
    // Header
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.Settings,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
          Text(
            text = "Configurações e Memória",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
          )
          Text(
            text = "Armazenamento permanente no celular",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      IconButton(
        onClick = onClose,
        modifier = Modifier.size(36.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Fechar configurações"
        )
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Botão de acesso rápido aos Perfis de História
    Card(
      shape = RoundedCornerShape(14.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
      ),
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f)
        ) {
          Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(24.dp)
          )
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "Perfis de História",
              fontWeight = FontWeight.Bold,
              fontSize = 14.5.sp,
              color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
              text = "Crie e alterne perfis com histórias personalizadas",
              fontSize = 11.5.sp,
              color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )
          }
        }
        Button(
          onClick = onOpenProfiles,
          shape = RoundedCornerShape(8.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          modifier = Modifier.testTag("btn_settings_open_profiles")
        ) {
          Text("Gerenciar", fontSize = 12.sp)
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Botão de acesso rápido ao inventário visual de personagens
    Card(
      shape = RoundedCornerShape(14.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
      ),
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f)
        ) {
          Icon(
            imageVector = Icons.Default.FolderShared,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
          )
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "Inventário de Personagens",
              fontWeight = FontWeight.Bold,
              fontSize = 14.5.sp,
              color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
              text = "$charactersCount personagem(ns) catalogado(s) com até 5 fotos cada",
              fontSize = 11.5.sp,
              color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
          }
        }
        Button(
          onClick = onOpenCharactersMemory,
          shape = RoundedCornerShape(8.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          modifier = Modifier.testTag("btn_settings_open_characters")
        ) {
          Text("Abrir", fontSize = 12.sp)
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Seção de Memória Específica Guardada
    Text(
      text = "Memória do Aparelho (Informações Específicas)",
      fontWeight = FontWeight.Bold,
      fontSize = 15.sp,
      color = MaterialTheme.colorScheme.onSurface
    )
    Text(
      text = "Diga \"GUARDE EM SUA MEMORIA ESSA INFORMAÇÃO ESPECÍFICA\" no chat ou digite abaixo para persistir permanentemente no dispositivo sem perder nenhum dado.",
      fontSize = 12.sp,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
    )

    // Campo para adicionar nova informação na memória diretamente
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      OutlinedTextField(
        value = newMemoryText,
        onValueChange = { newMemoryText = it },
        placeholder = { Text("Adicionar anotação na memória...", fontSize = 12.5.sp) },
        modifier = Modifier
          .weight(1f)
          .testTag("input_settings_new_memory"),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = MaterialTheme.colorScheme.primary,
          unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        ),
        maxLines = 3
      )
      Spacer(modifier = Modifier.width(8.dp))
      Button(
        onClick = {
          if (newMemoryText.isNotBlank()) {
            onAddMemoryEntry(newMemoryText)
            newMemoryText = ""
          }
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .height(52.dp)
          .testTag("btn_save_memory_manual")
      ) {
        Icon(
          imageVector = Icons.Default.Add,
          contentDescription = "Guardar na memória"
        )
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Lista de Informações Específicas Guardadas
    if (memories.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(180.dp),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(
            imageVector = Icons.Default.Storage,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(36.dp)
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Nenhuma informação guardada na memória ainda.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = "Use o comando: \"GUARDE EM SUA MEMORIA ESSA INFORMAÇÃO ESPECÍFICA\"",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 2.dp)
          )
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .height(280.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
      ) {
        items(memories, key = { it.id }) { memory ->
          Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalAlignment = Alignment.Top,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = memory.content,
                  fontSize = 13.5.sp,
                  lineHeight = 19.sp,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = dateFormat.format(Date(memory.timestamp)),
                  fontSize = 10.5.sp,
                  color = MaterialTheme.colorScheme.outline
                )
              }
              IconButton(
                onClick = { onDeleteMemoryEntry(memory.id) },
                modifier = Modifier
                  .size(28.dp)
                  .testTag("btn_delete_memory_${memory.id}")
              ) {
                Icon(
                  imageVector = Icons.Default.Delete,
                  contentDescription = "Excluir informação",
                  tint = MaterialTheme.colorScheme.error,
                  modifier = Modifier.size(16.dp)
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun ProfileOptionsSheetContent(
  currentProfile: UserProfile,
  profiles: List<UserProfile>,
  onSwitchProfile: (String) -> Unit,
  onCreateProfile: (String) -> Unit,
  onDeleteProfile: (String) -> Unit,
  onClose: () -> Unit
) {
  var showCreateDialog by remember { mutableStateOf(false) }
  var newProfileName by remember { mutableStateOf("") }
  var profileToDelete by remember { mutableStateOf<UserProfile?>(null) }
  val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp, vertical = 12.dp)
  ) {
    // Header
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.AccountCircle,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
          Text(
            text = stringResource(R.string.profile_options),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = "Alterne perfis e salve histórias independentes",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      IconButton(onClick = onClose) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Fechar opções de perfil"
        )
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Card explicativo sobre a regra da primeira mensagem
    Card(
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
      ),
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(12.dp),
        verticalAlignment = Alignment.Top
      ) {
        Icon(
          imageVector = Icons.Default.MenuBook,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.secondary,
          modifier = Modifier
            .size(20.dp)
            .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
          Text(
            text = "Regra de Histórias por Perfil",
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer
          )
          Spacer(modifier = Modifier.height(3.dp))
          Text(
            text = "• Cada perfil possui sua própria conversa e história salva.\n" +
              "• Se você enviar um comando para salvar outra história logo na PRIMEIRA mensagem ao abrir a conversa, ela será gravada para sempre neste perfil.\n" +
              "• Caso não envie comando na primeira mensagem, continuarei especialista em \"A Ordem dos Sem Olhos\".\n" +
              "• Esse comando não é aceito após a primeira mensagem da conversa.",
            fontSize = 11.5.sp,
            lineHeight = 16.5.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f)
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Botão Criar Novo Perfil
    Button(
      onClick = {
        newProfileName = ""
        showCreateDialog = true
      },
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("btn_create_new_profile"),
      colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary
      )
    ) {
      Icon(
        imageVector = Icons.Default.PersonAdd,
        contentDescription = null,
        modifier = Modifier.size(18.dp)
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = stringResource(R.string.create_new_profile),
        fontWeight = FontWeight.SemiBold
      )
    }

    Spacer(modifier = Modifier.height(14.dp))

    Text(
      text = "Perfis Salvos (${profiles.size})",
      fontWeight = FontWeight.Bold,
      fontSize = 14.5.sp,
      color = MaterialTheme.colorScheme.onSurface
    )

    Spacer(modifier = Modifier.height(8.dp))

    // Lista de Perfis
    LazyColumn(
      modifier = Modifier
        .fillMaxWidth()
        .height(300.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
      contentPadding = PaddingValues(bottom = 16.dp)
    ) {
      items(profiles, key = { it.id }) { profile ->
        val isActive = profile.id == currentProfile.id

        Card(
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
              MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
              MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
          ),
          border = if (isActive) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
          } else null,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_card_${profile.id}")
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(
                  text = profile.name,
                  fontWeight = FontWeight.Bold,
                  fontSize = 15.sp,
                  color = MaterialTheme.colorScheme.onSurface
                )
                if (isActive) {
                  Spacer(modifier = Modifier.width(8.dp))
                  Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                      Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp)
                      )
                      Spacer(modifier = Modifier.width(3.dp))
                      Text(
                        text = "Ativo",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                      )
                    }
                  }
                }
              }

              if (!profile.isDefault && profiles.size > 1 && !isActive) {
                IconButton(
                  onClick = { profileToDelete = profile },
                  modifier = Modifier
                    .size(28.dp)
                    .testTag("btn_delete_profile_${profile.id}")
                ) {
                  Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Excluir perfil",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Informação da história
            if (profile.customStory != null) {
              Text(
                text = "📖 História: ${profile.storyTitle}",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.primary
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = profile.customStory,
                fontSize = 11.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            } else {
              Text(
                text = "📖 Especialista em A Ordem dos Sem Olhos (Padrão)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Criado em: ${dateFormat.format(Date(profile.createdAt))}",
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.outline
              )

              if (!isActive) {
                OutlinedButton(
                  onClick = { onSwitchProfile(profile.id) },
                  shape = RoundedCornerShape(8.dp),
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                  modifier = Modifier
                    .height(30.dp)
                    .testTag("btn_select_profile_${profile.id}")
                ) {
                  Text(
                    text = "Selecionar",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  // Diálogo para criação de novo perfil
  if (showCreateDialog) {
    AlertDialog(
      onDismissRequest = { showCreateDialog = false },
      title = {
        Text(
          text = stringResource(R.string.dialog_new_profile_title),
          fontWeight = FontWeight.Bold
        )
      },
      text = {
        Column {
          Text(
            text = "Dê um nome para este perfil (ex: Campanha 2, Fantasia Medieval, Linha do Tempo B):",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedTextField(
            value = newProfileName,
            onValueChange = { newProfileName = it },
            placeholder = { Text("Nome do perfil...") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("input_new_profile_name")
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (newProfileName.isNotBlank()) {
              onCreateProfile(newProfileName.trim())
              showCreateDialog = false
            }
          },
          enabled = newProfileName.isNotBlank(),
          modifier = Modifier.testTag("btn_confirm_create_profile")
        ) {
          Text("Criar Perfil")
        }
      },
      dismissButton = {
        TextButton(onClick = { showCreateDialog = false }) {
          Text("Cancelar")
        }
      }
    )
  }

  // Diálogo para confirmação de exclusão
  profileToDelete?.let { prof ->
    AlertDialog(
      onDismissRequest = { profileToDelete = null },
      title = { Text("Excluir Perfil?") },
      text = {
        Text("Tem certeza que deseja excluir o perfil \"${prof.name}\"? Todas as mensagens e a história deste perfil serão apagadas.")
      },
      confirmButton = {
        Button(
          onClick = {
            onDeleteProfile(prof.id)
            profileToDelete = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          modifier = Modifier.testTag("btn_confirm_delete_profile")
        ) {
          Text("Excluir")
        }
      },
      dismissButton = {
        TextButton(onClick = { profileToDelete = null }) {
          Text("Cancelar")
        }
      }
    )
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}
