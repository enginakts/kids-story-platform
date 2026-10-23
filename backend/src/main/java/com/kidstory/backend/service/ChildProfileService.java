package com.kidstory.backend.service;

import com.kidstory.backend.dto.ChildProfileResponse;
import com.kidstory.backend.dto.CreateChildRequest;
import com.kidstory.backend.entity.ChildProfile;
import com.kidstory.backend.entity.Parent;
import com.kidstory.backend.repository.ChildProfileRepository;
import com.kidstory.backend.repository.ParentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChildProfileService {

    private final ChildProfileRepository childProfileRepository;
    private final ParentRepository parentRepository;

    @Transactional(readOnly = true)
    public List<ChildProfileResponse> getChildrenByParent(Long parentId) {
        return childProfileRepository.findByParentIdAndIsActiveTrue(parentId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ChildProfileResponse createChild(Long parentId, CreateChildRequest request) {
        Parent parent = parentRepository.findById(parentId)
                .orElseThrow(() -> new IllegalArgumentException("Ebeveyn bulunamadı"));

        String avatarUrl = request.getAvatarUrl();
        if (avatarUrl == null || avatarUrl.isBlank()) {
            avatarUrl = "https://api.dicebear.com/7.x/bottts/svg?seed=" + request.getName().trim();
        }

        ChildProfile profile = ChildProfile.builder()
                .parent(parent)
                .name(request.getName().trim())
                .age(request.getAge())
                .gender(request.getGender())
                .favoriteAnimals(request.getFavoriteAnimals())
                .favoriteColors(request.getFavoriteColors())
                .specialInterests(request.getSpecialInterests())
                .bedtimeHour(request.getBedtimeHour() != null ? request.getBedtimeHour() : "20:30:00")
                .avatarUrl(avatarUrl)
                .isActive(true)
                .build();

        ChildProfile saved = childProfileRepository.save(profile);
        return mapToResponse(saved);
    }

    private ChildProfileResponse mapToResponse(ChildProfile child) {
        return ChildProfileResponse.builder()
                .id(child.getId())
                .parentId(child.getParent() != null ? child.getParent().getId() : null)
                .name(child.getName())
                .age(child.getAge())
                .gender(child.getGender())
                .favoriteAnimals(child.getFavoriteAnimals())
                .favoriteColors(child.getFavoriteColors())
                .specialInterests(child.getSpecialInterests())
                .bedtimeHour(child.getBedtimeHour())
                .avatarUrl(child.getAvatarUrl())
                .isActive(child.getIsActive())
                .build();
    }
}
