import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import FoodList from "./FoodList";
import { addItemToCart, getMenus, getRestaurants } from "../api/client";

vi.mock("../api/client", () => ({
  addItemToCart: vi.fn(),
  getMenus: vi.fn(),
  getRestaurants: vi.fn()
}));

beforeEach(() => {
  getRestaurants.mockResolvedValue([
    { id: 1, name: "Cafe", address: "1 Main St", phone: "555-0100" }
  ]);
  getMenus.mockResolvedValue([
    {
      id: 8,
      name: "Great Dish",
      description: "Made fresh",
      price: 12.5,
      image_url: "dish.jpg"
    }
  ]);
  addItemToCart.mockResolvedValue(undefined);
});

it("loads the first restaurant menu and adds an item to the cart", async () => {
  const onCartChanged = vi.fn();
  const user = userEvent.setup();
  render(<FoodList onCartChanged={onCartChanged} />);

  expect(await screen.findByRole("heading", { name: "Great Dish" })).toBeInTheDocument();
  expect(getMenus).toHaveBeenCalledWith(1);

  await user.click(screen.getByRole("button", { name: "Add item to cart" }));
  await waitFor(() => expect(addItemToCart).toHaveBeenCalledWith(8));
  expect(onCartChanged).toHaveBeenCalledOnce();
});
