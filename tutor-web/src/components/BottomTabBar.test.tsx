import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { MemoryRouter, useLocation } from "react-router-dom";
import { BottomTabBar } from "./BottomTabBar";

function Wrapper({ children }: { children: React.ReactNode }) {
  return <MemoryRouter initialEntries={["/oggi"]}>{children}</MemoryRouter>;
}

describe("BottomTabBar", () => {
  it("renders bottom-tab-bar testid", () => {
    render(<BottomTabBar />, { wrapper: Wrapper });
    expect(screen.getByTestId("bottom-tab-bar")).toBeInTheDocument();
  });

  it("renders all 4 tabs: azi, materie, jurnal, eu", () => {
    render(<BottomTabBar />, { wrapper: Wrapper });
    expect(screen.getByTestId("tab-azi")).toBeInTheDocument();
    expect(screen.getByTestId("tab-materie")).toBeInTheDocument();
    expect(screen.getByTestId("tab-jurnal")).toBeInTheDocument();
    expect(screen.getByTestId("tab-eu")).toBeInTheDocument();
  });

  it("tab labels are in Romanian uppercase", () => {
    render(<BottomTabBar />, { wrapper: Wrapper });
    expect(screen.getByTestId("tab-azi")).toHaveTextContent("AZI");
    expect(screen.getByTestId("tab-materie")).toHaveTextContent("MATERIE");
    expect(screen.getByTestId("tab-jurnal")).toHaveTextContent("JURNAL");
    expect(screen.getByTestId("tab-eu")).toHaveTextContent("EU");
  });

  it("marks the active tab with data-testid='tab-active' when route matches", () => {
    render(
      <MemoryRouter initialEntries={["/oggi"]}>
        <BottomTabBar />
      </MemoryRouter>,
    );
    // /oggi maps to azi tab
    const aziTab = screen.getByTestId("tab-azi");
    expect(aziTab).toHaveAttribute("aria-current", "page");
  });

  it("renders a tab-active element for the current route", () => {
    render(
      <MemoryRouter initialEntries={["/subjects"]}>
        <BottomTabBar />
      </MemoryRouter>,
    );
    // /subjects maps to materie tab
    const materieTab = screen.getByTestId("tab-materie");
    expect(materieTab).toHaveAttribute("aria-current", "page");
  });

  it("renders tab-active element somewhere", () => {
    render(<BottomTabBar />, { wrapper: Wrapper });
    expect(screen.getByTestId("tab-active")).toBeInTheDocument();
  });
});
