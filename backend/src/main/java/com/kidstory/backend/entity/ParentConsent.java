package com.kidstory.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Entity @Table(name = "parent_consents")
public class ParentConsent extends CreatedEntity {

    public enum Type { KVKK_PARENTAL, TERMS_OF_SERVICE }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_id", nullable = false)
    private Parent parent;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 50)
    private Type consentType;

    @Column(name = "document_version", nullable = false, length = 20)
    private String documentVersion;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;
}
