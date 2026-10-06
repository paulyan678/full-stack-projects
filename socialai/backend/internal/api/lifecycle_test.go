package api

import (
	"context"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"sync/atomic"
	"testing"
	"time"

	"socialai/internal/ai"
	"socialai/internal/auth"
	"socialai/internal/media"
	"socialai/internal/model"
	"socialai/internal/repository"
)

type rejectingRepository struct {
	repository.Repository
	reject atomic.Bool
}

func (r *rejectingRepository) CreatePost(ctx context.Context, post model.Post) error {
	if r.reject.Load() {
		return errors.New("synthetic persistence failure")
	}
	return r.Repository.CreatePost(ctx, post)
}

type rejectingStorage struct {
	media.Storage
	rejectDelete atomic.Bool
}

func (s *rejectingStorage) Delete(ctx context.Context, key string) error {
	if s.rejectDelete.Load() {
		return errors.New("synthetic storage failure")
	}
	return s.Storage.Delete(ctx, key)
}
func lifecycleApp(t *testing.T) (testApp, *rejectingRepository, *rejectingStorage) {
	t.Helper()
	local, err := media.NewLocal(filepath.Join(t.TempDir(), "media"), "http://assets.test")
	if err != nil {
		t.Fatal(err)
	}
	repo := &rejectingRepository{Repository: repository.NewMemory()}
	storage := &rejectingStorage{Storage: local}
	tokens := auth.NewTokenManager([]byte("01234567890123456789012345678901"), time.Hour)
	service := New(repo, storage, ai.Local{}, tokens, Settings{MaxUploadBytes: 2 << 20}, slog.New(slog.NewTextHandler(io.Discard, nil)))
	server := httptest.NewServer(service.Handler(local.Handler()))
	t.Cleanup(server.Close)
	return testApp{server: server, tokens: tokens}, repo, storage
}
func TestFailedPublishPreservesPreviewForRetryAndPublishesOnlyOnce(t *testing.T) {
	app, repo, _ := lifecycleApp(t)
	token, _ := app.tokens.Issue("owner")
	generated := decode[struct{ ID string }](t, app.json(t, "POST", "/api/ai/images", token, `{"prompt":"blue garden"}`))
	repo.reject.Store(true)
	response := app.json(t, "POST", "/api/ai/images/"+generated.ID+"/publish", token, `{}`)
	if response.StatusCode != 500 {
		t.Fatalf("failed publish=%d", response.StatusCode)
	}
	response.Body.Close()
	repo.reject.Store(false)
	response = app.json(t, "POST", "/api/ai/images/"+generated.ID+"/publish", token, `{}`)
	if response.StatusCode != 201 {
		t.Fatalf("retry publish=%d", response.StatusCode)
	}
	response.Body.Close()
	response = app.json(t, "POST", "/api/ai/images/"+generated.ID+"/publish", token, `{}`)
	if response.StatusCode != 404 {
		t.Fatalf("duplicate publish=%d", response.StatusCode)
	}
	response.Body.Close()
	posts, err := repo.SearchPosts(context.Background(), model.SearchFilter{})
	if err != nil || len(posts) != 1 {
		t.Fatalf("posts=%v error=%v", posts, err)
	}
}
func TestFailedDiscardPreservesOwnershipAndCanBeRetried(t *testing.T) {
	app, _, storage := lifecycleApp(t)
	token, _ := app.tokens.Issue("owner")
	other, _ := app.tokens.Issue("other")
	generated := decode[struct{ ID string }](t, app.json(t, "POST", "/api/ai/images", token, `{"prompt":"blue garden"}`))
	storage.rejectDelete.Store(true)
	response := app.json(t, http.MethodDelete, "/api/ai/images/"+generated.ID, token, "")
	if response.StatusCode != 500 {
		t.Fatalf("failed discard=%d", response.StatusCode)
	}
	response.Body.Close()
	response = app.json(t, http.MethodDelete, "/api/ai/images/"+generated.ID, other, "")
	if response.StatusCode != 404 {
		t.Fatalf("cross-owner retry=%d", response.StatusCode)
	}
	response.Body.Close()
	storage.rejectDelete.Store(false)
	response = app.json(t, http.MethodDelete, "/api/ai/images/"+generated.ID, token, "")
	if response.StatusCode != 204 {
		t.Fatalf("retry discard=%d", response.StatusCode)
	}
	response.Body.Close()
}
