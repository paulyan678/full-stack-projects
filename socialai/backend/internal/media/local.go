package media

import (
	"context"
	"errors"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"
)

type Local struct {
	Directory string
	publicURL string
}

func NewLocal(directory, publicURL string) (*Local, error) {
	abs, err := filepath.Abs(directory)
	if err != nil {
		return nil, err
	}
	if err := os.MkdirAll(abs, 0o750); err != nil {
		return nil, err
	}
	return &Local{Directory: abs, publicURL: strings.TrimRight(publicURL, "/")}, nil
}

func (l *Local) Save(_ context.Context, key, _ string, source io.Reader) (Object, error) {
	path, err := l.safePath(key)
	if err != nil {
		return Object{}, err
	}
	file, err := os.OpenFile(path, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o640)
	if err != nil {
		return Object{}, err
	}
	_, copyErr := io.Copy(file, source)
	closeErr := file.Close()
	if copyErr != nil || closeErr != nil {
		_ = os.Remove(path)
		if copyErr != nil {
			return Object{}, copyErr
		}
		return Object{}, closeErr
	}
	return Object{Key: key, URL: l.publicURL + "/media/" + key}, nil
}

func (l *Local) Delete(_ context.Context, key string) error {
	path, err := l.safePath(key)
	if err != nil {
		return err
	}
	if err := os.Remove(path); err != nil && !os.IsNotExist(err) {
		return err
	}
	return nil
}

// Handler serves individual media objects without exposing a directory index.
func (l *Local) Handler() http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		key := strings.TrimPrefix(r.URL.Path, "/")
		file, err := l.safePath(key)
		if err != nil || key == "" {
			http.NotFound(w, r)
			return
		}
		w.Header().Set("Cache-Control", "public, max-age=31536000, immutable")
		http.ServeFile(w, r, file)
	})
}

func (l *Local) safePath(key string) (string, error) {
	if key == "" || filepath.Base(key) != key || strings.ContainsAny(key, `/\\`) {
		return "", errors.New("invalid media key")
	}
	return filepath.Join(l.Directory, key), nil
}
