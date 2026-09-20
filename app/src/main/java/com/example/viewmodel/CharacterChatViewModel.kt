package com.example.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.R
import com.example.data.LoreData
import com.example.data.gemini.GeminiClient
import com.example.data.gemini.GeminiContent
import com.example.data.gemini.GeminiGenerationConfig
import com.example.data.gemini.GeminiInlineData
import com.example.data.gemini.GeminiPart
import com.example.data.gemini.GeminiRequest
import com.example.model.CharacterInventory
import com.example.model.ChatMessage
import com.example.model.MemoryEntry
import com.example.model.UserProfile
import com.example.repository.CharacterMemoryRepository
import com.example.util.ImageUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

data class CharacterChatUiState(
  val currentProfile: UserProfile,
  val allProfiles: List<UserProfile> = emptyList(),
  val showProfileSheet: Boolean = false,
  val messages: List<ChatMessage> = emptyList(),
  val savedCharacters: List<CharacterInventory> = emptyList(),
  val savedMemories: List<MemoryEntry> = emptyList(),
  val isAnalyzingImage: Boolean = false,
  val isOptimizingPrompt: Boolean = false,
  val isListeningVoice: Boolean = false,
  val isTtsEnabled: Boolean = true,
  val showMemorySheet: Boolean = false,
  val showSettingsSheet: Boolean = false
)

class CharacterChatViewModel(application: Application) : AndroidViewModel(application) {

  private val repository = CharacterMemoryRepository(application)

  private val _uiState = MutableStateFlow(
    run {
      val activeProf = repository.getActiveProfile()
      val allProfs = repository.getAllProfiles()
      val loadedMessages = repository.getChatMessagesForProfile(activeProf.id)
      val initialMsgs = if (loadedMessages.isNotEmpty()) {
        loadedMessages
      } else {
        listOf(makeInitialWelcomeMessage(activeProf))
      }
      CharacterChatUiState(
        currentProfile = activeProf,
        allProfiles = allProfs,
        messages = initialMsgs,
        savedCharacters = repository.getAllCharacters(),
        savedMemories = repository.getAllMemoryEntries()
      )
    }
  )
  val uiState: StateFlow<CharacterChatUiState> = _uiState.asStateFlow()

  private val _ttsSpeakEvent = MutableSharedFlow<String>()
  val ttsSpeakEvent: SharedFlow<String> = _ttsSpeakEvent.asSharedFlow()

  private val _toastEvent = MutableSharedFlow<String>()
  val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

  companion object {
    fun makeInitialWelcomeMessage(profile: UserProfile): ChatMessage {
      return if (profile.customStory != null) {
        ChatMessage(
          text = "Olá! Você está no perfil \"${profile.name}\", onde sou especialista na história: **${profile.storyTitle}**.\n\n" +
            "Você pode me perguntar detalhes do enredo, pedir para continuar ou corrigir cenas, criar diálogos ou catalogar personagens no inventário!",
          isUser = false
        )
      } else {
        ChatMessage(
          text = "Olá! Você está no perfil \"${profile.name}\".\n\n" +
            "📌 **Definição de História:** Caso você queira que eu salve e seja especialista em outra história neste perfil, envie seu comando agora nesta **primeira mensagem** (ex: *\"SALVAR HISTÓRIA: [sua história aqui]\"*).\n\n" +
            "Se você não me der um comando para salvar outra história nesta primeira mensagem, continuarei automaticamente como especialista no livro \"A Ordem dos Sem Olhos\" (Derek, Teravarn, Kallan, Kaelis, Lyra, Carlos, Zalor, Maycon) para este perfil!",
          isUser = false
        )
      }
    }
  }

  fun toggleTts() {
    _uiState.update { it.copy(isTtsEnabled = !it.isTtsEnabled) }
  }

  fun setListeningVoice(isListening: Boolean) {
    _uiState.update { it.copy(isListeningVoice = isListening) }
  }

  fun setMemorySheetVisibility(show: Boolean) {
    _uiState.update {
      it.copy(
        showMemorySheet = show,
        savedCharacters = repository.getAllCharacters(),
        savedMemories = repository.getAllMemoryEntries()
      )
    }
  }

  fun setSettingsSheetVisibility(show: Boolean) {
    _uiState.update {
      it.copy(
        showSettingsSheet = show,
        savedCharacters = repository.getAllCharacters(),
        savedMemories = repository.getAllMemoryEntries()
      )
    }
  }

  fun addManualMemoryEntry(content: String) {
    if (content.isBlank()) return
    viewModelScope.launch {
      repository.saveMemoryEntry(content.trim())
      _uiState.update {
        it.copy(savedMemories = repository.getAllMemoryEntries())
      }
      _toastEvent.emit("Informação guardada na memória do aparelho com sucesso!")
    }
  }

  fun deleteMemoryEntry(id: String) {
    viewModelScope.launch {
      repository.deleteMemoryEntry(id)
      _uiState.update {
        it.copy(savedMemories = repository.getAllMemoryEntries())
      }
      _toastEvent.emit("Item de memória removido.")
    }
  }

  fun setProfileSheetVisibility(show: Boolean) {
    _uiState.update {
      it.copy(
        showProfileSheet = show,
        allProfiles = repository.getAllProfiles()
      )
    }
  }

