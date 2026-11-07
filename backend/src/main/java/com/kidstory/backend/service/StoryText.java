package com.kidstory.backend.service;

/** 1. aşama çıktısı: Trendyol LLM'in yazdığı başlık ve masal metni (çocuğun adı yerine yer tutucu). */
public record StoryText(String title, String body) {
}
