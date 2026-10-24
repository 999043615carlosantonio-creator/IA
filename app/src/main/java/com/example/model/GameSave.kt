package com.example.model

enum class SaveType {
  AUTO_KEY_MOMENT, // Salvo automaticamente em momentos-chave e decisões da história
  AUTO_INTERVAL,   // Salvo automaticamente em intervalos regulares de tempo
  MANUAL           // Ponto de restauração criado manualmente pelo jogador
}

data class GameSave(
  val id: String = java.util.UUID.randomUUID().toString(),
  val profileId: String,
  val title: String,
  val timestamp: Long = System.currentTimeMillis(),
  val saveType: SaveType = SaveType.AUTO_KEY_MOMENT,
  val triggerReason: String,
  val turnCount: Int = 0,
  val lastActionSnippet: String = "",
  val storyTitle: String = "A Ordem dos Sem Olhos",
  val messagesSnapshot: List<ChatMessage> = emptyList(),
  val memoriesSnapshot: List<MemoryEntry> = emptyList(),
  val charactersSnapshot: List<CharacterInventory> = emptyList()
)
