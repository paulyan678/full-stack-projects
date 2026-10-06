package repository

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"socialai/internal/model"
)

func TestElasticsearchHTTPContractsAndOwnership(t *testing.T) {
	deleteCalls := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		user, pass, ok := r.BasicAuth()
		if !ok || user != "fixture-user" || pass != "fixture-password" {
			t.Error("missing basic auth")
		}
		w.Header().Set("Content-Type", "application/json")
		switch {
		case r.Method == http.MethodHead:
			w.WriteHeader(http.StatusNotFound)
		case r.Method == http.MethodPut && !strings.Contains(r.URL.Path, "/_"):
			var mapping map[string]any
			if json.NewDecoder(r.Body).Decode(&mapping) != nil || mapping["mappings"] == nil {
				t.Error("missing index mapping")
			}
			w.WriteHeader(http.StatusOK)
		case strings.Contains(r.URL.Path, "/_create/"):
			if r.URL.Query().Get("refresh") != "wait_for" {
				t.Error("writes must wait for search visibility")
			}
			w.WriteHeader(http.StatusConflict)
		case r.Method == http.MethodGet:
			_, _ = w.Write([]byte(`{"_source":{"id":"p1","user":"owner","message":"blue sky"}}`))
		case r.Method == http.MethodPost && strings.HasSuffix(r.URL.Path, "/_search"):
			var payload struct {
				Size  int `json:"size"`
				Query struct {
					Bool struct {
						Must []map[string]any `json:"must"`
					} `json:"bool"`
				} `json:"query"`
			}
			if err := json.NewDecoder(r.Body).Decode(&payload); err != nil {
				t.Error(err)
			}
			if payload.Size != 50 || len(payload.Query.Bool.Must) != 2 {
				t.Errorf("unexpected search payload %+v", payload)
			}
			match := payload.Query.Bool.Must[1]["match"].(map[string]any)["message"].(map[string]any)
			if match["operator"] != "and" || match["query"] != "blue sky" {
				t.Error("all-term search drift")
			}
			_, _ = w.Write([]byte(`{"hits":{"hits":[{"_source":{"id":"p1","user":"owner","message":"blue sky"}}]}}`))
		case r.Method == http.MethodDelete:
			deleteCalls++
			w.WriteHeader(http.StatusOK)
		default:
			t.Errorf("unexpected route %s %s", r.Method, r.URL)
			w.WriteHeader(500)
		}
	}))
	defer server.Close()
	repo, err := NewElasticsearch(context.Background(), server.URL, "fixture-user", "fixture-password", "test")
	if err != nil {
		t.Fatal(err)
	}
	if err := repo.CreateUser(context.Background(), model.User{Username: "Owner"}); !errors.Is(err, ErrConflict) {
		t.Fatalf("conflict=%v", err)
	}
	posts, err := repo.SearchPosts(context.Background(), model.SearchFilter{User: "Owner", Keywords: "blue sky", Limit: 999})
	if err != nil || len(posts) != 1 || posts[0].ID != "p1" {
		t.Fatalf("posts=%v err=%v", posts, err)
	}
	if err := repo.DeletePost(context.Background(), "p1", "intruder"); !errors.Is(err, ErrNotFound) {
		t.Fatalf("cross-owner delete=%v", err)
	}
	if deleteCalls != 0 {
		t.Fatal("cross-owner request reached DELETE")
	}
	if err := repo.DeletePost(context.Background(), "p1", "owner"); err != nil {
		t.Fatal(err)
	}
	if deleteCalls != 1 {
		t.Fatalf("deleteCalls=%d", deleteCalls)
	}
}

func TestElasticsearchThrottlingAndInvalidJSON(t *testing.T) {
	for _, testCase := range []struct {
		name   string
		status int
		body   string
	}{{"throttle", 429, `{}`}, {"malformed", 200, `not json`}} {
		t.Run(testCase.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				if r.Method == http.MethodHead {
					w.WriteHeader(200)
					return
				}
				if testCase.status == 429 {
					w.Header().Set("Retry-After", "5")
				}
				w.WriteHeader(testCase.status)
				_, _ = w.Write([]byte(testCase.body))
			}))
			defer server.Close()
			repo, err := NewElasticsearch(context.Background(), server.URL, "", "", "test")
			if err != nil {
				t.Fatal(err)
			}
			if _, err := repo.SearchPosts(context.Background(), model.SearchFilter{}); err == nil {
				t.Fatal("upstream failure hidden")
			}
		})
	}
}
