package media

import (
	"context"
	"io"
)

type Object struct {
	Key string `json:"key"`
	URL string `json:"url"`
}

type Storage interface {
	Save(context.Context, string, string, io.Reader) (Object, error)
	Delete(context.Context, string) error
}
