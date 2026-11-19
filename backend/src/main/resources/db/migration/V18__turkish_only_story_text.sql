-- V18: Masallar yalnızca Türkçe. İngilizce düşünen modeller (Groq gpt-oss) ve sahne bölücü ara sıra metni İngilizceye
-- kaydırabiliyordu. Yeni sürümler eskilerin aynısıdır; sonlarına açık bir dil kuralı eklenir. Ayrıca sunucu, Türkçe olmayan
-- metni kabul etmez ve işi yeniden dener (StoryGenerationService + TurkishText). Eski şablonlar silinmez.

-- Masal metni: v5 + dil kuralı (sistem) + kullanıcı mesajının sonunda yinelenen talimat
INSERT INTO prompt_templates (template_key, system_prompt, user_prompt_template)
SELECT 'story.text.v6',
       system_prompt || E'\n7. Dil: Başlıkları ve masalın bütün cümlelerini yalnızca Türkçe yaz. İngilizce ya da başka bir dilde tek bir kelime, cümle veya açıklama yazma; sana verilen bilgilerde İngilizce bir kelime olsa bile masalı Türkçe anlat.',
       replace(user_prompt_template, '/no_think', E'Masalın tamamını Türkçe yaz.\n/no_think')
FROM prompt_templates WHERE template_key = 'story.text.v5';

-- Sahne bölme: v2 + metni çevirmeme kuralı (yalnızca image_prompt İngilizcedir)
INSERT INTO prompt_templates (template_key, system_prompt, user_prompt_template)
SELECT 'story.scenes.v3',
       system_prompt || E'\n8. Dil: title ve her sahnenin text alanı Türkçe masaldaki cümlelerin aynısıdır; onları asla İngilizceye ya da başka bir dile çevirme. Yalnızca image_prompt alanı İngilizcedir.',
       user_prompt_template
FROM prompt_templates WHERE template_key = 'story.scenes.v2';