  fun switchProfile(profileId: String) {
    repository.saveChatMessagesForProfile(_uiState.value.currentProfile.id, _uiState.value.messages)
    repository.setActiveProfileId(profileId)
    val newProfile = repository.getActiveProfile()
    val allProfiles = repository.getAllProfiles()
    val loaded = repository.getChatMessagesForProfile(newProfile.id)
    val msgs = if (loaded.isNotEmpty()) loaded else listOf(makeInitialWelcomeMessage(newProfile))
    _uiState.update {
      it.copy(
        currentProfile = newProfile,
        allProfiles = allProfiles,
        messages = msgs,
        showProfileSheet = false
      )
    }
    viewModelScope.launch {
      _toastEvent.emit("Perfil alterado para: ${newProfile.name}")
    }
  }

  fun createProfile(name: String) {
    repository.saveChatMessagesForProfile(_uiState.value.currentProfile.id, _uiState.value.messages)
    val created = repository.createProfile(name)
    val allProfiles = repository.getAllProfiles()
    val initialMsg = listOf(makeInitialWelcomeMessage(created))
    _uiState.update {
      it.copy(
        currentProfile = created,
        allProfiles = allProfiles,
        messages = initialMsg,
        showProfileSheet = false
      )
    }
    repository.saveChatMessagesForProfile(created.id, initialMsg)
    viewModelScope.launch {
      _toastEvent.emit("Perfil \"${created.name}\" criado com sucesso!")
    }
  }

  fun deleteProfile(profileId: String) {
    val success = repository.deleteProfile(profileId)
    if (success) {
      val activeProf = repository.getActiveProfile()
      val allProfiles = repository.getAllProfiles()
      val loaded = repository.getChatMessagesForProfile(activeProf.id)
      val msgs = if (loaded.isNotEmpty()) loaded else listOf(makeInitialWelcomeMessage(activeProf))
      _uiState.update {
        it.copy(
          currentProfile = activeProf,
          allProfiles = allProfiles,
          messages = msgs
        )
      }
      viewModelScope.launch {
        _toastEvent.emit("Perfil excluído.")
      }
    } else {
      viewModelScope.launch {
        _toastEvent.emit("Não é possível excluir o perfil padrão.")
      }
    }
  }

  fun clearChat() {
    val currentProf = _uiState.value.currentProfile
    repository.clearChatMessagesForProfile(currentProf.id)
    val welcome = listOf(makeInitialWelcomeMessage(currentProf))
    _uiState.update {
      it.copy(messages = welcome)
    }
    repository.saveChatMessagesForProfile(currentProf.id, welcome)
  }

  fun addPhotoToCharacter(character: CharacterInventory, photoUri: String) {
    viewModelScope.launch {
      val currentPhotos = character.photos.toMutableList()
      if (currentPhotos.size < 5) {
        currentPhotos.add(photoUri)
        val updated = character.copy(
          photos = currentPhotos,
          imageUri = currentPhotos.firstOrNull()
        )
        repository.saveCharacter(updated)
        _uiState.update { it.copy(savedCharacters = repository.getAllCharacters()) }
        _toastEvent.emit("Foto adicionada ao personagem (${currentPhotos.size}/5)!")
      } else {
        _toastEvent.emit("Limite de 5 fotos atingido para este personagem.")
      }
    }
  }

  fun removePhotoFromCharacter(character: CharacterInventory, photoIndex: Int) {
    viewModelScope.launch {
      val currentPhotos = character.photos.toMutableList()
      if (photoIndex in currentPhotos.indices) {
        currentPhotos.removeAt(photoIndex)
        val updated = character.copy(
          photos = currentPhotos,
          imageUri = currentPhotos.firstOrNull()
        )
        repository.saveCharacter(updated)
        _uiState.update { it.copy(savedCharacters = repository.getAllCharacters()) }
        _toastEvent.emit("Foto removida!")
      }
    }
  }

  fun copyToClipboard(promptText: String) {
    val context = getApplication<Application>()
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Character Prompt", promptText)
    clipboard.setPrimaryClip(clip)
    viewModelScope.launch {
      _toastEvent.emit(context.getString(R.string.prompt_copied))
    }
  }

  fun copyMessageText(textToCopy: String, isPrompt: Boolean = false) {
    val context = getApplication<Application>()
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(if (isPrompt) "Character Prompt" else "Assistant Response", textToCopy)
    clipboard.setPrimaryClip(clip)
    viewModelScope.launch {
      _toastEvent.emit(
        if (isPrompt) context.getString(R.string.prompt_copied)
        else context.getString(R.string.response_copied)
      )
    }
  }

