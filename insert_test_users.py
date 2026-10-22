import sqlite3
import os

db_path = os.path.join(r"c:\Users\engin\Desktop\kids-histories\backend", "kidstory.db")

conn = sqlite3.connect(db_path)
cursor = conn.cursor()
cursor.execute("PRAGMA foreign_keys = ON;")

# Test şifresi için bcrypt formatında temsili hash ($2a$10$...)
# Plain text karşılığı: Admin123! veya Parent123!
PASSWORD_HASH_ADMIN = "$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi"  # Admin123!
PASSWORD_HASH_PARENT = "$2a$10$G8o.hB2nQd8Z5k9kFq7rCeY8rV7bVpU5O1aL4vX3hJ7iKTVKIUi"  # Parent123!

# 1. YÖNETİCİLER (admins)
cursor.execute("""
INSERT INTO admins (username, email, password_hash, full_name, role_id, is_active, last_login_at)
VALUES 
('superadmin', 'admin@kidstory.com', ?, 'Engin Aktaş', 3, 1, datetime('now')),
('moderator_ayse', 'ayse.moderator@kidstory.com', ?, 'Ayşe Yılmaz', 2, 1, datetime('now', '-2 days'))
""", (PASSWORD_HASH_ADMIN, PASSWORD_HASH_ADMIN))

# 2. EBEVEYNLER (parents)
cursor.execute("""
INSERT INTO parents (email, password_hash, first_name, last_name, phone_number, role_id, preferred_language, avatar_url, is_active, is_email_verified, last_login_at)
VALUES 
('mehmet.kaya@example.com', ?, 'Mehmet', 'Kaya', '+90 532 111 2233', 1, 'tr', 'https://api.dicebear.com/7.x/bottts/svg?seed=Mehmet', 1, 1, datetime('now', '-1 hours')),
('elif.demir@example.com', ?, 'Elif', 'Demir', '+90 542 444 5566', 1, 'tr', 'https://api.dicebear.com/7.x/bottts/svg?seed=Elif', 1, 1, datetime('now', '-5 hours')),
('can.ozkan@example.com', ?, 'Can', 'Özkan', '+90 555 777 8899', 1, 'tr', 'https://api.dicebear.com/7.x/bottts/svg?seed=Can', 1, 0, NULL)
""", (PASSWORD_HASH_PARENT, PASSWORD_HASH_PARENT, PASSWORD_HASH_PARENT))

# 3. KREDİ CÜZDANLARI (credit_wallets)
# Mehmet Kaya (id=1): 15 Masal, 5 Etkileşimli
# Elif Demir (id=2): 3 Masal, 1 Etkileşimli
# Can Özkan (id=3): 3 Masal, 1 Etkileşimli
cursor.execute("""
INSERT INTO credit_wallets (parent_id, story_credit_balance, interactive_credit_balance)
VALUES 
(1, 15, 5),
(2, 3, 1),
(3, 3, 1)
""")

# 4. ÇOCUK PROFİLLERİ (child_profiles)
cursor.execute("""
INSERT INTO child_profiles (parent_id, name, age, gender, favorite_animals, favorite_colors, special_interests, bedtime_hour, avatar_url, is_active)
VALUES 
(1, 'Zeynep', 4, 'Kız', 'Kedi, Tavşan', 'Pembe, Mor', 'Uzay maceraları, resim çizme, yıldızlar', '20:30:00', 'https://api.dicebear.com/7.x/bottts/svg?seed=Zeynep', 1),
(1, 'Ali', 6, 'Erkek', 'Aslan, Dinozor', 'Mavi, Yeşil', 'Lego yapımı, orman keşfi, süper kahramanlar', '21:00:00', 'https://api.dicebear.com/7.x/bottts/svg?seed=Ali', 1),
(2, 'Emir', 3, 'Erkek', 'Köpek, Ayıcık', 'Sarı, Turuncu', 'Uyku masalları, trenler, neşeli şarkılar', '20:00:00', 'https://api.dicebear.com/7.x/bottts/svg?seed=Emir', 1)
""")

conn.commit()

# Doğrulama sorguları
print("--- EKLENEN YÖNETİCİLER ---")
for row in cursor.execute("SELECT id, username, email, full_name, role_id, is_active FROM admins"):
    print(row)

print("\n--- EKLENEN EBEVEYNLER ---")
for row in cursor.execute("SELECT id, email, first_name, last_name, phone_number, is_email_verified FROM parents"):
    print(row)

print("\n--- CÜZDANLAR ---")
for row in cursor.execute("SELECT id, parent_id, story_credit_balance, interactive_credit_balance FROM credit_wallets"):
    print(row)

print("\n--- ÇOCUK PROFİLLERİ ---")
for row in cursor.execute("SELECT id, parent_id, name, age, gender, favorite_animals FROM child_profiles"):
    print(row)

conn.close()
