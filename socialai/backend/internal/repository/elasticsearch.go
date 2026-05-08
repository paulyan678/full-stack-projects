package repository

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"socialai/internal/model"
)

// Elasticsearch implements Repository against the Elasticsearch 7/8 HTTP API.
// It deliberately uses net/http so local builds do not need a production SDK.
type Elasticsearch struct {
	baseURL string
	user    string
	pass    string
	prefix  string
	client  *http.Client
}

func NewElasticsearch(ctx context.Context, baseURL, user, pass, prefix string) (*Elasticsearch, error) {
	if baseURL == "" {
		return nil, errors.New("ELASTICSEARCH_URL is required")
	}
	e := &Elasticsearch{
		baseURL: strings.TrimRight(baseURL, "/"), user: user, pass: pass,
		prefix: prefix, client: &http.Client{Timeout: 15 * time.Second},
	}
	postMapping := map[string]any{"mappings": map[string]any{"properties": map[string]any{
		"id": map[string]string{"type": "keyword"}, "user": map[string]string{"type": "keyword"},
		"message": map[string]string{"type": "text"}, "url": map[string]any{"type": "keyword", "index": false},
		"type": map[string]string{"type": "keyword"}, "created_at": map[string]string{"type": "date"},
	}}}
	userMapping := map[string]any{"mappings": map[string]any{"properties": map[string]any{
		"username":      map[string]string{"type": "keyword"},
		"password_hash": map[string]any{"type": "keyword", "index": false},
		"created_at":    map[string]string{"type": "date"},
	}}}
	if err := e.ensureIndex(ctx, e.usersIndex(), userMapping); err != nil {
		return nil, err
	}
	if err := e.ensureIndex(ctx, e.postsIndex(), postMapping); err != nil {
		return nil, err
	}
	return e, nil
}

func (e *Elasticsearch) usersIndex() string { return e.prefix + "-users" }
func (e *Elasticsearch) postsIndex() string { return e.prefix + "-posts" }

func (e *Elasticsearch) ensureIndex(ctx context.Context, index string, mapping any) error {
	status, _, err := e.request(ctx, http.MethodHead, "/"+url.PathEscape(index), nil)
	if err != nil {
		return err
	}
	if status == http.StatusOK {
		return nil
	}
	if status != http.StatusNotFound {
		return fmt.Errorf("check Elasticsearch index %q: status %d", index, status)
	}
	status, body, err := e.request(ctx, http.MethodPut, "/"+url.PathEscape(index), mapping)
	if err != nil {
		return err
	}
	if status != http.StatusOK && status != http.StatusCreated {
		return fmt.Errorf("create Elasticsearch index %q: status %d: %s", index, status, body)
	}
	return nil
}

func (e *Elasticsearch) CreateUser(ctx context.Context, user model.User) error {
	status, body, err := e.request(ctx, http.MethodPut, "/"+url.PathEscape(e.usersIndex())+"/_create/"+url.PathEscape(strings.ToLower(user.Username))+"?refresh=wait_for", user)
	if err != nil {
		return err
	}
	if status == http.StatusConflict {
		return ErrConflict
	}
	if status != http.StatusCreated {
		return fmt.Errorf("create user in Elasticsearch: status %d: %s", status, body)
	}
	return nil
}

func (e *Elasticsearch) GetUser(ctx context.Context, username string) (model.User, error) {
	status, body, err := e.request(ctx, http.MethodGet, "/"+url.PathEscape(e.usersIndex())+"/_doc/"+url.PathEscape(strings.ToLower(username)), nil)
	if err != nil {
		return model.User{}, err
	}
	if status == http.StatusNotFound {
		return model.User{}, ErrNotFound
	}
	if status != http.StatusOK {
		return model.User{}, fmt.Errorf("read user from Elasticsearch: status %d: %s", status, body)
	}
	var response struct {
		Source model.User `json:"_source"`
	}
	if err := json.Unmarshal(body, &response); err != nil {
		return model.User{}, err
	}
	return response.Source, nil
}

func (e *Elasticsearch) CreatePost(ctx context.Context, post model.Post) error {
	status, body, err := e.request(ctx, http.MethodPut, "/"+url.PathEscape(e.postsIndex())+"/_create/"+url.PathEscape(post.ID)+"?refresh=wait_for", post)
	if err != nil {
		return err
	}
	if status == http.StatusConflict {
		return ErrConflict
	}
	if status != http.StatusCreated {
		return fmt.Errorf("create post in Elasticsearch: status %d: %s", status, body)
	}
	return nil
}

