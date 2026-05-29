import { vizRegistry } from "./viz/vizRegistry";

/** E3 routing: mount the registry component for `vizId`, wrapped in a testid'd panel.
 *  Renders null when vizId is absent/unknown (text-only fallback — never a fake box). */
export function RoutedViz({ vizId }: { vizId?: string }) {
  if (!vizId) return null;
  const Component = vizRegistry[vizId];
  if (!Component) return null;
  return (
    <div data-testid={`routed-viz-${vizId}`} className="border-2 border-border-strong p-2">
      <Component />
    </div>
  );
}