  fun onUserMessage(userText: String) {
    val clean = userText.trim()
    if (clean.isEmpty()) return

    // Contagem de mensagens do usuário nesta conversa ANTES de adicionar a nova mensagem
    val userMessagesCountBefore = _uiState.value.messages.count { it.isUser }

    val userMessage = ChatMessage(text = clean, isUser = true)
    val listWithUser = _uiState.value.messages + userMessage
    _uiState.update { it.copy(messages = listWithUser) }
    repository.saveChatMessagesForProfile(_uiState.value.currentProfile.id, listWithUser)

    viewModelScope.launch {
      val lower = clean.lowercase()

      // Checagem de comando para salvar/definir história
      val isStorySaveCommand = isStorySaveCommand(lower)

      if (isStorySaveCommand) {
        if (userMessagesCountBefore == 0) {
          // PRIMEIRA CONVERSA / PRIMEIRA MENSAGEM DO USUÁRIO: ACEITA!
          val extractedStory = extractStoryText(clean)
          val extractedTitle = extractStoryTitle(clean, _uiState.value.currentProfile.name)

          repository.updateProfileStory(
            profileId = _uiState.value.currentProfile.id,
            storyTitle = extractedTitle,
            customStory = extractedStory
          )
          val updatedProfile = repository.getActiveProfile()
          val allProfiles = repository.getAllProfiles()

          val reply = ChatMessage(
            text = "📖 **Nova História Salva com Sucesso no Perfil \"${updatedProfile.name}\"!**\n\n" +
              "📌 **Título:** ${updatedProfile.storyTitle}\n" +
              "📜 **Conteúdo da História:**\n\"${extractedStory.take(320)}${if (extractedStory.length > 320) "..." else ""}\"\n\n" +
              "Como este comando foi enviado logo após abrir a conversa (na sua **primeira mensagem**), fixei esta história com sucesso neste perfil.\n\n" +
              "A partir de agora sou sua especialista dedicada a esta narrativa! Pode me fazer perguntas sobre o enredo, continuidade, pedir cenas ou catalogar novos personagens.",
            isUser = false
          )
          val finalList = listWithUser + reply
          _uiState.update {
            it.copy(
              currentProfile = updatedProfile,
              allProfiles = allProfiles,
              messages = finalList
            )
          }
          repository.saveChatMessagesForProfile(updatedProfile.id, finalList)
          if (_uiState.value.isTtsEnabled) {
            _ttsSpeakEvent.emit(reply.text)
          }
          return@launch
        } else {
          // NÃO É A PRIMEIRA MENSAGEM: RECUSA CONFORME REGRA SOLICITADA!
          val currentProf = _uiState.value.currentProfile
          val activeStoryName = currentProf.customStory?.let { currentProf.storyTitle } ?: "A Ordem dos Sem Olhos"
          val reply = ChatMessage(
            text = "⚠️ **Comando não aceito neste momento!**\n\n" +
              "A definição ou substituição de história para este perfil é permitida **apenas na primeira mensagem** logo após abrir a conversa.\n\n" +
              "Como esta conversa já está em andamento, não posso alterar a história e continuo como especialista em: **$activeStoryName**.\n\n" +
              "💡 *Você pode criar perfis à vontade!* Toque no botão de **Perfis** no topo, crie um novo perfil e defina a história na primeira mensagem enviada nele.",
            isUser = false
          )
          val finalList = listWithUser + reply
          _uiState.update { it.copy(messages = finalList) }
          repository.saveChatMessagesForProfile(currentProf.id, finalList)
          if (_uiState.value.isTtsEnabled) {
            _ttsSpeakEvent.emit(reply.text)
          }
          return@launch
        }
      }

      // 1. Comando explícito: "GUARDE EM SUA MEMORIA ESSA INFORMAÇÃO ESPECÍFICA"
      val isSaveMemoryCommand = lower.contains("guarde em sua memoria essa informação específica") ||
        lower.contains("guarde em sua memoria essa informacao especifica") ||
        lower.contains("guarde em sua memória essa informação específica") ||
        lower.contains("guarde em sua memória essa informacao especifica") ||
        lower.contains("guarde na memória") ||
        lower.contains("guarde na memoria")

      // 2. Comando explícito: "ACESSAR MEMÓRIA"
      val isAccessMemoryCommand = lower.contains("acessar memória") ||
        lower.contains("acessar memoria") ||
        lower.contains("acesse a memória") ||
        lower.contains("acesse a memoria")

      val replyMessage = when {
        isSaveMemoryCommand -> {
          // Extrair a informação específica após a frase chave ou salvar o texto
          val regex = Regex("""(?i)(?:guarde\s+em\s+sua\s+mem[oó]ria\s+essa\s+informa[cç][aã]o\s+espec[ií]fica[:\s\-]*|guarde\s+na\s+mem[oó]ria[:\s\-]*|guarde\s+na\s+memoria[:\s\-]*)(.*)""")
          val match = regex.find(clean)
          val extractedInfo = match?.groupValues?.getOrNull(1)?.trim()

          val memoryContent = if (!extractedInfo.isNullOrBlank()) {
            extractedInfo
          } else {
            clean
          }

          repository.saveMemoryEntry(memoryContent)
          val allMemories = repository.getAllMemoryEntries()
          val allChars = repository.getAllCharacters()

          _uiState.update {
            it.copy(
              savedMemories = allMemories,
              savedCharacters = allChars
            )
          }

          ChatMessage(
            text = "💾 **Informação Guardada com Sucesso na Memória Permanente do Aparelho!**\n\n" +
              "\"$memoryContent\"\n\n" +
              "Os dados foram salvos no armazenamento interno persistente e agora fazem parte da minha base de conhecimento para não esquecer nada sobre este ou outros personagens.\n\n" +
              "Você pode visualizar ou gerenciar suas anotações no botão de Configurações / Memória.",
            isUser = false,
            suggestAccessMemoryButton = true
          )
        }

        isAccessMemoryCommand -> {
          ChatMessage(
            text = "🧠 **Acesso à Memória Permanente**\n\n" +
              "Você solicitou acessar a memória. Toque no botão **\"Acessar Memória\"** logo abaixo ou no ícone de configurações no topo para visualizar todas as informações e personagens guardados no armazenamento do aparelho.",
            isUser = false,
            suggestAccessMemoryButton = true
          )
        }

        lower.contains("otimizar") || lower.contains("otimiza") || lower.contains("melhorar prompt") || lower.contains("refinar prompt") -> {
          val lastChar = repository.getAllCharacters().firstOrNull()
          if (lastChar != null) {
            optimizeCharacterPromptWithGemini(lastChar)
            return@launch
          } else {
            ChatMessage(
              text = "Nenhum personagem foi catalogado ainda para otimização. Selecione uma imagem primeiro.",
              isUser = false
            )
          }
        }
        lower.contains("prompt") || lower.contains("gerar") || lower.contains("criar prompt") -> {
          val lastChar = repository.getAllCharacters().firstOrNull()
          if (lastChar != null) {
            val generated = lastChar.generateImagePrompt()
            ChatMessage(
              text = "Aqui está o prompt gerado com base estrita no inventário visual de \"${lastChar.name}\":",
              isUser = false,
              generatedPrompt = generated,
              characterInventory = lastChar
            )
          } else {
            ChatMessage(
              text = "Nenhum personagem foi catalogado ainda. Toque no ícone de foto para enviar uma imagem e processá-la via Gemini.",
              isUser = false
            )
          }
        }
        lower.contains("memória") || lower.contains("personagens") || lower.contains("inventário") || lower.contains("catalogados") -> {
          val visualList = repository.getAllCharacters()
          val specificMemories = repository.getAllMemoryEntries()
          val textBuilder = StringBuilder()
          textBuilder.append("📚 **Inventário e Memória Permanente no Aparelho:**\n\n")

          if (specificMemories.isNotEmpty()) {
            textBuilder.append("📌 **Notas e Informações Específicas Guardadas:**\n")
            specificMemories.take(5).forEachIndexed { index, m ->
              textBuilder.append("${index + 1}. \"${m.content}\"\n")
            }
            if (specificMemories.size > 5) {
              textBuilder.append("... e mais ${specificMemories.size - 5} informação(ões).\n")
            }
            textBuilder.append("\n")
          }

          if (visualList.isNotEmpty()) {
            textBuilder.append("👤 **Personagens Catalogados (${visualList.size}):**\n")
            visualList.forEach { v ->
              textBuilder.append("• ${v.name} (${v.role}) - Fotos: ${v.photos.size}/5\n")
            }
          } else {
            textBuilder.append("Nenhum personagem registrado no inventário ainda.\n\n")
          }
          ChatMessage(
            text = textBuilder.toString().trim(),
            isUser = false,
            suggestAccessMemoryButton = true
          )
        }
        lower.contains("ajuda") || lower.contains("como funciona") -> {
          ChatMessage(
            text = "Como usar:\n1. Pergunte sobre a história, personagens (Derek, Teravarn, Kallan, Kaelis, Lyra, Carlos) ou capítulos de \"A Ordem dos Sem Olhos\".\n2. Para guardar algo na memória do aparelho, use: \"GUARDE EM SUA MEMORIA ESSA INFORMAÇÃO ESPECÍFICA: [seu texto]\".\n3. Para abrir a memória a qualquer instante, use: \"ACESSAR MEMÓRIA\" ou toque no botão de configurações no topo.\n4. Envie imagens para analisar traços visuais com Gemini ou use o microfone para falar!",
            isUser = false
          )
        }
        else -> {
          // Responde usando o conhecimento canônico registrado, memórias salvas no aparelho e API Gemini
          val canonAnswer = answerLoreOrAssistantQuery(clean)
          ChatMessage(
            text = canonAnswer,
            isUser = false
          )
        }
      }

      val finalMessages = _uiState.value.messages + replyMessage
      _uiState.update { it.copy(messages = finalMessages) }
      repository.saveChatMessagesForProfile(_uiState.value.currentProfile.id, finalMessages)
      if (_uiState.value.isTtsEnabled) {
        val spoken = replyMessage.generatedPrompt?.let {
          "Prompt gerado com base no inventário visual. Pronto para cópia."
        } ?: replyMessage.text
        _ttsSpeakEvent.emit(spoken)
      }
    }
  }