func (e *Elasticsearch) GetPost(ctx context.Context, id string) (model.Post, error) {
	status, body, err := e.request(ctx, http.MethodGet, "/"+url.PathEscape(e.postsIndex())+"/_doc/"+url.PathEscape(id), nil)
	if err != nil {
		return model.Post{}, err
	}
	if status == http.StatusNotFound {
		return model.Post{}, ErrNotFound
	}
	if status != http.StatusOK {
		return model.Post{}, fmt.Errorf("read post from Elasticsearch: status %d: %s", status, body)
	}
	var response struct {
		Source model.Post `json:"_source"`
	}
	if err := json.Unmarshal(body, &response); err != nil {
		return model.Post{}, err
	}
	return response.Source, nil
}

func (e *Elasticsearch) SearchPosts(ctx context.Context, filter model.SearchFilter) ([]model.Post, error) {
	limit := filter.Limit
	if limit <= 0 || limit > 100 {
		limit = 50
	}
	must := make([]any, 0, 2)
	if filter.User != "" {
		must = append(must, map[string]any{"term": map[string]string{"user": strings.ToLower(filter.User)}})
	}
	if strings.TrimSpace(filter.Keywords) != "" {
		must = append(must, map[string]any{"match": map[string]any{"message": map[string]string{"query": filter.Keywords, "operator": "and"}}})
	}
	query := any(map[string]any{"match_all": map[string]any{}})
	if len(must) > 0 {
		query = map[string]any{"bool": map[string]any{"must": must}}
	}
	payload := map[string]any{"size": limit, "sort": []any{map[string]any{"created_at": map[string]string{"order": "desc"}}}, "query": query}
	status, body, err := e.request(ctx, http.MethodPost, "/"+url.PathEscape(e.postsIndex())+"/_search", payload)
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, fmt.Errorf("search Elasticsearch: status %d: %s", status, body)
	}
	var response struct {
		Hits struct {
			Hits []struct {
				Source model.Post `json:"_source"`
			} `json:"hits"`
		} `json:"hits"`
	}
	if err := json.Unmarshal(body, &response); err != nil {
		return nil, err
	}
	posts := make([]model.Post, 0, len(response.Hits.Hits))
	for _, hit := range response.Hits.Hits {
		posts = append(posts, hit.Source)
	}
	return posts, nil
}

func (e *Elasticsearch) DeletePost(ctx context.Context, id, username string) error {
	post, err := e.GetPost(ctx, id)
	if err != nil {
		return err
	}
	if post.User != username {
		return ErrNotFound
	}
	status, body, err := e.request(ctx, http.MethodDelete, "/"+url.PathEscape(e.postsIndex())+"/_doc/"+url.PathEscape(id)+"?refresh=wait_for", nil)
	if err != nil {
		return err
	}
	if status == http.StatusNotFound {
		return ErrNotFound
	}
	if status != http.StatusOK {
		return fmt.Errorf("delete post from Elasticsearch: status %d: %s", status, body)
	}
	return nil
}

func (e *Elasticsearch) Close() error { return nil }

func (e *Elasticsearch) request(ctx context.Context, method, path string, payload any) (int, []byte, error) {
	var body io.Reader
	if payload != nil {
		raw, err := json.Marshal(payload)
		if err != nil {
			return 0, nil, err
		}
		body = bytes.NewReader(raw)
	}
	req, err := http.NewRequestWithContext(ctx, method, e.baseURL+path, body)
	if err != nil {
		return 0, nil, err
	}
	if payload != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	if e.user != "" {
		req.SetBasicAuth(e.user, e.pass)
	}
	res, err := e.client.Do(req)
	if err != nil {
		return 0, nil, err
	}
	defer res.Body.Close()
	raw, err := io.ReadAll(io.LimitReader(res.Body, 2<<20))
	if err != nil {
		return 0, nil, err
	}
	if retry := res.Header.Get("Retry-After"); res.StatusCode == http.StatusTooManyRequests && retry != "" {
		if seconds, err := strconv.Atoi(retry); err == nil {
			return res.StatusCode, raw, fmt.Errorf("Elasticsearch throttled request for %d seconds", seconds)
		}
	}
	return res.StatusCode, raw, nil
}
