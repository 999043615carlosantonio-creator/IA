package com.example.model

data class CharacterInventory(
  val id: String = java.util.UUID.randomUUID().toString(),
  val name: String,
  val role: String = "Personagem",
  val age: String = "Não especificada",
  val physicalTraits: List<String> = emptyList(),
  val clothingTraits: List<String> = emptyList(),
  val accessories: List<String> = emptyList(),
  val photos: List<String> = emptyList(),
  val imageUri: String? = null,
  val notes: String = "",
  val timestamp: Long = System.currentTimeMillis()
) {
  fun generateImagePrompt(style: String = "cinematic, high detail, masterpiece"): String {
    val physicalSection = if (physicalTraits.isNotEmpty()) {
      "Character appearance: " + physicalTraits.joinToString(", ")
    } else ""

    val clothingSection = if (clothingTraits.isNotEmpty()) {
      "Attire: " + clothingTraits.joinToString(", ")
    } else ""

    val accessoriesSection = if (accessories.isNotEmpty()) {
      "Accessories & equipment: " + accessories.joinToString(", ")
    } else ""

    val combined = listOf(physicalSection, clothingSection, accessoriesSection)
      .filter { it.isNotBlank() }
      .joinToString(". ")

    return "$name. $combined. $style, photorealistic lighting, 8k resolution, centered composition."
  }
}