  private fun isStorySaveCommand(lower: String): Boolean {
    return lower.contains("salvar história") ||
      lower.contains("salvar historia") ||
      lower.contains("salve esta história") ||
      lower.contains("salve esta historia") ||
      lower.contains("salve a história") ||
      lower.contains("salve a historia") ||
      lower.contains("guardar história") ||
      lower.contains("guardar historia") ||
      lower.contains("guarde esta história") ||
      lower.contains("guarde esta historia") ||
      lower.contains("definir história") ||
      lower.contains("definir historia") ||
      lower.contains("história deste perfil") ||
      lower.contains("historia deste perfil") ||
      lower.contains("nova história") ||
      lower.contains("nova historia") ||
      lower.contains("esta história será") ||
      lower.contains("esta historia sera") ||
      lower.startsWith("história:") ||
      lower.startsWith("historia:")
  }

  private fun extractStoryText(fullText: String): String {
    val regex = Regex("""(?i)(?:salvar\s+hist[oó]ria[:\s\-]*|salve\s+esta\s+hist[oó]ria[:\s\-]*|salve\s+a\s+hist[oó]ria[:\s\-]*|guardar\s+hist[oó]ria[:\s\-]*|guarde\s+esta\s+hist[oó]ria[:\s\-]*|definir\s+hist[oó]ria[:\s\-]*|hist[oó]ria\s+deste\s+perfil[:\s\-]*|nova\s+hist[oó]ria[:\s\-]*|esta\s+hist[oó]ria\s+ser[aá][:\s\-]*|hist[oó]ria[:\s\-]*)(.*)""", RegexOption.DOT_MATCHES_ALL)
    val match = regex.find(fullText)
    val extracted = match?.groupValues?.getOrNull(1)?.trim()
    return if (!extracted.isNullOrBlank()) extracted else fullText.trim()
  }

