package com.example.model

data class UserProfile(
  val id: String,
  val name: String,
  val storyTitle: String = "A Ordem dos Sem Olhos",
  val customStory: String? = null,
  val isDefault: Boolean = false,
  val createdAt: Long = System.currentTimeMillis()
)
