package repository

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sync"

	"socialai/internal/model"
)

type fileData struct {
	Users []model.User `json:"users"`
	Posts []model.Post `json:"posts"`
}

// File adds crash-safe JSON persistence to the in-memory repository.
type File struct {
	*Memory
	path    string
	writeMu sync.Mutex
}

func NewFile(path string) (*File, error) {
	f := &File{Memory: NewMemory(), path: path}
	raw, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return f, nil
		}
		return nil, err
	}
	var data fileData
	if err := json.Unmarshal(raw, &data); err != nil {
		return nil, fmt.Errorf("decode repository file: %w", err)
	}
	for _, user := range data.Users {
		f.Memory.users[normalize(user.Username)] = user
	}
	for _, post := range data.Posts {
		f.Memory.posts[post.ID] = post
	}
	return f, nil
}

func normalize(value string) string {
	for i := range value {
		if value[i] >= 'A' && value[i] <= 'Z' {
			return stringLower(value)
		}
	}
	return value
}

func stringLower(value string) string {
	b := []byte(value)
	for i, c := range b {
		if c >= 'A' && c <= 'Z' {
			b[i] = c + ('a' - 'A')
		}
	}
	return string(b)
}

func (f *File) CreateUser(ctx context.Context, user model.User) error {
	f.writeMu.Lock()
	defer f.writeMu.Unlock()
	if err := f.Memory.CreateUser(ctx, user); err != nil {
		return err
	}
	if err := f.persistLocked(); err != nil {
		f.Memory.mu.Lock()
		delete(f.Memory.users, normalize(user.Username))
		f.Memory.mu.Unlock()
		return err
	}
	return nil
}

func (f *File) CreatePost(ctx context.Context, post model.Post) error {
	f.writeMu.Lock()
	defer f.writeMu.Unlock()
	if err := f.Memory.CreatePost(ctx, post); err != nil {
		return err
	}
	if err := f.persistLocked(); err != nil {
		f.Memory.mu.Lock()
		delete(f.Memory.posts, post.ID)
		f.Memory.mu.Unlock()
		return err
	}
	return nil
}

func (f *File) DeletePost(ctx context.Context, id, user string) error {
	f.writeMu.Lock()
	defer f.writeMu.Unlock()
	post, err := f.Memory.GetPost(ctx, id)
	if err != nil {
		return err
	}
	if err := f.Memory.DeletePost(ctx, id, user); err != nil {
		return err
	}
	if err := f.persistLocked(); err != nil {
		f.Memory.mu.Lock()
		f.Memory.posts[id] = post
		f.Memory.mu.Unlock()
		return err
	}
	return nil
}

func (f *File) persistLocked() error {
	f.Memory.mu.RLock()
	data := fileData{Users: make([]model.User, 0, len(f.users)), Posts: make([]model.Post, 0, len(f.posts))}
	for _, user := range f.users {
		data.Users = append(data.Users, user)
	}
	for _, post := range f.posts {
		data.Posts = append(data.Posts, post)
	}
	f.Memory.mu.RUnlock()
	raw, err := json.MarshalIndent(data, "", "  ")
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(f.path), 0o750); err != nil {
		return err
	}
	tmp := f.path + ".tmp"
	if err := os.WriteFile(tmp, raw, 0o600); err != nil {
		return err
	}
	return os.Rename(tmp, f.path)
}