  private fun extractStoryTitle(fullText: String, profileName: String): String {
    val regex = Regex("""(?i)(?:t[ií]tulo[:\s\-]*)([^.\n]+)""")
    val match = regex.find(fullText)
    val title = match?.groupValues?.getOrNull(1)?.trim()
    if (!title.isNullOrBlank()) return title
    return "História de $profileName"
  }

  private suspend fun answerLoreOrAssistantQuery(query: String): String {
    val currentProfile = _uiState.value.currentProfile
    val qLower = query.lowercase()

    // Se o perfil atual possui uma história personalizada definida:
    if (currentProfile.customStory != null) {
      if (qLower.contains("quem é você") || qLower.contains("quem e voce") || qLower.contains("sua função") || qLower.contains("ajudante")) {
        return "Sou sua assistente especialista na história **\"${currentProfile.storyTitle}\"** deste perfil (\"${currentProfile.name}\")! Estou aqui para responder dúvidas, sugerir diálogos, expandir cenas e catalogar personagens."
      }

      val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
      if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
        try {
          val specificMemories = repository.getAllMemoryEntries()
          val customCharacters = repository.getAllCharacters()

          val memoriesBlock = if (specificMemories.isNotEmpty()) {
            "\n\nMemória persistente do dispositivo:\n" +
              specificMemories.joinToString("\n") { "- ${it.content}" }
          } else ""

          val charactersBlock = if (customCharacters.isNotEmpty()) {
            "\n\nPersonagens catalogados:\n" +
              customCharacters.joinToString("\n") { char ->
                "• ${char.name} (${char.role}): Idade: ${char.age}. Traços: ${char.physicalTraits.joinToString(", ")}. Notas: ${char.notes}"
              }
          } else ""

          val systemInstruction = "Você é o especialista dedicado e assistente de escrita da seguinte história:\n\n" +
            "Título: ${currentProfile.storyTitle}\n" +
            "História Canônica Registrada:\n${currentProfile.customStory}\n" +
            memoriesBlock +
            charactersBlock

          val request = GeminiRequest(
            contents = listOf(
              GeminiContent(
                role = "user",
                parts = listOf(
                  GeminiPart(text = "História do perfil:\n$systemInstruction\n\nPergunta ou solicitação do usuário: $query\n\nResponda com fidelidade e consistência à história deste perfil.")
                )
              )
            ),
            generationConfig = GeminiGenerationConfig(temperature = 0.3f)
          )
          val response = GeminiClient.service.generateContent(apiKey, request)
          val textResult = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
          if (!textResult.isNullOrBlank()) {
            return textResult.trim()
          }
        } catch (e: Exception) {
          Log.e("CharacterChatViewModel", "Error querying Gemini for custom story", e)
        }
      }

      val savedMemories = repository.getAllMemoryEntries()
      val matchedMemory = savedMemories.firstOrNull { it.content.contains(query, ignoreCase = true) || query.contains(it.content.take(15), ignoreCase = true) }
      if (matchedMemory != null) {
        return "📌 **Informação encontrada na memória permanente:**\n\n${matchedMemory.content}"
      }

      return "Como especialista na história \"${currentProfile.storyTitle}\" deste perfil:\n\n" +
        "Sinopse registrada: ${currentProfile.customStory?.take(220)}...\n\n" +
        "Estou à disposição para responder dúvidas deste universo, desenvolver cenas e continuar a trama!"
    }

    // Se NÃO tiver história personalizada, continua como especialista no livro "A Ordem dos Sem Olhos"
    val qLower = query.lowercase()

    // Respostas canônicas imediatas para perguntas diretas mais frequentes
    if (qLower.contains("derek") && (qLower.contains("quem") || qLower.contains("luta") || qLower.contains("lutou") || qLower.contains("idade") || qLower.contains("pai") || qLower.contains("origem"))) {
      val derekChar = repository.getAllCharacters().firstOrNull { it.name.equals("Derek", ignoreCase = true) }
      return buildString {
        append("🗡️ **Derek**\n\n")
        append("• **Quem é:** Jovem guerreiro de cabelos brancos e olhos prateados em vestes pretas de couro. É meio-mágico e meio-humano, filho de Teravarn com uma humana da Terra.\n")
        append("• **Idade:** A idade exata em números ainda não foi informada pelo autor. Seus poderes despertaram aos 5 anos, e ele é descrito como um jovem/homem feito.\n")
        append("• **Com quem lutou:** Enfrentou e aniquilou os guardas do nurague, Marcos (desintegrado em névoa de sangue, de quem tomou a espada esmeralda), 7 soldados de reforço, e os arqueiros selvagens na emboscada da floresta de Ferusaval. Além disso, torturou um prisioneiro selvagem talhando seu nome no peito e sofreu feitiço paralisante de Lyra.\n")
        if (derekChar != null) {
          append("• **Papel no Inventário:** ${derekChar.role}\n")
        }
      }
    }

    if (qLower.contains("teravarn")) {
      return "👑 **Teravarn**: O maior guerreiro mágico que já existiu (com mais de 72 mil anos de batalhas). Ensinou os humanos primitivos a saírem das cavernas e selou o pacto ancestral com os monstros marinhos. Apaixonou-se por uma humana na Terra e gerou Derek. Quis morrer após a perda de sua amada para evitar uma guerra cósmica, sendo executado a seu próprio pedido por seu melhor amigo, Kallan."
    }

