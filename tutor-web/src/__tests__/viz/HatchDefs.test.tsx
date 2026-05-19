import { render } from "@testing-library/react";
import { HatchDefs } from "../../components/viz/HatchDefs";
import { HATCH_LIGHT, HATCH_DENSE } from "../../components/viz/theme";

test("theme exports hatch fill tokens", () => {
  expect(HATCH_LIGHT).toBe("url(#hatch-light)");
  expect(HATCH_DENSE).toBe("url(#hatch-dense)");
});

test("HatchDefs renders both pattern elements", () => {
  const { container } = render(
    <svg>
      <HatchDefs />
    </svg>
  );
  expect(container.querySelector("pattern#hatch-light")).not.toBeNull();
  expect(container.querySelector("pattern#hatch-dense")).not.toBeNull();
});
