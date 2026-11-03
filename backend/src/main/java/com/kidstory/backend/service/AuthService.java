package com.kidstory.backend.service;

import com.kidstory.backend.dto.ParentRegisterRequest;
import com.kidstory.backend.entity.Parent;
import com.kidstory.backend.entity.ParentConsent;
import com.kidstory.backend.entity.Role;
import com.kidstory.backend.exception.ConflictException;
import com.kidstory.backend.repository.ParentConsentRepository;
import com.kidstory.backend.repository.ParentRepository;
import com.kidstory.backend.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    /** Onay metni değiştiğinde artırılır; ebeveynden yeniden onay alınır. */
    private static final String CONSENT_VERSION = "1.0";

    private final ParentRepository parents;
    private final RoleRepository roles;
    private final ParentConsentRepository consents;
    private final PasswordEncoder passwordEncoder;
    private final BillingService billing;
    private final ApplicationEventPublisher events;

    @Transactional
    public Parent register(ParentRegisterRequest request, String ipAddress) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (parents.existsByEmail(email)) {
            throw new ConflictException("Bu e-posta adresi zaten kayıtlı");
        }

        Parent parent = new Parent();
        parent.setEmail(email);
        parent.setPasswordHash(passwordEncoder.encode(request.password()));
        parent.setFirstName(request.firstName().trim());
        parent.setLastName(request.lastName().trim());
        parent.setRole(roles.findByName(Role.PARENT).orElseThrow());
        parents.save(parent);

        ParentConsent consent = new ParentConsent();
        consent.setParent(parent);
        consent.setConsentType(ParentConsent.Type.KVKK_PARENTAL);
        consent.setDocumentVersion(CONSENT_VERSION);
        consent.setIpAddress(ipAddress);
        consents.save(consent);

        billing.openFreeAccount(parent);
        events.publishEvent(new ParentRegisteredEvent(parent.getId()));
        return parent;
    }
}