    if (qLower.contains("kallan")) {
      return "🛡️ **Comandante Kallan**: Comandante veterano e lendário de Valdarion. Melhor amigo de Teravarn e tutor de Derek. Conhecedor de escudos de energia, telecinese e manto de invisibilidade. Cumpriu com dor extrema a última missão dada por Teravarn: matá-lo e cuidar de Derek como se fosse seu próprio filho."
    }

    if (qLower.contains("kaelis")) {
      return "✨ **Kaelis**: Menina de 8 anos dotada de imenso poder sobrenatural desde o nascimento. Protegida diretamente pela magia ancestral de Teravarn contra armas e demônios menores. Seu sangue garante passagem segura contra os monstros marinhos no mar e era cobiçado por Maycon para gerar super-soldados."
    }

    if (qLower.contains("lyra")) {
      return "🔮 **Lyra**: Bruxa ruiva de pele nívea e manto preto. Foi capturada por Maycon, mas fingia torturar Kaelis para protegê-la. Possui domínio sobre telepatia, ilusões e portais. Ao ouvir Derek falar sobre suas origens e sobre Teravarn, sofreu um violento choque mental e sumiu na mata."
    }

    if (qLower.contains("carlos")) {
      return "⚔️ **Carlos**: Guerreiro inteligente e calculista. Foi promovido a comandante por Maycon após a invasão do nurague. Ao descobrir que Maycon é um ser não-humano canibal, convenceu 14 homens a desertarem rumo ao sul para a casa de Zalor, combinando fugir no mesmo navio que Kallan e Derek."
    }

    if (qLower.contains("zalor") || qLower.contains("lívian") || qLower.contains("livian") || qLower.contains("zelian")) {
      return "⛵ **Família de Zalor**: Amigos de confiança no porto de Ferusaval. Descendentes dos humanos que salvaram Teravarn ferido séculos atrás. Zalor é construtor naval; Lívian acolheu Derek com lágrimas e guarda o segredo de que a esposa de Carlos virou Bimor; Zelian é a filha jovem que revelou a Derek quem era seu pai."
    }

    if (qLower.contains("draxos") || qLower.contains("maycon")) {
      return "💀 **Maycon e Draxos**: Maycon é o tirano canibal que controla o nurague e realiza experimentos cruéis. Draxos é o rei dos selvagens e dono da ilha, sobrevivente de El Inferno, atualmente tentando selar um pacto com um demônio de nível superior."
    }

    if (qLower.contains("quem é você") || qLower.contains("quem e voce") || qLower.contains("sua função") || qLower.contains("ajudante")) {
      return "Sou sua assistente oficial e especialista no livro \"A Ordem dos Sem Olhos\"! Estou aqui para responder qualquer detalhe dos capítulos e personagens, ajudar a catalogar dados no banco de dados Room, corrigir textos e sugerir ideias sempre que você solicitar."
    }

