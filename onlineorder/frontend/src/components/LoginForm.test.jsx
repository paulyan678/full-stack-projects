import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import LoginForm from "./LoginForm";
import { login } from "../api/client";

vi.mock("../api/client", () => ({
  login: vi.fn()
}));

beforeEach(() => {
  login.mockReset();
});

it("submits an email and password and reports the normalized email", async () => {
  login.mockResolvedValue(undefined);
  const onSuccess = vi.fn();
  const user = userEvent.setup();
  render(<LoginForm onSuccess={onSuccess} />);

  await user.type(screen.getByLabelText("Email"), "Person@Example.com");
  await user.type(screen.getByLabelText("Password"), "safe-password");
  await user.click(screen.getByRole("button", { name: "Sign in" }));

  await waitFor(() => expect(login).toHaveBeenCalledWith({
    email: "Person@Example.com",
    password: "safe-password"
  }));
  expect(onSuccess).toHaveBeenCalledWith("person@example.com");
});
