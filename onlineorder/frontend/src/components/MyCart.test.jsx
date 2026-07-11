import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import MyCart from "./MyCart";
import { getCart } from "../api/client";

vi.mock("../api/client", () => ({
  checkout: vi.fn(),
  getCart: vi.fn()
}));

beforeEach(() => {
  getCart.mockResolvedValue({
    id: 1,
    total_price: 12,
    order_items: [{ order_item_id: 3, quantity: 2 }]
  });
});

it("loads and refreshes the badge even while the drawer is closed", async () => {
  const { rerender } = render(<MyCart refreshKey={0} />);

  await waitFor(() => expect(getCart).toHaveBeenCalledTimes(1));
  expect(await screen.findByText("2")).toBeInTheDocument();

  rerender(<MyCart refreshKey={1} />);
  await waitFor(() => expect(getCart).toHaveBeenCalledTimes(2));
});