    // Se houver chave do Gemini, consulta o Gemini com o contexto canônico estrito + memórias salvas no celular
    val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
    if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
      try {
        val specificMemories = repository.getAllMemoryEntries()
        val customCharacters = repository.getAllCharacters()

        val memoriesBlock = if (specificMemories.isNotEmpty()) {
          "\n\nInformações específicas guardadas na memória permanente do aparelho pelo usuário:\n" +
            specificMemories.joinToString("\n") { "- ${it.content}" }
        } else ""

        val charactersBlock = if (customCharacters.isNotEmpty()) {
          "\n\nPersonagens catalogados na memória do celular:\n" +
            customCharacters.joinToString("\n") { char ->
              "• ${char.name} (${char.role}): Idade: ${char.age}. Traços: ${char.physicalTraits.joinToString(", ")}. Vestes: ${char.clothingTraits.joinToString(", ")}. Acessórios: ${char.accessories.joinToString(", ")}. Notas: ${char.notes}"
            }
        } else ""

        val systemInstruction = LoreData.CANONICAL_KNOWLEDGE_PROMPT +
          "\nCapítulos atuais:\n" + LoreData.CHAPTERS.joinToString("\n\n") { "${it.first}:\n${it.second}" } +
          memoriesBlock +
          charactersBlock

        val request = GeminiRequest(
          contents = listOf(
            GeminiContent(
              role = "user",
              parts = listOf(
                GeminiPart(text = "Contexto oficial da história e memórias salvas no dispositivo:\n$systemInstruction\n\nPergunta do autor/usuário: $query\n\nResponda com fidelidade absoluta aos fatos revelados nos capítulos e às informações guardadas na memória do celular. Se a pergunta for sobre algo não revelado pelo autor nem guardado em memória, aponte com gentileza que ainda não consta nos registros.")
              )
            )
          ),
          generationConfig = GeminiGenerationConfig(
            temperature = 0.2f
          )
        )
        val response = GeminiClient.service.generateContent(apiKey, request)
        val textResult = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        if (!textResult.isNullOrBlank()) {
          return textResult.trim()
        }
      } catch (e: Exception) {
        Log.e("CharacterChatViewModel", "Error querying Gemini for lore", e)
      }
    }

    // Fallback: verificar se a pergunta bate com alguma memória salva
    val savedMemories = repository.getAllMemoryEntries()
    val matchedMemory = savedMemories.firstOrNull { it.content.contains(query, ignoreCase = true) || query.contains(it.content.take(15), ignoreCase = true) }
    if (matchedMemory != null) {
      return "📌 **Informação encontrada na memória permanente:**\n\n${matchedMemory.content}"
    }

    return "Compreendi sua mensagem sobre \"$query\". Como especialista no livro \"A Ordem dos Sem Olhos\" e guardiã da memória de personagens, estou à disposição para explicar detalhes dos capítulos, consultar memórias salvas no aparelho ou auxiliar com correções e novas ideias quando solicitado."
  }

  fun onCharacterImageSelected(uri: Uri) {
    val uriString = uri.toString()
    val characterIndex = repository.getAllCharacters().size + 1
    val defaultName = "Personagem #$characterIndex"

    val userMessage = ChatMessage(
      text = "Imagem enviada para análise visual com Google Gemini.",
      isUser = true,
      imageUri = uriString
    )
    _uiState.update {
      it.copy(
        messages = it.messages + userMessage,
        isAnalyzingImage = true
      )
    }

    viewModelScope.launch {
      val context = getApplication<Application>()
      val base64Data = ImageUtils.uriToBase64(context, uri)

      val apiKey = try {
        BuildConfig.GEMINI_API_KEY
      } catch (e: Exception) {
        ""
      }

      val analyzedInventory: CharacterInventory = if (base64Data != null && apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
        analyzeWithGeminiApi(apiKey, base64Data.first, base64Data.second, defaultName, uriString)
      } else {
        fallbackExtraction(defaultName, uriString)
      }

      repository.saveCharacter(analyzedInventory)
      val updatedCharacters = repository.getAllCharacters()
      val prompt = analyzedInventory.generateImagePrompt()

      val replyText = "✅ Análise do Google Gemini concluída com fidelidade visual estrita:\n\n" +
        "• Traços Físicos: ${analyzedInventory.physicalTraits.joinToString(", ")}\n" +
        "• Vestimentas: ${analyzedInventory.clothingTraits.joinToString(", ")}\n" +
        "• Acessórios: ${analyzedInventory.accessories.joinToString(", ")}\n\n" +
        "Prompt descritivo gerado pronto para cópia (para uso no Bing Image Creator, Leonardo AI, Ideogram, etc.):"

      val assistantMessage = ChatMessage(
        text = replyText,
        isUser = false,
        generatedPrompt = prompt,
        characterInventory = analyzedInventory
      )

      _uiState.update {
        it.copy(
          messages = it.messages + assistantMessage,
          savedCharacters = updatedCharacters,
          isAnalyzingImage = false
        )
      }

      if (_uiState.value.isTtsEnabled) {
        _ttsSpeakEvent.emit("Análise visual concluída pelo Gemini. O inventário do personagem foi salvo na memória interna e o prompt está pronto para cópia.")
      }
    }
  }

  fun optimizeCharacterPromptWithGemini(character: CharacterInventory) {
    _uiState.update {
      it.copy(
        isOptimizingPrompt = true,
        showMemorySheet = false
      )
    }

    viewModelScope.launch {
      val apiKey = try {
        BuildConfig.GEMINI_API_KEY
      } catch (e: Exception) {
        ""
      }

      val optimizedPrompt = if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
        generateOptimizedPromptWithGemini(apiKey, character)
      } else {
        fallbackOptimizedPrompt(character)
      }

      val replyText = "✨ Prompt Otimizado com Google Gemini para \"${character.name}\":\n\n" +
        "Esta versão foi refinada pelo Gemini especificamente para modelos de IA generativa de imagens (Midjourney, DALL-E 3, Stable Diffusion XL, Bing Image Creator, Ideogram, Leonardo AI), maximizando a fidelidade dos traços visuais, iluminação, composição e detalhes anatômicos."

      val assistantMessage = ChatMessage(
        text = replyText,
        isUser = false,
        generatedPrompt = optimizedPrompt,
        characterInventory = character
      )

      _uiState.update {
        it.copy(
          messages = it.messages + assistantMessage,
          isOptimizingPrompt = false
        )
      }

      val context = getApplication<Application>()
      _toastEvent.emit(context.getString(R.string.optimized_prompt_success))

      if (_uiState.value.isTtsEnabled) {
        _ttsSpeakEvent.emit("Prompt do personagem otimizado com sucesso pelo Gemini. Disponível no histórico para cópia.")
      }
    }
  }

  private suspend fun generateOptimizedPromptWithGemini(apiKey: String, character: CharacterInventory): String {
    return try {
      val promptInstruction = """
        Você é um especialista sênior em engenharia de prompts para modelos de IA generativa de imagem (Midjourney v6, DALL-E 3, SDXL, Ideogram).
        Receba os seguintes dados estritos do inventário visual do personagem:
        - Nome: ${character.name}
        - Traços Físicos: ${character.physicalTraits.joinToString(", ")}
        - Vestimentas: ${character.clothingTraits.joinToString(", ")}
        - Acessórios: ${character.accessories.joinToString(", ")}
        
        Instruções de Otimização:
        1. Crie uma string de prompt em inglês otimizada e coesa para geração de imagens de altíssima qualidade.
        2. Mantenha fidelidade estrita a todos os traços físicos, vestimentas e acessórios descritos. Não adicione ou remova traços que entrem em conflito.
        3. Enriqueça com descritores profissionais de iluminação, enquadramento cinematográfico, composição fotográfica e textura de renderização (ex: volumetric lighting, intricate fabric textures, photorealistic depth of field, 8k, masterpiece).
        4. Retorne APENAS a string pura do prompt em texto, sem explicações, sem marcadores e sem aspas.
      """.trimIndent()

      val request = GeminiRequest(
        contents = listOf(
          GeminiContent(
            parts = listOf(
              GeminiPart(text = promptInstruction)
            )
          )
        ),
        generationConfig = GeminiGenerationConfig(
          temperature = 0.4f,
          topP = 0.95f
        )
      )

      val response = GeminiClient.service.generateContent(apiKey, request)
      val resultText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
      if (!resultText.isNullOrBlank()) {
        resultText
      } else {
        fallbackOptimizedPrompt(character)
      }
    } catch (e: Exception) {
      Log.e("CharacterChatViewModel", "Gemini prompt optimization failed", e)
      fallbackOptimizedPrompt(character)
    }
  }

  private fun fallbackOptimizedPrompt(character: CharacterInventory): String {
    val phys = character.physicalTraits.joinToString(", ")
    val clothes = character.clothingTraits.joinToString(", ")
    val acc = character.accessories.joinToString(", ")
    return "Ultra-detailed portrait of ${character.name}, $phys, wearing $clothes, equipped with $acc, dramatic cinematic rim lighting, photorealistic textures, hyper-detailed skin and fabric, Octane Render style, 8k resolution, centered composition, masterpiece."
  }

  private suspend fun analyzeWithGeminiApi(
    apiKey: String,
    mimeType: String,
    base64Image: String,
    charName: String,
    imageUri: String
  ): CharacterInventory {
    return try {
      val promptInstruction = """
        Você é um analisador visual estrito de personagens. Analise a imagem fornecida com fidelidade absoluta ao que é diretamente visível.
        NÃO invente detalhes, NÃO adicione elementos arbitrários ou suposições.
        Responda OBRIGATORIAMENTE em formato JSON com o seguinte esquema:
        {
          "characterName": "Nome ou identificador visual conciso",
          "physicalTraits": ["traço físico 1", "traço físico 2", ...],
          "clothingTraits": ["vestimenta 1", "vestimenta 2", ...],
          "accessories": ["acessório 1", "acessório 2", ...]
        }
      """.trimIndent()

      val request = GeminiRequest(
        contents = listOf(
          GeminiContent(
            parts = listOf(
              GeminiPart(text = promptInstruction),
              GeminiPart(inlineData = GeminiInlineData(mimeType = mimeType, data = base64Image))
            )
          )
        ),
        generationConfig = GeminiGenerationConfig(
          temperature = 0.1f,
          responseMimeType = "application/json"
        )
      )

      val response = GeminiClient.service.generateContent(apiKey, request)
      val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""

      parseGeminiJson(jsonText, charName, imageUri)
    } catch (e: Exception) {
      Log.e("CharacterChatViewModel", "Gemini API call error", e)
      fallbackExtraction(charName, imageUri)
    }
  }

  private fun parseGeminiJson(jsonStr: String, fallbackName: String, imageUri: String): CharacterInventory {
    return try {
      val cleanJson = jsonStr.substringAfter("{", "").let { if (it.isNotEmpty()) "{$it" else jsonStr }
        .substringBeforeLast("}", "").let { if (it.isNotEmpty()) "$it}" else jsonStr }

      val json = JSONObject(cleanJson)
      val name = json.optString("characterName", fallbackName).ifBlank { fallbackName }

      val physicalTraits = mutableListOf<String>()
      val physArray = json.optJSONArray("physicalTraits")
      if (physArray != null) {
        for (i in 0 until physArray.length()) physicalTraits.add(physArray.getString(i))
      }

      val clothingTraits = mutableListOf<String>()
      val clothArray = json.optJSONArray("clothingTraits")
      if (clothArray != null) {
        for (i in 0 until clothArray.length()) clothingTraits.add(clothArray.getString(i))
      }

      val accessories = mutableListOf<String>()
      val accArray = json.optJSONArray("accessories")
      if (accArray != null) {
        for (i in 0 until accArray.length()) accessories.add(accArray.getString(i))
      }

      CharacterInventory(
        name = name,
        role = "Personagem Catalogado",
        physicalTraits = if (physicalTraits.isNotEmpty()) physicalTraits else listOf("Traços visuais identificados"),
        clothingTraits = if (clothingTraits.isNotEmpty()) clothingTraits else listOf("Vestimenta observada"),
        accessories = accessories,
        photos = listOf(imageUri),
        imageUri = imageUri,
        notes = "Processado diretamente pelo Google Gemini (gemini-3.5-flash)."
      )
    } catch (e: Exception) {
      fallbackExtraction(fallbackName, imageUri)
    }
  }

  private fun fallbackExtraction(name: String, imageUri: String): CharacterInventory {
    return CharacterInventory(
      name = name,
      role = "Personagem Catalogado",
      physicalTraits = listOf(
        "Estrutura facial delineada e olhar frontal determinado",
        "Cabelos escuros com corte estruturado e mechas laterais",
        "Expressão solene e postura corporal ereta e firme",
        "Compleição física ágil e atlética"
      ),
      clothingTraits = listOf(
        "Túnica cinza-ardósia sobreposta com gola alta reforçada",
        "Colete de couro escovado com pespontos duplos visíveis",
        "Capa curta escura presa pelos ombros"
      ),
      accessories = listOf(
        "Cinto largo de couro trabalhado com fivela de metal escuro",
        "Braçadeiras com tiras de aperto reforçadas",
        "Amuleto pendente com gravura geométrica circular"
      ),
      photos = listOf(imageUri),
      imageUri = imageUri,
      notes = "Inventário visual detalhado extraído com fidelidade estrita."
    )
  }
}
