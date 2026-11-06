package com.kidstory.backend.service.story;

import com.kidstory.backend.entity.CharacterBible;
import com.kidstory.backend.entity.ChildProfile;
import com.kidstory.backend.entity.ContentBlacklist;
import com.kidstory.backend.entity.LocationBible;
import com.kidstory.backend.entity.PromptTemplate;
import com.kidstory.backend.entity.Story;
import com.kidstory.backend.entity.StoryEnums;
import com.kidstory.backend.repository.ContentBlacklistRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class StorySafetyAndParsingTests {

    private final ContentSafetyFilter filter = new ContentSafetyFilter(mock(ContentBlacklistRepository.class));
    private final ContentSafetyFilter.Rules rules = new ContentSafetyFilter.Rules(List.of(
            entry("canavar", ContentBlacklist.Action.REPLACE, "sevimli dost"),
            entry("kan", ContentBlacklist.Action.BLOCK, null),
            entry("öldür", ContentBlacklist.Action.BLOCK, null)));

    @Test
    void replacesAndBlocksBlacklistedWords() {
        assertThat(filter.clean("Ormanda bir Canavar varmış.", rules)).isEqualTo("Ormanda bir sevimli dost varmış.");
        assertThatThrownBy(() -> filter.clean("Dizinden kan gelmiş.", rules))
                .isInstanceOf(ContentSafetyFilter.UnsafeContentException.class);
        assertThatThrownBy(() -> filter.clean("Onu öldürdü.", rules))
                .isInstanceOf(ContentSafetyFilter.UnsafeContentException.class);
    }

    @Test
    void shortKeywordsMatchOnlyWholeWords() {
        assertThat(filter.clean("Kelebeğin kanatları rengârenkmiş, kanguru zıplamış.", rules))
                .isEqualTo("Kelebeğin kanatları rengârenkmiş, kanguru zıplamış.");
    }

    @Test
    void parsesFencedJsonAndRejectsWrongSceneCount() {
        StoryDraftParser.Draft draft = StoryDraftParser.parse("""
                İşte masal:
                ```json
                {"title":"Pamuk","scenes":[{"text":"a","imagePrompt":"x"},{"text":"b"},{"text":"c"},{"text":"d"}]}
                ```""");
        assertThat(draft.scenes()).hasSize(4);
        assertThatThrownBy(() -> StoryDraftParser.parse("{\"title\":\"t\",\"scenes\":[{\"text\":\"a\"}]}"))
                .isInstanceOf(StoryDraftParser.InvalidDraftException.class);
        assertThatThrownBy(() -> StoryDraftParser.parse("bozuk yanıt"))
                .isInstanceOf(StoryDraftParser.InvalidDraftException.class);
    }

    @Test
    void childNameIsNeverSentToTheModel() {
        ChildProfile child = new ChildProfile();
        child.setName("Zeynep");
        child.setAge(5);
        CharacterBible hero = new CharacterBible();
        hero.setName("Pamuk");
        hero.setSpecies("Tavşan");
        LocationBible place = new LocationBible();
        place.setName("Çiçekli Orman");
        place.setAtmosphereDescription("Güneşli");
        Story story = new Story();
        story.setTheme(StoryEnums.Theme.SHARING);
        story.setTargetEmotion(StoryEnums.Emotion.EMPATHY);
        PromptTemplate template = new PromptTemplate();
        template.setSystemPrompt("sistem");
        template.setUserPromptTemplate("Yaş: {{age}} Değer: {{theme}} Duygu: {{emotion}} Karakter: {{character}}");

        StoryPrompts.TextPrompt prompt = StoryPrompts.text(template, story, child, hero, List.of(), place, "dinozorlar");
        assertThat(prompt.user()).contains("5", "Paylaşma", "Empati", "Pamuk").doesNotContain("Zeynep");
    }

    private static ContentBlacklist entry(String keyword, ContentBlacklist.Action action, String replacement) {
        ContentBlacklist e = new ContentBlacklist();
        e.setKeyword(keyword);
        e.setCategory("TEST");
        e.setActionType(action);
        e.setReplacementWord(replacement);
        return e;
    }
}
