import { useState, type ReactNode } from "react";
import { CONCEPTS, getConcept } from "./concept";
import { DoorBrutalist } from "./DoorBrutalist";
import { DoorWarm } from "./DoorWarm";
import { ThemePicker, type Skin } from "./ThemePicker";
import { brutalistVars, warmVars, type ThemeChoice } from "./palettes";

// Door theme playground (/door-compare). Both layouts + live recolor + concept
// switcher, all driven from the brand-row circle. Query seeds the initial view:
//   ?skin=warm   ?concept=mergesort|roundrobin|bayes|recurrence
export function DoorCompare(): ReactNode {
  const q = new URLSearchParams(window.location.search);
  const [skin, setSkin] = useState<Skin>(q.get("skin") === "warm" ? "warm" : "brutalist");
  const [conceptId, setConceptId] = useState<string>(getConcept(q.get("concept")).id);
  const [choice, setChoice] = useState<ThemeChoice>({
    paletteId: skin === "warm" ? "coral-teal" : "brand-yellow",
  });

  const concept = getConcept(conceptId);

  const picker = (
    <ThemePicker
      skin={skin}
      onSkin={(s) => {
        setSkin(s);
        setChoice((c) =>
          c.customPrimary
            ? c
            : { paletteId: s === "warm" ? "coral-teal" : "brand-yellow" },
        );
      }}
      choice={choice}
      onChoice={setChoice}
      concepts={CONCEPTS.map((c) => ({ id: c.id, label: `${c.titleTop} ${c.titleAccent}` }))}
      conceptId={conceptId}
      onConcept={setConceptId}
    />
  );

  return skin === "warm" ? (
    <DoorWarm concept={concept} theme={warmVars(choice)} brandMark={picker} />
  ) : (
    <DoorBrutalist concept={concept} theme={brutalistVars(choice)} brandMark={picker} />
  );
}
