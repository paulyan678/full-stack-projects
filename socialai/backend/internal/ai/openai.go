package ai

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"strings"
	"time"
)

type OpenAI struct {
	apiKey         string
	model          string
	baseURL        string
	client         *http.Client
	downloadClient *http.Client
}

func NewOpenAI(apiKey, model, baseURL string) (*OpenAI, error) {
	if strings.TrimSpace(apiKey) == "" {
		return nil, errors.New("OPENAI_API_KEY is required when AI_IMAGE_BACKEND=openai")
	}
	transport := http.DefaultTransport.(*http.Transport).Clone()
	transport.Proxy = nil
	transport.DialContext = safeImageDial
	downloadClient := &http.Client{
		Timeout:   2 * time.Minute,
		Transport: transport,
		CheckRedirect: func(req *http.Request, via []*http.Request) error {
			if len(via) >= 3 {
				return errors.New("too many generated image redirects")
			}
			req.Header.Del("Referer")
			return validateImageURL(req.URL)
		},
	}
	return &OpenAI{
		apiKey: apiKey, model: model, baseURL: strings.TrimRight(baseURL, "/"),
		client: &http.Client{Timeout: 2 * time.Minute}, downloadClient: downloadClient,
	}, nil
}

func (o *OpenAI) Generate(ctx context.Context, prompt string) (Image, error) {
	payload, _ := json.Marshal(map[string]any{"model": o.model, "prompt": prompt, "n": 1, "size": "1024x1024"})
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, o.baseURL+"/images/generations", bytes.NewReader(payload))
	if err != nil {
		return Image{}, err
	}
	req.Header.Set("Authorization", "Bearer "+o.apiKey)
	req.Header.Set("Content-Type", "application/json")
	res, err := o.client.Do(req)
	if err != nil {
		return Image{}, err
	}
	defer res.Body.Close()
	const maxAPIResponse = 42 << 20
	raw, err := io.ReadAll(io.LimitReader(res.Body, maxAPIResponse+1))
	if err != nil {
		return Image{}, err
	}
	if len(raw) > maxAPIResponse {
		return Image{}, errors.New("OpenAI image response is too large")
	}
	if res.StatusCode < 200 || res.StatusCode >= 300 {
		var apiError struct {
			Error struct {
				Message string `json:"message"`
			} `json:"error"`
		}
		_ = json.Unmarshal(raw, &apiError)
		if apiError.Error.Message == "" {
			apiError.Error.Message = http.StatusText(res.StatusCode)
		}
		return Image{}, fmt.Errorf("OpenAI image generation failed: %s", apiError.Error.Message)
	}
	var response struct {
		Data []struct {
			B64JSON string `json:"b64_json"`
			URL     string `json:"url"`
		} `json:"data"`
	}
	if err := json.Unmarshal(raw, &response); err != nil || len(response.Data) == 0 {
		return Image{}, errors.New("OpenAI returned no image")
	}
	if response.Data[0].B64JSON != "" {
		if len(response.Data[0].B64JSON) > 40<<20 {
			return Image{}, errors.New("OpenAI base64 image is too large")
		}
		data, err := base64.StdEncoding.DecodeString(response.Data[0].B64JSON)
		if err != nil {
			return Image{}, fmt.Errorf("decode OpenAI image: %w", err)
		}
		if len(data) == 0 || len(data) > 30<<20 {
			return Image{}, errors.New("OpenAI image is empty or too large")
		}
		contentType, extension, err := inspectGeneratedImage(data)
		if err != nil {
			return Image{}, err
		}
		return Image{Data: data, ContentType: contentType, Extension: extension}, nil
	}
	return o.download(ctx, response.Data[0].URL)
}

func (o *OpenAI) download(ctx context.Context, address string) (Image, error) {
	parsed, err := url.Parse(address)
	if err != nil || validateImageURL(parsed) != nil {
		return Image{}, errors.New("OpenAI returned an invalid image URL")
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, parsed.String(), nil)
	if err != nil {
		return Image{}, err
	}
	res, err := o.downloadClient.Do(req)
	if err != nil {
		return Image{}, err
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		return Image{}, fmt.Errorf("download generated image: status %d", res.StatusCode)
	}
	const maxImageBytes = 30 << 20
	data, err := io.ReadAll(io.LimitReader(res.Body, maxImageBytes+1))
	if err != nil {
		return Image{}, err
	}
	if len(data) == 0 || len(data) > maxImageBytes {
		return Image{}, errors.New("generated image is empty or too large")
	}
	contentType, extension, err := inspectGeneratedImage(data)
	if err != nil {
		return Image{}, err
	}
	return Image{Data: data, ContentType: contentType, Extension: extension}, nil
}

func validateImageURL(parsed *url.URL) error {
	if parsed == nil || parsed.Scheme != "https" || parsed.Host == "" || parsed.User != nil {
		return errors.New("generated image URL must be an HTTPS URL without credentials")
	}
	return nil
}

func safeImageDial(ctx context.Context, network, address string) (net.Conn, error) {
	host, port, err := net.SplitHostPort(address)
	if err != nil {
		return nil, fmt.Errorf("parse generated image address: %w", err)
	}
	addresses, err := net.DefaultResolver.LookupIPAddr(ctx, host)
	if err != nil {
		return nil, fmt.Errorf("resolve generated image host: %w", err)
	}
	if len(addresses) == 0 {
		return nil, errors.New("generated image host resolved to no addresses")
	}
	for _, address := range addresses {
		if !isPublicIP(address.IP) {
			return nil, errors.New("generated image URL resolves to a non-public address")
		}
	}
	dialer := net.Dialer{Timeout: 10 * time.Second, KeepAlive: 30 * time.Second}
	var lastErr error
	for _, resolved := range addresses {
		connection, err := dialer.DialContext(ctx, network, net.JoinHostPort(resolved.IP.String(), port))
		if err == nil {
			return connection, nil
		}
		lastErr = err
	}
	return nil, lastErr
}

func isPublicIP(ip net.IP) bool {
	if ip == nil || !ip.IsGlobalUnicast() || ip.IsPrivate() || ip.IsLoopback() || ip.IsUnspecified() || ip.IsLinkLocalUnicast() || ip.IsLinkLocalMulticast() {
		return false
	}
	_, sharedAddressSpace, _ := net.ParseCIDR("100.64.0.0/10")
	return !sharedAddressSpace.Contains(ip)
}

func inspectGeneratedImage(data []byte) (string, string, error) {
	contentType := strings.Split(http.DetectContentType(data), ";")[0]
	extensions := map[string]string{"image/png": ".png", "image/jpeg": ".jpg", "image/webp": ".webp"}
	extension, ok := extensions[contentType]
	if !ok {
		return "", "", errors.New("generated image data is not a supported PNG, JPEG, or WebP image")
	}
	return contentType, extension, nil
}
