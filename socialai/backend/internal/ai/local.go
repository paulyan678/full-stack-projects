package ai

import (
	"context"
	"fmt"
	"html"
	"strings"
)

// Local creates a deterministic SVG preview so the complete AI workflow can
// be developed without credentials. It is intentionally labelled as a preview.
type Local struct{}

func (Local) Generate(_ context.Context, prompt string) (Image, error) {
	words := strings.Fields(prompt)
	lines := make([]string, 0, 4)
	for len(words) > 0 && len(lines) < 4 {
		take := 7
		if len(words) < take {
			take = len(words)
		}
		lines = append(lines, strings.Join(words[:take], " "))
		words = words[take:]
	}
	var text strings.Builder
	for i, line := range lines {
		fmt.Fprintf(&text, `<text x="512" y="%d" text-anchor="middle" fill="white" font-family="system-ui,sans-serif" font-size="38">%s</text>`, 460+i*52, html.EscapeString(line))
	}
	seed := 0
	for _, value := range []byte(prompt) {
		seed = (seed*31 + int(value)) % 360
	}
	svg := fmt.Sprintf(`<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop stop-color="hsl(%d,75%%,38%%)"/><stop offset="1" stop-color="hsl(%d,78%%,18%%)"/></linearGradient><filter id="blur"><feGaussianBlur stdDeviation="55"/></filter></defs><rect width="1024" height="1024" fill="url(#g)"/><circle cx="250" cy="240" r="190" fill="rgba(255,255,255,.18)" filter="url(#blur)"/><circle cx="820" cy="760" r="250" fill="rgba(255,170,100,.22)" filter="url(#blur)"/><text x="512" y="340" text-anchor="middle" fill="rgba(255,255,255,.72)" font-family="system-ui,sans-serif" font-size="22" letter-spacing="8">LOCAL AI PREVIEW</text>%s</svg>`, seed, (seed+95)%360, text.String())
	return Image{Data: []byte(svg), ContentType: "image/svg+xml", Extension: ".svg"}, nil
}
