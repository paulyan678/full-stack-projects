package repository

import (
	"context"
	"errors"

	"socialai/internal/model"
)

var (
	ErrNotFound = errors.New("not found")
	ErrConflict = errors.New("already exists")
)

type Repository interface {
	CreateUser(context.Context, model.User) error
	GetUser(context.Context, string) (model.User, error)
	CreatePost(context.Context, model.Post) error
	GetPost(context.Context, string) (model.Post, error)
	SearchPosts(context.Context, model.SearchFilter) ([]model.Post, error)
	DeletePost(context.Context, string, string) error
	Close() error
}
