package com.example.model

data class ChatMessage(
  val id: String = java.util.UUID.randomUUID().toString(),
  val text: String,
  val isUser: Boolean,
  val imageUri: String? = null,
  val generatedPrompt: String? = null,
  val characterInventory: CharacterInventory? = null,
  val suggestAccessMemoryButton: Boolean = false,
  val timestamp: Long = System.currentTimeMillis()
)
