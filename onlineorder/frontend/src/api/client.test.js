import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  ApiError,
  addItemToCart,
  getRestaurants,
  login,
  resetClientForTests,
  signup
} from "./client";

function jsonResponse(payload, status = 200, contentType = "application/json") {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { "Content-Type": contentType }
  });
}

describe("API client", () => {
  beforeEach(() => {
    resetClientForTests();
    vi.restoreAllMocks();
  });

  it("loads a CSRF token before signup and sends snake-case JSON", async () => {
    const fetchMock = vi.spyOn(global, "fetch")
      .mockResolvedValueOnce(jsonResponse({
        token: "secure-token",
        header_name: "X-XSRF-TOKEN",
        parameter_name: "_csrf"
      }))
      .mockResolvedValueOnce(new Response(null, { status: 201 }));

    await signup({
      email: "person@example.com",
      password: "safe-password",
      first_name: "Pat",
      last_name: "Lee"
    });

    expect(fetchMock).toHaveBeenNthCalledWith(1, "/auth/csrf", expect.any(Object));
    expect(fetchMock).toHaveBeenNthCalledWith(2, "/signup", expect.objectContaining({
      method: "POST",
      body: JSON.stringify({
        email: "person@example.com",
        password: "safe-password",
        first_name: "Pat",
        last_name: "Lee"
      }),
      headers: expect.objectContaining({ "X-XSRF-TOKEN": "secure-token" })
    }));
  });

  it("sends login credentials in a form body rather than the URL", async () => {
    const fetchMock = vi.spyOn(global, "fetch")
      .mockResolvedValueOnce(jsonResponse({
        token: "secure-token",
        header_name: "X-XSRF-TOKEN",
        parameter_name: "_csrf"
      }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));

    await login({ email: "person@example.com", password: "safe-password" });

    const [url, options] = fetchMock.mock.calls[1];
    expect(url).toBe("/login");
    expect(String(options.body)).toBe("email=person%40example.com&password=safe-password");
    expect(url).not.toContain("password");
  });

  it("reuses the CSRF token for subsequent cart mutations", async () => {
    const fetchMock = vi.spyOn(global, "fetch")
      .mockResolvedValueOnce(jsonResponse({
        token: "secure-token",
        header_name: "X-XSRF-TOKEN",
        parameter_name: "_csrf"
      }))
      .mockResolvedValue(new Response(null, { status: 204 }));

    await addItemToCart(4);
    await addItemToCart(7);

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[1][1].body).toBe('{"menu_id":4}');
    expect(fetchMock.mock.calls[2][1].body).toBe('{"menu_id":7}');
  });

  it("deduplicates CSRF initialization for concurrent mutations", async () => {
    const fetchMock = vi.spyOn(global, "fetch")
      .mockResolvedValueOnce(jsonResponse({
        token: "shared-token",
        header_name: "X-XSRF-TOKEN",
        parameter_name: "_csrf"
      }))
      .mockResolvedValue(new Response(null, { status: 204 }));

    await Promise.all([addItemToCart(4), addItemToCart(7)]);

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[1][1].headers["X-XSRF-TOKEN"]).toBe("shared-token");
    expect(fetchMock.mock.calls[2][1].headers["X-XSRF-TOKEN"]).toBe("shared-token");
  });

  it("parses successful JSON responses", async () => {
    vi.spyOn(global, "fetch").mockResolvedValueOnce(jsonResponse([
      { id: 1, name: "Cafe", menu_items: [] }
    ]));

    await expect(getRestaurants()).resolves.toEqual([
      { id: 1, name: "Cafe", menu_items: [] }
    ]);
  });

  it("surfaces Problem Detail messages", async () => {
    vi.spyOn(global, "fetch").mockResolvedValueOnce(jsonResponse(
      { title: "Not found", detail: "Restaurant 99 was not found" },
      404,
      "application/problem+json"
    ));

    await expect(getRestaurants()).rejects.toEqual(
      new ApiError("Restaurant 99 was not found", 404)
    );
  });
});
