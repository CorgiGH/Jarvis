/** BilingualText — renders text with glossary term highlighting.
 *
 *  Glossary = hardcoded Set<string> (NOT an API call).
 *  Known terms are wrapped in an accent-colored span with a tooltip.
 *
 *  Usage:
 *    <BilingualText lang="ro">Formula EWMA calculează media mobilă.</BilingualText>
 */
import type { ReactNode } from "react";
import type { Lang } from "./LangToggle";

/** Hardcoded glossary — acronyms and key terms used in AI/ML/CS courses. */
const GLOSSARY_TERMS: Set<string> = new Set([
  "EWMA",
  "FSRS",
  "LLM",
  "API",
  "GRU",
  "LSTM",
  "CNN",
  "RNN",
  "SVM",
  "PCA",
  "KNN",
  "MLE",
  "MAP",
  "EM",
  "MCMC",
  "HMM",
  "CRF",
  "NLP",
  "TF-IDF",
  "BERT",
  "GPT",
  "RL",
  "DQN",
  "PPO",
  "SQL",
  "JSON",
  "HTTP",
  "REST",
  "CSS",
  "HTML",
  "DOM",
  "OOP",
  "SOLID",
]);

/** Glossary tooltip translations (EN label for the RO glossary term). */
const GLOSSARY_EN: Record<string, string> = {
  EWMA: "Exponentially Weighted Moving Average",
  FSRS: "Free Spaced Repetition Scheduler",
  LLM: "Large Language Model",
  NLP: "Natural Language Processing",
  PCA: "Principal Component Analysis",
  SVM: "Support Vector Machine",
  KNN: "K-Nearest Neighbors",
  MLE: "Maximum Likelihood Estimation",
  MAP: "Maximum A Posteriori",
  HMM: "Hidden Markov Model",
  RL: "Reinforcement Learning",
  OOP: "Object-Oriented Programming",
};

interface Props {
  lang: Lang;
  children: string | ReactNode;
}

function highlightGlossary(text: string): ReactNode[] {
  // Split on word boundaries for known terms
  const terms = Array.from(GLOSSARY_TERMS).join("|");
  if (!terms) return [text];

  const regex = new RegExp(`\\b(${terms})\\b`, "g");
  const parts: ReactNode[] = [];
  let lastIndex = 0;
  let match: RegExpExecArray | null;

  while ((match = regex.exec(text)) !== null) {
    if (match.index > lastIndex) {
      parts.push(text.slice(lastIndex, match.index));
    }
    const term = match[1];
    const title = GLOSSARY_EN[term];
    parts.push(
      <span
        key={`${term}-${match.index}`}
        data-glossary-term={term}
        title={title ?? term}
        className="text-accent font-bold cursor-help border-b border-dotted border-accent/50"
      >
        {term}
      </span>,
    );
    lastIndex = regex.lastIndex;
  }

  if (lastIndex < text.length) {
    parts.push(text.slice(lastIndex));
  }

  return parts;
}

export function BilingualText({ children }: Props) {
  // If children is a plain string, apply glossary highlighting
  if (typeof children === "string") {
    const highlighted = highlightGlossary(children);
    return <span>{highlighted}</span>;
  }

  // ReactNode children passed through unchanged
  return <span>{children}</span>;
}
