package ai

import "context"

type Image struct {
	Data        []byte
	ContentType string
	Extension   string
}

type Generator interface {
	Generate(context.Context, string) (Image, error)
}
