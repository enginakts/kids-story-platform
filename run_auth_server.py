import json
import sqlite3
import os
import time
import base64
import hmac
import hashlib
from http.server import HTTPServer, BaseHTTPRequestHandler
import bcrypt

DB_PATH = os.path.join(os.path.dirname(__file__), "backend", "kidstory.db")
SECRET_KEY = b"KidsStoryPlatformSecretKeyForJwt2026SuperSecureKeyMustBeLongEnough!"

def create_jwt(subject, user_type, role, user_id):
    header = {"alg": "HS256", "typ": "JWT"}
    payload = {
        "sub": subject,
        "userType": user_type,
        "role": role,
        "userId": user_id,
        "iat": int(time.time()),
        "exp": int(time.time()) + 86400
    }
    
    def b64_encode(data):
        return base64.urlsafe_b64encode(json.dumps(data).encode()).decode().rstrip("=")
        
    h_b64 = b64_encode(header)
    p_b64 = b64_encode(payload)
    message = f"{h_b64}.{p_b64}".encode()
    signature = base64.urlsafe_b64encode(hmac.new(SECRET_KEY, message, hashlib.sha256).digest()).decode().rstrip("=")
    return f"{h_b64}.{p_b64}.{signature}"

class AuthHandler(BaseHTTPRequestHandler):
    def _send_cors_headers(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")

    def do_OPTIONS(self):
        self.send_response(200)
        self._send_cors_headers()
        self.end_headers()

    def _send_json(self, status_code, data):
        self.send_response(status_code)
        self._send_cors_headers()
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.end_headers()
        self.wfile.write(json.dumps(data, ensure_ascii=False).encode("utf-8"))

    def do_GET(self):
        if self.path == "/api/auth/health":
            self._send_json(200, {"status": "UP", "service": "Kids Story Auth Service", "timestamp": int(time.time() * 1000)})
        elif self.path.startswith("/api/children"):
            import urllib.parse
            parsed = urllib.parse.urlparse(self.path)
            query = urllib.parse.parse_qs(parsed.query)
            parent_id = query.get("parentId", [None])[0]

            conn = sqlite3.connect(DB_PATH)
            cursor = conn.cursor()
            try:
                if parent_id:
                    cursor.execute("""
                        SELECT id, parent_id, name, age, gender, favorite_animals, favorite_colors, special_interests, bedtime_hour, avatar_url, is_active 
                        FROM child_profiles WHERE parent_id = ? AND is_active = 1
                    """, (parent_id,))
                else:
                    cursor.execute("""
                        SELECT id, parent_id, name, age, gender, favorite_animals, favorite_colors, special_interests, bedtime_hour, avatar_url, is_active 
                        FROM child_profiles WHERE is_active = 1
                    """)
                rows = cursor.fetchall()
                children = []
                for r in rows:
                    children.append({
                        "id": r[0],
                        "parentId": r[1],
                        "name": r[2],
                        "age": r[3],
                        "gender": r[4],
                        "favoriteAnimals": r[5],
                        "favoriteColors": r[6],
                        "specialInterests": r[7],
                        "bedtimeHour": r[8],
                        "avatarUrl": r[9] or f"https://api.dicebear.com/7.x/bottts/svg?seed={r[2]}",
                        "isActive": bool(r[10])
                    })
                self._send_json(200, children)
            finally:
                conn.close()
        elif self.path.startswith("/api/wallet"):
            import urllib.parse
            parsed = urllib.parse.urlparse(self.path)
            query = urllib.parse.parse_qs(parsed.query)
            parent_id = query.get("parentId", [None])[0]

            conn = sqlite3.connect(DB_PATH)
            cursor = conn.cursor()
            try:
                cursor.execute("SELECT story_credit_balance, interactive_credit_balance FROM credit_wallets WHERE parent_id = ?", (parent_id,))
                w = cursor.fetchone()
                if w:
                    self._send_json(200, {"storyCredits": w[0], "interactiveCredits": w[1]})
                else:
                    self._send_json(200, {"storyCredits": 0, "interactiveCredits": 0})
            finally:
                conn.close()
        else:
            self._send_json(404, {"error": "Not Found"})

    def do_POST(self):
        content_length = int(self.headers.get("Content-Length", 0))
        post_data = self.rfile.read(content_length).decode("utf-8")
        
        try:
            body = json.loads(post_data) if post_data else {}
        except Exception:
            self._send_json(400, {"error": "Geçersiz JSON verisi"})
            return

        conn = sqlite3.connect(DB_PATH)
        cursor = conn.cursor()

        try:
            if self.path == "/api/auth/parent/login":
                email = body.get("email", "").strip().lower()
                password = body.get("password", "")

                cursor.execute("SELECT id, email, password_hash, first_name, last_name, is_active FROM parents WHERE email = ?", (email,))
                parent = cursor.fetchone()

                if not parent:
                    self._send_json(401, {"error": "E-posta adresi veya şifre hatalı"})
                    return

                parent_id, p_email, p_hash, f_name, l_name, is_active = parent

                if not is_active:
                    self._send_json(401, {"error": "Bu ebeveyn hesabı devre dışı bırakılmıştır."})
                    return

                # Parola kontrolü
                if not bcrypt.checkpw(password.encode("utf-8"), p_hash.encode("utf-8")):
                    self._send_json(401, {"error": "E-posta adresi veya şifre hatalı"})
                    return

                # Cüzdan bakiyesi
                cursor.execute("SELECT story_credit_balance, interactive_credit_balance FROM credit_wallets WHERE parent_id = ?", (parent_id,))
                wallet = cursor.fetchone()
                story_cr = wallet[0] if wallet else 0
                inter_cr = wallet[1] if wallet else 0

                token = create_jwt(p_email, "PARENT", "ROLE_PARENT", parent_id)

                self._send_json(200, {
                    "token": token,
                    "tokenType": "Bearer",
                    "userType": "PARENT",
                    "id": parent_id,
                    "email": p_email,
                    "fullName": f"{f_name} {l_name}",
                    "role": "ROLE_PARENT",
                    "storyCredits": story_cr,
                    "interactiveCredits": inter_cr
                })

            elif self.path == "/api/auth/parent/register":
                email = body.get("email", "").strip().lower()
                password = body.get("password", "")
                f_name = body.get("firstName", "").strip()
                l_name = body.get("lastName", "").strip()
                phone = body.get("phoneNumber", "")
                pref_lang = body.get("preferredLanguage", "tr")

                cursor.execute("SELECT id FROM parents WHERE email = ?", (email,))
                if cursor.fetchone():
                    self._send_json(400, {"error": "Bu e-posta adresi ile kayıtlı bir hesap zaten mevcuttur."})
                    return

                hashed_pw = bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")
                
                cursor.execute("""
                    INSERT INTO parents (email, password_hash, first_name, last_name, phone_number, role_id, preferred_language, avatar_url, is_active, is_email_verified)
                    VALUES (?, ?, ?, ?, ?, 1, ?, ?, 1, 0)
                """, (email, hashed_pw, f_name, l_name, phone, pref_lang, f"https://api.dicebear.com/7.x/bottts/svg?seed={f_name}"))
                
                new_parent_id = cursor.lastrowid

                # Hoş geldin kredisi (3 Masal, 1 Etkileşimli)
                cursor.execute("""
                    INSERT INTO credit_wallets (parent_id, story_credit_balance, interactive_credit_balance)
                    VALUES (?, 3, 1)
                """, (new_parent_id,))

                conn.commit()

                token = create_jwt(email, "PARENT", "ROLE_PARENT", new_parent_id)

                self._send_json(201, {
                    "token": token,
                    "tokenType": "Bearer",
                    "userType": "PARENT",
                    "id": new_parent_id,
                    "email": email,
                    "fullName": f"{f_name} {l_name}",
                    "role": "ROLE_PARENT",
                    "storyCredits": 3,
                    "interactiveCredits": 1
                })

            elif self.path == "/api/auth/admin/login":
                identifier = body.get("usernameOrEmail", "").strip()
                password = body.get("password", "")

                cursor.execute("""
                    SELECT a.id, a.username, a.email, a.password_hash, a.full_name, r.name, a.is_active 
                    FROM admins a 
                    JOIN roles r ON a.role_id = r.id 
                    WHERE a.username = ? OR a.email = ?
                """, (identifier, identifier))
                admin = cursor.fetchone()

                if not admin:
                    self._send_json(401, {"error": "Yönetici bilgileri veya şifre hatalı"})
                    return

                admin_id, u_name, a_email, a_hash, full_name, r_name, is_active = admin

                if not is_active:
                    self._send_json(401, {"error": "Bu yönetici hesabı devre dışı bırakılmıştır."})
                    return

                if not bcrypt.checkpw(password.encode("utf-8"), a_hash.encode("utf-8")):
                    self._send_json(401, {"error": "Yönetici bilgileri veya şifre hatalı"})
                    return

                token = create_jwt(u_name, "ADMIN", r_name, admin_id)

                self._send_json(200, {
                    "token": token,
                    "tokenType": "Bearer",
                    "userType": "ADMIN",
                    "id": admin_id,
                    "email": a_email,
                    "username": u_name,
                    "fullName": full_name,
                    "role": r_name
                })
            else:
                self._send_json(404, {"error": "Bilinmeyen uç nokta"})
        finally:
            conn.close()

if __name__ == "__main__":
    server = HTTPServer(("0.0.0.0", 8080), AuthHandler)
    print("API Sunucusu http://localhost:8080 adresinde hazır!")
    server.serve_forever()
