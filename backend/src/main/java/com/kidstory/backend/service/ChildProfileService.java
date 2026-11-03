package com.kidstory.backend.service;

import com.kidstory.backend.dto.ChildProfileResponse;
import com.kidstory.backend.dto.CreateChildRequest;
import com.kidstory.backend.entity.ChildProfile;
import com.kidstory.backend.exception.NotFoundException;
import com.kidstory.backend.repository.ChildProfileRepository;
import com.kidstory.backend.repository.ParentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** parentId her zaman oturumdan veya token'dan gelir, istekten alınmaz. */
@Service
@RequiredArgsConstructor
public class ChildProfileService {

    private final ChildProfileRepository children;
    private final ParentRepository parents;

    @Transactional(readOnly = true)
    public List<ChildProfileResponse> list(Long parentId) {
        return children.findByParentIdAndActiveTrueOrderByCreatedAtAsc(parentId).stream()
                .map(ChildProfileResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChildProfileResponse get(Long parentId, Long childId) {
        return ChildProfileResponse.from(owned(parentId, childId));
    }

    @Transactional
    public ChildProfileResponse create(Long parentId, CreateChildRequest request) {
        ChildProfile child = new ChildProfile();
        child.setParent(parents.getReferenceById(parentId));
        apply(child, request);
        return ChildProfileResponse.from(children.save(child));
    }

    @Transactional
    public ChildProfileResponse update(Long parentId, Long childId, CreateChildRequest request) {
        ChildProfile child = owned(parentId, childId);
        apply(child, request);
        return ChildProfileResponse.from(child);
    }

    @Transactional
    public void deactivate(Long parentId, Long childId) {
        owned(parentId, childId).setActive(false);
    }

    private ChildProfile owned(Long parentId, Long childId) {
        return children.findByIdAndParentIdAndActiveTrue(childId, parentId)
                .orElseThrow(() -> new NotFoundException("Çocuk profili bulunamadı"));
    }

    private void apply(ChildProfile child, CreateChildRequest r) {
        child.setName(r.name().trim());
        child.setAge(r.age());
        child.setGender(r.gender());
        child.setFavoriteAnimals(r.favoriteAnimals());
        child.setFavoriteColors(r.favoriteColors());
        child.setSpecialInterests(r.specialInterests());
        if (r.bedtimeHour() != null) {
            child.setBedtimeHour(r.bedtimeHour());
        }
    }
}
