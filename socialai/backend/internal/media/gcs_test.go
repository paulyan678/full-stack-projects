package media

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"strings"
	"testing"
	"time"
)

type transportFunc func(*http.Request) (*http.Response, error)

func (f transportFunc) RoundTrip(r *http.Request) (*http.Response, error) { return f(r) }
func cloudResponse(status int, body string) *http.Response {
	return &http.Response{StatusCode: status, Body: io.NopCloser(strings.NewReader(body)), Header: make(http.Header)}
}

func TestGCSUploadDeleteHTTPContract(t *testing.T) {
	g, err := NewGCS("fixture-bucket", "synthetic-token")
	if err != nil {
		t.Fatal(err)
	}
	calls := 0
	g.client.Transport = transportFunc(func(r *http.Request) (*http.Response, error) {
		calls++
		if r.URL.Host != "storage.googleapis.com" || r.Header.Get("Authorization") != "Bearer synthetic-token" {
			t.Errorf("unexpected destination/auth")
		}
		switch r.Method {
		case http.MethodPost:
			if r.URL.Query().Get("name") != "preview/name.png" || r.URL.Query().Get("uploadType") != "media" {
				t.Errorf("wrong object query: %s", r.URL)
			}
			body, _ := io.ReadAll(r.Body)
			if string(body) != "image bytes" || r.Header.Get("Content-Type") != "image/png" {
				t.Errorf("wrong upload payload")
			}
			return cloudResponse(http.StatusOK, `{}`), nil
		case http.MethodDelete:
			if !strings.HasSuffix(r.URL.EscapedPath(), "preview%2Fname.png") {
				t.Errorf("object key not escaped: %s", r.URL.EscapedPath())
			}
			return cloudResponse(http.StatusNotFound, `{}`), nil
		default:
			return nil, fmt.Errorf("unexpected method: %s", r.Method)
		}
	})
	object, err := g.Save(context.Background(), "preview/name.png", "image/png", strings.NewReader("image bytes"))
	if err != nil || object.Key != "preview/name.png" {
		t.Fatalf("object=%+v err=%v", object, err)
	}
	if err := g.Delete(context.Background(), object.Key); err != nil {
		t.Fatal(err)
	}
	if calls != 2 {
		t.Fatalf("calls=%d", calls)
	}
}

func TestGCSMetadataTokenCacheAndUpstreamErrors(t *testing.T) {
	g, _ := NewGCS("fixture-bucket", "")
	metadataCalls := 0
	g.metadataClient.Transport = transportFunc(func(r *http.Request) (*http.Response, error) {
		metadataCalls++
		if r.URL.Host != "metadata.google.internal" || r.Header.Get("Metadata-Flavor") != "Google" {
			t.Errorf("metadata request contract")
		}
		return cloudResponse(http.StatusOK, `{"access_token":"temporary-token","expires_in":3600}`), nil
	})
	g.client.Transport = transportFunc(func(r *http.Request) (*http.Response, error) {
		if r.Header.Get("Authorization") != "Bearer temporary-token" {
			t.Errorf("missing metadata token")
		}
		return cloudResponse(http.StatusForbidden, "denied"), nil
	})
	for i := 0; i < 2; i++ {
		if _, err := g.Save(context.Background(), "x.png", "image/png", strings.NewReader("x")); err == nil {
			t.Fatal("upload failure hidden")
		}
	}
	if metadataCalls != 1 {
		t.Fatalf("metadata token not reused: %d", metadataCalls)
	}
	g.metadataExp = time.Now().Add(-time.Minute)
	if err := g.Delete(context.Background(), "x.png"); err == nil {
		t.Fatal("delete failure hidden")
	}
	if metadataCalls != 2 {
		t.Fatalf("expired metadata token not refreshed: %d", metadataCalls)
	}
}
