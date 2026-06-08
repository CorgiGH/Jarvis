/** BottomTabBar — fixed 56px DARK bottom strip for mobile (<768px).
 *
 *  4 cells: AZI / MATERIE / JURNAL / EU.
 *  Active cell = accent bg.
 *  Rendered only at width < 768px (md:hidden via Tailwind).
 *
 *  Route mapping:
 *   AZI     → /oggi
 *   MATERIE → /subjects
 *   JURNAL  → /review
 *   EU      → /me
 *
 *  testids:
 *   bottom-tab-bar
 *   tab-{name}         — azi / materie / jurnal / eu (on the Link, always)
 *   tab-active         — an accent pip on the active tab
 */
import { Link, useLocation } from "react-router-dom";

interface Tab {
  name: "azi" | "materie" | "jurnal" | "eu";
  label: string;
  to: string;
}

const TABS: Tab[] = [
  { name: "azi", label: "AZI", to: "/oggi" },
  { name: "materie", label: "MATERIE", to: "/subjects" },
  { name: "jurnal", label: "JURNAL", to: "/review" },
  { name: "eu", label: "EU", to: "/me" },
];

export function BottomTabBar() {
  const location = useLocation();

  function isActive(tab: Tab): boolean {
    return location.pathname === tab.to;
  }

  return (
    <nav
      data-testid="bottom-tab-bar"
      aria-label="Navigare principală"
      className="fixed bottom-0 left-0 right-0 h-14 bg-panel-dark-bg border-t-2 border-accent flex md:hidden z-30"
    >
      {TABS.map((tab) => {
        const active = isActive(tab);
        return (
          <Link
            key={tab.name}
            to={tab.to}
            data-testid={`tab-${tab.name}`}
            aria-current={active ? "page" : undefined}
            className={`flex-1 flex flex-col items-center justify-center text-[10px] font-bold tracking-widest font-mono transition-colors relative ${
              active
                ? "bg-accent text-page-fg"
                : "text-panel-dark-fg/70 hover:text-panel-dark-fg"
            }`}
          >
            {/* Active indicator pip — carries tab-active testid */}
            {active && (
              <span
                data-testid="tab-active"
                aria-hidden="true"
                className="absolute top-0 left-0 right-0 h-0.5 bg-page-fg"
              />
            )}
            {tab.label}
          </Link>
        );
      })}
    </nav>
  );
}
