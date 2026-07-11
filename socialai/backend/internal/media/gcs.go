package media

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"
)

// GCS uses the Google Cloud Storage JSON API. On GCE/GAE it obtains an access
// token from the metadata server; GCS_BEARER_TOKEN is convenient elsewhere.
type GCS struct {
	bucket        string
	explicitToken string
	client        *http.Client
	mu            sync.Mutex
	metadataToken string
	metadataExp   time.Time
}

func NewGCS(bucket, token string) (*GCS, error) {
	if strings.TrimSpace(bucket) == "" {
		return nil, fmt.Errorf("GCS_BUCKET is required")
	}
	return &GCS{bucket: bucket, explicitToken: token, client: &http.Client{Timeout: 60 * time.Second}}, nil
}

func (g *GCS) Save(ctx context.Context, key, contentType string, source io.Reader) (Object, error) {
	token, err := g.token(ctx)
	if err != nil {
		return Object{}, err
	}
	endpoint := "https://storage.googleapis.com/upload/storage/v1/b/" + url.PathEscape(g.bucket) + "/o?uploadType=media&name=" + url.QueryEscape(key)
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, endpoint, source)
	if err != nil {
		return Object{}, err
	}
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", contentType)
	res, err := g.client.Do(req)
	if err != nil {
		return Object{}, err
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(io.LimitReader(res.Body, 16<<10))
		return Object{}, fmt.Errorf("GCS upload failed with status %d: %s", res.StatusCode, body)
	}
	return Object{Key: key, URL: "https://storage.googleapis.com/" + url.PathEscape(g.bucket) + "/" + url.PathEscape(key)}, nil
}

func (g *GCS) Delete(ctx context.Context, key string) error {
	token, err := g.token(ctx)
	if err != nil {
		return err
	}
	endpoint := "https://storage.googleapis.com/storage/v1/b/" + url.PathEscape(g.bucket) + "/o/" + url.PathEscape(key)
	req, err := http.NewRequestWithContext(ctx, http.MethodDelete, endpoint, nil)
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "Bearer "+token)
	res, err := g.client.Do(req)
	if err != nil {
		return err
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusNoContent && res.StatusCode != http.StatusNotFound {
		return fmt.Errorf("GCS delete failed with status %d", res.StatusCode)
	}
	return nil
}

func (g *GCS) token(ctx context.Context) (string, error) {
	if g.explicitToken != "" {
		return g.explicitToken, nil
	}
	g.mu.Lock()
	defer g.mu.Unlock()
	if g.metadataToken != "" && time.Until(g.metadataExp) > time.Minute {
		return g.metadataToken, nil
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token", nil)
	if err != nil {
		return "", err
	}
	req.Header.Set("Metadata-Flavor", "Google")
	client := &http.Client{Timeout: 3 * time.Second}
	res, err := client.Do(req)
	if err != nil {
		return "", fmt.Errorf("get GCS metadata token (or set GCS_BEARER_TOKEN): %w", err)
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		_, _ = io.Copy(io.Discard, io.LimitReader(res.Body, 16<<10))
		return "", fmt.Errorf("GCS metadata token request failed with status %d", res.StatusCode)
	}
	var payload struct {
		AccessToken string `json:"access_token"`
		ExpiresIn   int    `json:"expires_in"`
	}
	// Avoid importing a broad SDK while still using the standard ADC metadata path.
	if err := json.NewDecoder(io.LimitReader(res.Body, 16<<10)).Decode(&payload); err != nil || payload.AccessToken == "" || payload.ExpiresIn <= 0 {
		return "", fmt.Errorf("invalid metadata token response")
	}
	g.metadataToken = payload.AccessToken
	g.metadataExp = time.Now().Add(time.Duration(payload.ExpiresIn) * time.Second)
	return g.metadataToken, nil
}
