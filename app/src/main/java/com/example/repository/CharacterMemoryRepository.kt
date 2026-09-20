package com.example.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.model.CharacterInventory
import com.example.model.ChatMessage
import com.example.model.MemoryEntry
import com.example.model.UserProfile
import org.json.JSONArray
import org.json.JSONObject

class CharacterMemoryRepository(context: Context) {
  private val prefs: SharedPreferences = context.getSharedPreferences("character_memory_prefs", Context.MODE_PRIVATE)

  // ===================== GERENCIAMENTO DE PERFIS =====================
  fun getActiveProfileId(): String {
    return prefs.getString("active_user_profile_id", "default_ordem") ?: "default_ordem"
  }

  fun setActiveProfileId(profileId: String) {
    prefs.edit().putString("active_user_profile_id", profileId).apply()
  }

  fun getAllProfiles(): List<UserProfile> {
    val rawJson = prefs.getString("saved_user_profiles", null)
    if (rawJson.isNullOrBlank()) {
      val defaultList = listOf(defaultProfile())
      persistProfiles(defaultList)
      return defaultList
    }
    return try {
      val jsonArray = JSONArray(rawJson)
      val list = mutableListOf<UserProfile>()
      for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)
        list.add(
          UserProfile(
            id = obj.optString("id"),
            name = obj.optString("name"),
            storyTitle = obj.optString("storyTitle", "A Ordem dos Sem Olhos"),
            customStory = if (obj.isNull("customStory")) null else obj.optString("customStory"),
            isDefault = obj.optBoolean("isDefault", false),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
          )
        )
      }
      if (list.isEmpty()) {
        val defaultList = listOf(defaultProfile())
        persistProfiles(defaultList)
        defaultList
      } else {
        list
      }
    } catch (e: Exception) {
      listOf(defaultProfile())
    }
  }

  fun getActiveProfile(): UserProfile {
    val activeId = getActiveProfileId()
    return getAllProfiles().firstOrNull { it.id == activeId } ?: getAllProfiles().firstOrNull() ?: defaultProfile()
  }

  fun createProfile(name: String): UserProfile {
    val newProfile = UserProfile(
      id = "profile_" + System.currentTimeMillis(),
      name = name.trim().ifBlank { "Novo Perfil" },
      storyTitle = "A Ordem dos Sem Olhos",
      customStory = null,
      isDefault = false,
      createdAt = System.currentTimeMillis()
    )
    val list = getAllProfiles().toMutableList()
    list.add(newProfile)
    persistProfiles(list)
    setActiveProfileId(newProfile.id)
    return newProfile
  }

  fun saveProfile(profile: UserProfile) {
    val list = getAllProfiles().toMutableList()
    val index = list.indexOfFirst { it.id == profile.id }
    if (index >= 0) {
      list[index] = profile
    } else {
      list.add(profile)
    }
    persistProfiles(list)
  }

  fun updateProfileStory(profileId: String, storyTitle: String, customStory: String?) {
    val list = getAllProfiles().toMutableList()
    val index = list.indexOfFirst { it.id == profileId }
    if (index >= 0) {
      val updated = list[index].copy(
        storyTitle = storyTitle.ifBlank { "História Personalizada" },
        customStory = customStory
      )
      list[index] = updated
      persistProfiles(list)
    }
  }

  fun deleteProfile(profileId: String): Boolean {
    val list = getAllProfiles().toMutableList()
    val target = list.firstOrNull { it.id == profileId } ?: return false
    if (target.isDefault || list.size <= 1) return false // Não deleta o perfil padrão único

    list.removeAll { it.id == profileId }
    persistProfiles(list)

    if (getActiveProfileId() == profileId) {
      setActiveProfileId(list.first().id)
    }
    return true
  }

  private fun defaultProfile(): UserProfile {
    return UserProfile(
      id = "default_ordem",
      name = "A Ordem dos Sem Olhos",
      storyTitle = "A Ordem dos Sem Olhos",
      customStory = null,
      isDefault = true,
      createdAt = 1700000000000L
    )
  }

  private fun persistProfiles(profiles: List<UserProfile>) {
    val jsonArray = JSONArray()
    profiles.forEach { p ->
      val obj = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("storyTitle", p.storyTitle)
        if (p.customStory != null) put("customStory", p.customStory) else put("customStory", JSONObject.NULL)
        put("isDefault", p.isDefault)
        put("createdAt", p.createdAt)
      }
      jsonArray.put(obj)
    }
    prefs.edit().putString("saved_user_profiles", jsonArray.toString()).apply()
  }

  // ===================== MENSAGENS POR PERFIL =====================
  fun getChatMessagesForProfile(profileId: String): List<ChatMessage> {
    val rawJson = prefs.getString("chat_messages_$profileId", null) ?: return emptyList()
    return try {
      val jsonArray = JSONArray(rawJson)
      val list = mutableListOf<ChatMessage>()
      for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)
        list.add(
          ChatMessage(
            text = obj.optString("text"),
            isUser = obj.optBoolean("isUser"),
            imageUri = obj.optString("imageUri").takeIf { it.isNotBlank() },
            generatedPrompt = obj.optString("generatedPrompt").takeIf { it.isNotBlank() },
            suggestAccessMemoryButton = obj.optBoolean("suggestAccessMemoryButton", false),
            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
          )
        )
      }
      list
    } catch (e: Exception) {
      emptyList()
    }
  }

  fun saveChatMessagesForProfile(profileId: String, messages: List<ChatMessage>) {
    val jsonArray = JSONArray()
    messages.forEach { m ->
      val obj = JSONObject().apply {
        put("text", m.text)
        put("isUser", m.isUser)
        put("imageUri", m.imageUri ?: "")
        put("generatedPrompt", m.generatedPrompt ?: "")
        put("suggestAccessMemoryButton", m.suggestAccessMemoryButton)
        put("timestamp", m.timestamp)
      }
      jsonArray.put(obj)
    }
    prefs.edit().putString("chat_messages_$profileId", jsonArray.toString()).apply()
  }

  fun clearChatMessagesForProfile(profileId: String) {
    prefs.edit().remove("chat_messages_$profileId").apply()
  }

  fun saveMemoryEntry(content: String): MemoryEntry {
    val entry = MemoryEntry(content = content.trim())
    val list = getAllMemoryEntries().toMutableList()
    list.add(0, entry)
    persistMemoryEntries(list)
    return entry
  }

  fun getAllMemoryEntries(): List<MemoryEntry> {
    val rawJson = prefs.getString("saved_specific_memories", null) ?: return emptyList()
    return try {
      val jsonArray = JSONArray(rawJson)
      val list = mutableListOf<MemoryEntry>()
      for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)
        list.add(
          MemoryEntry(
            id = obj.optString("id"),
            content = obj.optString("content"),
            timestamp = obj.optLong("timestamp")
          )
        )
      }
      list
    } catch (e: Exception) {
      emptyList()
    }
  }

  fun deleteMemoryEntry(id: String) {
    val list = getAllMemoryEntries().filterNot { it.id == id }
    persistMemoryEntries(list)
  }

  fun clearAllMemoryEntries() {
    prefs.edit().remove("saved_specific_memories").apply()
  }

  private fun persistMemoryEntries(list: List<MemoryEntry>) {
    val jsonArray = JSONArray()
    list.forEach { entry ->
      val obj = JSONObject().apply {
        put("id", entry.id)
        put("content", entry.content)
        put("timestamp", entry.timestamp)
      }
      jsonArray.put(obj)
    }
    prefs.edit().putString("saved_specific_memories", jsonArray.toString()).apply()
  }

  fun saveCharacter(character: CharacterInventory) {
    val list = getAllCharacters().toMutableList()
    val index = list.indexOfFirst { it.name.equals(character.name, ignoreCase = true) }
    if (index >= 0) {
      list[index] = character
    } else {
      list.add(0, character)
    }
    persistList(list)
  }

  fun getAllCharacters(): List<CharacterInventory> {
    val rawJson = prefs.getString("saved_characters", null) ?: return defaultSampleCharacters()
    return try {
      val jsonArray = JSONArray(rawJson)
      val list = mutableListOf<CharacterInventory>()
      for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)
        val photosList = jsonArrayToList(obj.optJSONArray("photos"))
        val primaryImg = obj.optString("imageUri").takeIf { it.isNotBlank() }
        // Se houver imageUri antigo e photos estiver vazio, migra
        val finalPhotos = if (photosList.isEmpty() && primaryImg != null) {
          listOf(primaryImg)
        } else {
          photosList
        }
        list.add(
          CharacterInventory(
            id = obj.optString("id"),
            name = obj.optString("name"),
            role = obj.optString("role", "Personagem"),
            age = obj.optString("age", "Não especificada"),
            physicalTraits = jsonArrayToList(obj.optJSONArray("physicalTraits")),
            clothingTraits = jsonArrayToList(obj.optJSONArray("clothingTraits")),
            accessories = jsonArrayToList(obj.optJSONArray("accessories")),
            photos = finalPhotos,
            imageUri = finalPhotos.firstOrNull() ?: primaryImg,
            notes = obj.optString("notes"),
            timestamp = obj.optLong("timestamp")
          )
        )
      }
      list
    } catch (e: Exception) {
      defaultSampleCharacters()
    }
  }

  fun findCharacterByName(name: String): CharacterInventory? {
    return getAllCharacters().firstOrNull { it.name.contains(name, ignoreCase = true) }
  }

  private fun persistList(list: List<CharacterInventory>) {
    val jsonArray = JSONArray()
    list.forEach { char ->
      val obj = JSONObject().apply {
        put("id", char.id)
        put("name", char.name)
        put("role", char.role)
        put("age", char.age)
        put("physicalTraits", JSONArray(char.physicalTraits))
        put("clothingTraits", JSONArray(char.clothingTraits))
        put("accessories", JSONArray(char.accessories))
        put("photos", JSONArray(char.photos))
        put("imageUri", char.photos.firstOrNull() ?: char.imageUri ?: "")
        put("notes", char.notes)
        put("timestamp", char.timestamp)
      }
      jsonArray.put(obj)
    }
    prefs.edit().putString("saved_characters", jsonArray.toString()).apply()
  }

  private fun jsonArrayToList(array: JSONArray?): List<String> {
    if (array == null) return emptyList()
    val list = mutableListOf<String>()
    for (i in 0 until array.length()) {
      list.add(array.getString(i))
    }
    return list
  }

  private fun defaultSampleCharacters(): List<CharacterInventory> {
    return listOf(
      CharacterInventory(
        name = "Derek",
        role = "Protagonista / Guerreiro Meio-Mágico",
        age = "Jovem (poderes despertados aos 5 anos)",
        physicalTraits = listOf("Cabelos brancos", "Olhos prateados penetrantes", "Porte jovem e atlético", "Traços que lembram o lendário guerreiro Teravarn"),
        clothingTraits = listOf("Vestimentas pretas de couro resistente", "Manto escuro para marcha noturna", "Botas de couro militar"),
        accessories = listOf("Espada mágica com empunhadura esmeralda hipnótica (tomada de Marcos no nurague)", "Energia cortante nas pontas dos dedos"),
        notes = "Filho de Teravarn com uma humana da Terra. Guerreiro meio-mágico com poderes despertados aos 5 anos. Criado e treinado pelo Comandante Kallan."
      ),
      CharacterInventory(
        name = "Comandante Kallan",
        role = "Comandante Veterano de Valdarion / Mentor",
        age = "Milenar (veterano de Valdarion)",
        physicalTraits = listOf("Feições rígidas e austeras de veterano", "Olhar afiado de comando militar", "Postura imponente de líder de Valdarion"),
        clothingTraits = listOf("Armadura de comando de Valdarion", "Manto de invisibilidade", "Túnica militar de combate"),
        accessories = listOf("Escudo invisível de pura energia", "Capacidade de manipulação telecinética e quebra de forças à distância"),
        notes = "Comandante lendário de Valdarion. Melhor amigo de Teravarn e tutor de Derek. Conhecedor dos segredos dos anjos e demônios."
      ),
      CharacterInventory(
        name = "Kaelis",
        role = "Criança Sagrada / Fonte de Poder",
        age = "8 anos",
        physicalTraits = listOf("Menina de 8 anos", "Aparência frágil e inocente", "Saúde perfeita sem marcas físicas graças à blindagem mágica ancestral"),
        clothingTraits = listOf("Manta protetora", "Vestimentas simples com capuz discreto para ocultação no porto"),
        accessories = listOf("Proteção mágica ancestral de Teravarn impenetrável contra armas mortais e demônios menores"),
        notes = "Criança dotada de poder sobrenatural imenso, cobiçada por Maycon e Draxos. Seu sangue concede passagem livre contra os monstros marinhos de Teravarn."
      ),
      CharacterInventory(
        name = "Lyra",
        role = "Bruxa Enigmática / Protetora nas Sombras",
        age = "Aparente jovem mulher",
        physicalTraits = listOf("Pele muito branca", "Longos cabelos vermelhos como chamas vivas", "Beleza letal e olhos amendoados profundos"),
        clothingTraits = listOf("Manto preto com capuz sombrio", "Vestes místicas com compartimentos para ervas e frascos alquímicos"),
        accessories = listOf("Jarros de ervas raras de Ferusaval", "Poder de telepatia, portais dimensionais e ilusões perceptivas"),
        notes = "Bruxa misteriosa sequestrada por Maycon que fingia torturar Kaelis para protegê-la. Sofreu um choque violento de memórias ao ouvir o nome de Teravarn."
      ),
      CharacterInventory(
        name = "Carlos",
        role = "Líder dos Desertores / Guerreiro Estrategista",
        age = "Veterano",
        physicalTraits = listOf("Postura firme e resoluta", "Olhar inteligente e calculista", "Porte de líder veterano"),
        clothingTraits = listOf("Armadura da guarda da ilha", "Trajes discretos para camuflagem civil no porto"),
        accessories = listOf("Espada de combate", "Unidade de 14 guerreiros leais e desertores sob seu comando"),
        notes = "Novo comandante nomeado por Maycon que descobriu a natureza canibal não-humana do tirano e desertou para o sul com seus homens. Amigo de Zalor."
      ),
      CharacterInventory(
        name = "Teravarn",
        role = "Maior Guerreiro Mágico Ancestral",
        age = "Mais de 72 mil anos",
        physicalTraits = listOf("Poder cósmico avassalador", "Aura divina primordial", "Pai de Derek"),
        clothingTraits = listOf("Vestimentas antigas dos primórdios", "Manto de guerra ancestral"),
        accessories = listOf("Pacto ancestral com monstros marinhos", "Magia que derrotou legiões inteiras"),
        notes = "Ensinou os homens a saírem das cavernas. Apaixonou-se por uma mulher na Terra e gerou Derek. Pediu a Kallan para tirar sua vida após perdê-la."
      ),
      CharacterInventory(
        name = "Zalor",
        role = "Construtor Naval e Aliado Leal",
        age = "Veterano",
        physicalTraits = listOf("Físico forte de carpinteiro naval", "Olhos bondosos e calejados pelo trabalho"),
        clothingTraits = listOf("Roupas de carpinteiro naval", "Avental de ferramentas e couro"),
        accessories = listOf("Ferramentas de construção naval", "Navio no porto de Ferusaval"),
        notes = "Líder da família do porto de Ferusaval. Descendente dos humanos que acolheram Teravarn ferido séculos atrás. Prepara o navio para a fuga."
      )
    )
  }
}
