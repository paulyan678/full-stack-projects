package auth

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"strings"
	"time"
)

var ErrInvalidToken = errors.New("invalid or expired token")

type TokenManager struct {
	secret []byte
	ttl    time.Duration
	now    func() time.Time
}

type Claims struct {
	Subject  string `json:"sub"`
	Issuer   string `json:"iss"`
	IssuedAt int64  `json:"iat"`
	Expires  int64  `json:"exp"`
}

func NewTokenManager(secret []byte, ttl time.Duration) *TokenManager {
	return &TokenManager{secret: append([]byte(nil), secret...), ttl: ttl, now: time.Now}
}

func (m *TokenManager) Issue(username string) (string, error) {
	now := m.now().UTC()
	header, _ := json.Marshal(map[string]string{"alg": "HS256", "typ": "JWT"})
	claims, err := json.Marshal(Claims{Subject: username, Issuer: "socialai", IssuedAt: now.Unix(), Expires: now.Add(m.ttl).Unix()})
	if err != nil {
		return "", err
	}
	unsigned := encode(header) + "." + encode(claims)
	return unsigned + "." + encode(m.sign(unsigned)), nil
}

func (m *TokenManager) Validate(token string) (Claims, error) {
	parts := strings.Split(token, ".")
	if len(parts) != 3 {
		return Claims{}, ErrInvalidToken
	}
	var header struct {
		Algorithm string `json:"alg"`
		Type      string `json:"typ"`
	}
	headerRaw, err := base64.RawURLEncoding.DecodeString(parts[0])
	if err != nil || json.Unmarshal(headerRaw, &header) != nil || header.Algorithm != "HS256" || header.Type != "JWT" {
		return Claims{}, ErrInvalidToken
	}
	signature, err := base64.RawURLEncoding.DecodeString(parts[2])
	if err != nil || !hmac.Equal(signature, m.sign(parts[0]+"."+parts[1])) {
		return Claims{}, ErrInvalidToken
	}
	payload, err := base64.RawURLEncoding.DecodeString(parts[1])
	if err != nil {
		return Claims{}, ErrInvalidToken
	}
	var claims Claims
	if json.Unmarshal(payload, &claims) != nil || claims.Subject == "" || claims.Issuer != "socialai" || claims.Expires <= m.now().Unix() {
		return Claims{}, ErrInvalidToken
	}
	return claims, nil
}

func (m *TokenManager) sign(value string) []byte {
	mac := hmac.New(sha256.New, m.secret)
	mac.Write([]byte(value))
	return mac.Sum(nil)
}

func encode(value []byte) string { return base64.RawURLEncoding.EncodeToString(value) }
