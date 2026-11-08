package com.kidstory.backend.service;

import com.kidstory.backend.entity.Admin;
import com.kidstory.backend.entity.AdminAuditLog;
import com.kidstory.backend.entity.AiProviderConfig;
import com.kidstory.backend.entity.Parent;
import com.kidstory.backend.entity.Role;
import com.kidstory.backend.entity.Story;
import com.kidstory.backend.entity.StoryEnums;
import com.kidstory.backend.entity.StoryModerationReview;
import com.kidstory.backend.exception.ConflictException;
import com.kidstory.backend.exception.FieldValidationException;
import com.kidstory.backend.exception.NotFoundException;
import com.kidstory.backend.repository.AdminAuditLogRepository;
import com.kidstory.backend.repository.AdminRepository;
import com.kidstory.backend.repository.AiProviderConfigRepository;
import com.kidstory.backend.repository.ParentRepository;
import com.kidstory.backend.repository.RoleRepository;
import com.kidstory.backend.repository.StoryModerationReviewRepository;
import com.kidstory.backend.repository.StoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Yönetim paneli. Listeler sayım içerdiği için düz SQL ile okunur; her değişiklik JPA ile yapılır ve
 * admin_audit_logs tablosuna kim, ne, hangi kayıt ve hangi IP bilgisiyle yazılır.
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    public static final int MAX_CREDIT_GRANT = 50;
    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    private final JdbcTemplate jdbc;
    private final AdminRepository admins;
    private final RoleRepository roles;
    private final ParentRepository parents;
    private final StoryRepository stories;
    private final AiProviderConfigRepository providers;
    private final AdminAuditLogRepository audits;
    private final StoryModerationReviewRepository reviews;
    private final BillingService billing;
    private final PasswordEncoder passwordEncoder;

    public record Overview(long parents, long verifiedParents, long children, long stories, long storiesThisWeek,
                           long failedStories, long pendingModeration, List<StoryRow> recentStories, List<AuditRow> recentAudits) {}

    public record StoryRow(long id, String title, String status, String moderation, String theme, String childName,
                           String parentEmail, LocalDateTime createdAt) {}

    public record SceneRow(int number, String text) {}

    public record ParentRow(long id, String email, String name, boolean verified, boolean active, int storyCredits,
                            int interactiveCredits, long children, long stories, LocalDateTime createdAt) {}

    public record ProviderRow(long id, String type, String name, String model, boolean active, int priority, int timeoutMs) {}

    public record AdminRow(long id, String username, String email, String fullName, String role, boolean active, LocalDateTime lastLoginAt) {}

    public record AuditRow(String admin, String action, String target, Long targetId, String details, LocalDateTime at) {}

    public record NewAdmin(String username, String email, String fullName, String password, String role) {}

    /** Masal listesindeki filtreler ve karşılık gelen koşullar. */
    private static final Map<String, String> STORY_FILTERS = Map.of(
            "all", "TRUE",
            "completed", "s.status = 'COMPLETED'",
            "running", "s.status IN ('PENDING', 'GENERATING')",
            "failed", "s.status = 'FAILED'",
            "review", "s.moderation_status = 'PENDING'");

    // ---------- Okuma ----------

    @Transactional(readOnly = true)
    public Overview overview() {
        return new Overview(
                count("SELECT count(*) FROM parents"),
                count("SELECT count(*) FROM parents WHERE is_email_verified"),
                count("SELECT count(*) FROM child_profiles WHERE is_active"),
                count("SELECT count(*) FROM stories"),
                count("SELECT count(*) FROM stories WHERE created_at > now() - interval '7 days'"),
                count("SELECT count(*) FROM stories WHERE status = 'FAILED'"),
                count("SELECT count(*) FROM stories WHERE moderation_status = 'PENDING'"),
                stories("all", 6),
                recentAudits(8));
    }

    @Transactional(readOnly = true)
    public List<StoryRow> stories(String filter, int limit) {
        String where = STORY_FILTERS.getOrDefault(filter, "TRUE");
        return jdbc.query("""
                SELECT s.id, s.title, s.status, s.moderation_status, s.theme, c.name AS child_name, p.email, s.created_at
                FROM stories s JOIN child_profiles c ON c.id = s.child_id JOIN parents p ON p.id = s.parent_id
                WHERE %s ORDER BY s.created_at DESC LIMIT ?""".formatted(where),
                (r, i) -> new StoryRow(r.getLong("id"), r.getString("title"), r.getString("status"), r.getString("moderation_status"),
                        r.getString("theme"), r.getString("child_name"), r.getString("email"), instant(r.getTimestamp("created_at"))),
                limit);
    }

    public static boolean knownStoryFilter(String filter) {
        return STORY_FILTERS.containsKey(filter);
    }

    @Transactional(readOnly = true)
    public StoryRow story(long id) {
        return jdbc.query("""
                SELECT s.id, s.title, s.status, s.moderation_status, s.theme, c.name AS child_name, p.email, s.created_at
                FROM stories s JOIN child_profiles c ON c.id = s.child_id JOIN parents p ON p.id = s.parent_id WHERE s.id = ?""",
                (r, i) -> new StoryRow(r.getLong("id"), r.getString("title"), r.getString("status"), r.getString("moderation_status"),
                        r.getString("theme"), r.getString("child_name"), r.getString("email"), instant(r.getTimestamp("created_at"))),
                id).stream().findFirst().orElseThrow(() -> new NotFoundException("Masal bulunamadı"));
    }

    @Transactional(readOnly = true)
    public List<SceneRow> scenes(long storyId) {
        return jdbc.query("SELECT scene_number, scene_text FROM story_scenes WHERE story_id = ? ORDER BY scene_number",
                (r, i) -> new SceneRow(r.getInt(1), r.getString(2)), storyId);
    }

    @Transactional(readOnly = true)
    public List<ParentRow> parents(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return jdbc.query("""
                SELECT p.id, p.email, p.first_name || ' ' || p.last_name AS name, p.is_email_verified, p.is_active, p.created_at,
                       coalesce(w.story_credit_balance, 0) AS credits, coalesce(w.interactive_credit_balance, 0) AS interactive,
                       (SELECT count(*) FROM child_profiles c WHERE c.parent_id = p.id AND c.is_active) AS children,
                       (SELECT count(*) FROM stories s WHERE s.parent_id = p.id) AS stories
                FROM parents p LEFT JOIN credit_wallets w ON w.parent_id = p.id
                WHERE ? = '' OR lower(p.email) LIKE '%' || ? || '%' OR lower(p.first_name || ' ' || p.last_name) LIKE '%' || ? || '%'
                ORDER BY p.created_at DESC LIMIT 200""",
                (r, i) -> new ParentRow(r.getLong("id"), r.getString("email"), r.getString("name"), r.getBoolean("is_email_verified"),
                        r.getBoolean("is_active"), r.getInt("credits"), r.getInt("interactive"), r.getLong("children"), r.getLong("stories"),
                        instant(r.getTimestamp("created_at"))),
                q, q, q);
    }

    @Transactional(readOnly = true)
    public List<ProviderRow> providers() {
        return jdbc.query("""
                SELECT id, provider_type, provider_name, active_model_name, is_active, priority, timeout_ms
                FROM ai_providers_config ORDER BY provider_type, priority, provider_name""",
                (r, i) -> new ProviderRow(r.getLong(1), r.getString(2), r.getString(3), r.getString(4), r.getBoolean(5), r.getInt(6), r.getInt(7)));
    }

    @Transactional(readOnly = true)
    public List<AdminRow> admins() {
        return jdbc.query("""
                SELECT a.id, a.username, a.email, a.full_name, r.name AS role, a.is_active, a.last_login_at
                FROM admins a JOIN roles r ON r.id = a.role_id ORDER BY a.created_at""",
                (r, i) -> new AdminRow(r.getLong("id"), r.getString("username"), r.getString("email"), r.getString("full_name"),
                        r.getString("role"), r.getBoolean("is_active"), instant(r.getTimestamp("last_login_at"))));
    }

    @Transactional(readOnly = true)
    public List<AuditRow> recentAudits(int limit) {
        return jdbc.query("""
                SELECT a.username, l.action, l.target_entity, l.target_id, l.details, l.created_at
                FROM admin_audit_logs l JOIN admins a ON a.id = l.admin_id ORDER BY l.created_at DESC LIMIT ?""",
                (r, i) -> new AuditRow(r.getString(1), r.getString(2), r.getString(3), (Long) r.getObject(4), r.getString(5),
                        instant(r.getTimestamp(6))), limit);
    }

    // ---------- Değişiklikler (hepsi denetim kaydı bırakır) ----------

    @Transactional
    public void moderate(long adminId, long storyId, StoryModerationReview.Decision decision, String reason, String ip) {
        Story story = stories.findById(storyId).orElseThrow(() -> new NotFoundException("Masal bulunamadı"));
        story.setModerationStatus(decision == StoryModerationReview.Decision.APPROVED
                ? StoryEnums.ModerationStatus.APPROVED : StoryEnums.ModerationStatus.REJECTED);
        StoryModerationReview review = new StoryModerationReview();
        review.setStory(story);
        review.setAdmin(admins.getReferenceById(adminId));
        review.setDecision(decision);
        review.setReviewSource(StoryModerationReview.Source.ADMIN);
        review.setReason(blankToNull(reason));
        reviews.save(review);
        audit(adminId, decision == StoryModerationReview.Decision.APPROVED ? "STORY_APPROVED" : "STORY_REJECTED",
                "story", storyId, blankToNull(reason), ip);
    }

    @Transactional
    public void setParentActive(long adminId, long parentId, boolean active, String ip) {
        Parent parent = parents.findById(parentId).orElseThrow(() -> new NotFoundException("Ebeveyn bulunamadı"));
        parent.setActive(active);
        audit(adminId, active ? "PARENT_ACTIVATED" : "PARENT_SUSPENDED", "parent", parentId, parent.getEmail(), ip);
    }

    @Transactional
    public void grantCredits(long adminId, long parentId, int amount, String ip) {
        if (amount < 1 || amount > MAX_CREDIT_GRANT) {
            throw new FieldValidationException("amount", "1 ile " + MAX_CREDIT_GRANT + " arasında bir kredi girin");
        }
        billing.grantStoryCredits(parentId, amount, "Yönetici tarafından eklendi");
        audit(adminId, "PARENT_CREDITS", "parent", parentId, "+" + amount + " masal kredisi", ip);
    }

    @Transactional
    public void updateProvider(long adminId, long providerId, boolean active, int priority, String ip) {
        if (priority < 1 || priority > 100) {
            throw new FieldValidationException("priority", "Öncelik 1 ile 100 arasında olmalıdır");
        }
        AiProviderConfig provider = providers.findById(providerId).orElseThrow(() -> new NotFoundException("Sağlayıcı bulunamadı"));
        provider.setActive(active);
        provider.setPriority(priority);
        audit(adminId, "PROVIDER_UPDATED", "ai_provider", providerId,
                provider.getProviderType() + "/" + provider.getProviderName() + " " + (active ? "açık" : "kapalı") + ", öncelik " + priority, ip);
    }

    @Transactional
    public void createAdmin(long adminId, NewAdmin form, String ip) {
        String username = form.username().trim();
        String email = form.email().trim().toLowerCase(Locale.ROOT);
        if (!Role.ADMIN.equals(form.role()) && !Role.SUPER_ADMIN.equals(form.role())) {
            throw new FieldValidationException("role", "Geçersiz rol");
        }
        if (admins.existsByUsernameIgnoreCase(username)) {
            throw new FieldValidationException("username", "Bu kullanıcı adı kullanılıyor");
        }
        if (admins.existsByEmailIgnoreCase(email)) {
            throw new FieldValidationException("email", "Bu e-posta kullanılıyor");
        }
        Admin admin = new Admin();
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setFullName(form.fullName().trim());
        admin.setPasswordHash(passwordEncoder.encode(form.password()));
        admin.setRole(roles.findByName(form.role()).orElseThrow());
        admins.save(admin);
        audit(adminId, "ADMIN_CREATED", "admin", admin.getId(), username + " (" + form.role() + ")", ip);
    }

    @Transactional
    public void setAdminActive(long adminId, long targetId, boolean active, String ip) {
        if (adminId == targetId) {
            throw new ConflictException("Kendi hesabınızı askıya alamazsınız");
        }
        Admin admin = admins.findById(targetId).orElseThrow(() -> new NotFoundException("Yönetici bulunamadı"));
        admin.setActive(active);
        audit(adminId, active ? "ADMIN_ACTIVATED" : "ADMIN_SUSPENDED", "admin", targetId, admin.getUsername(), ip);
    }

    private void audit(long adminId, String action, String target, Long targetId, String details, String ip) {
        AdminAuditLog log = new AdminAuditLog();
        log.setAdmin(admins.getReferenceById(adminId));
        log.setAction(action);
        log.setTargetEntity(target);
        log.setTargetId(targetId);
        log.setDetails(details);
        log.setIpAddress(ip == null ? null : ip.substring(0, Math.min(45, ip.length())));
        audits.save(log);
    }

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    /** Panelde saatler İstanbul saatiyle gösterilir. */
    private static LocalDateTime instant(Timestamp t) {
        return t == null ? null : LocalDateTime.ofInstant(t.toInstant(), ISTANBUL);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
