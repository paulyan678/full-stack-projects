package repository

import (
	"context"
	"sort"
	"strings"
	"sync"

	"socialai/internal/model"
)

type Memory struct {
	mu    sync.RWMutex
	users map[string]model.User
	posts map[string]model.Post
}

func NewMemory() *Memory {
	return &Memory{users: make(map[string]model.User), posts: make(map[string]model.Post)}
}

func (m *Memory) CreateUser(_ context.Context, user model.User) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	key := strings.ToLower(user.Username)
	if _, ok := m.users[key]; ok {
		return ErrConflict
	}
	m.users[key] = user
	return nil
}

func (m *Memory) GetUser(_ context.Context, username string) (model.User, error) {
	m.mu.RLock()
	defer m.mu.RUnlock()
	user, ok := m.users[strings.ToLower(username)]
	if !ok {
		return model.User{}, ErrNotFound
	}
	return user, nil
}

func (m *Memory) CreatePost(_ context.Context, post model.Post) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	if _, ok := m.posts[post.ID]; ok {
		return ErrConflict
	}
	m.posts[post.ID] = post
	return nil
}

func (m *Memory) GetPost(_ context.Context, id string) (model.Post, error) {
	m.mu.RLock()
	defer m.mu.RUnlock()
	post, ok := m.posts[id]
	if !ok {
		return model.Post{}, ErrNotFound
	}
	return post, nil
}

func (m *Memory) SearchPosts(_ context.Context, filter model.SearchFilter) ([]model.Post, error) {
	m.mu.RLock()
	defer m.mu.RUnlock()
	keywords := strings.Fields(strings.ToLower(filter.Keywords))
	posts := make([]model.Post, 0, len(m.posts))
	for _, post := range m.posts {
		if filter.User != "" && !strings.EqualFold(post.User, filter.User) {
			continue
		}
		message := strings.ToLower(post.Message)
		matches := true
		for _, keyword := range keywords {
			if !strings.Contains(message, keyword) {
				matches = false
				break
			}
		}
		if matches {
			posts = append(posts, post)
		}
	}
	sort.Slice(posts, func(i, j int) bool { return posts[i].CreatedAt.After(posts[j].CreatedAt) })
	limit := filter.Limit
	if limit <= 0 || limit > 100 {
		limit = 50
	}
	if len(posts) > limit {
		posts = posts[:limit]
	}
	return posts, nil
}

func (m *Memory) DeletePost(_ context.Context, id, username string) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	post, ok := m.posts[id]
	if !ok || post.User != username {
		return ErrNotFound
	}
	delete(m.posts, id)
	return nil
}

func (m *Memory) Close() error { return nil }
