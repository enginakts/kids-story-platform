package com.kidstory.backend.service;

/** Kayıt işlemi veritabanına yazıldıktan sonra doğrulama e-postası göndermek için yayınlanır. */
public record ParentRegisteredEvent(Long parentId) {
}
