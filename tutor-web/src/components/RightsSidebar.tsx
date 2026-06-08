/** RightsSidebar — GDPR Art 15/17/18/22 + AI Act rights summary panel.
 *
 *  Rendered inside /me (SettingsMe) as a supplementary dark panel.
 *  Lists user rights with short Romanian descriptions and links to
 *  the corresponding actions (export, pause, delete) where applicable.
 *
 *  testids:
 *   rights-sidebar
 *   gdpr-export-btn
 *   gdpr-delete-btn
 */

interface Right {
  article: string;
  titleRo: string;
  descRo: string;
  action?: "export" | "delete";
}

const RIGHTS: Right[] = [
  {
    article: "Art. 15",
    titleRo: "Dreptul la acces",
    descRo: "Poți obține o copie completă a datelor tale personale.",
    action: "export",
  },
  {
    article: "Art. 17",
    titleRo: "Dreptul la ștergere",
    descRo:
      'Poți solicita ștergerea contului și a tuturor datelor asociate ("dreptul de a fi uitat").',
    action: "delete",
  },
  {
    article: "Art. 18",
    titleRo: "Dreptul la restricționare",
    descRo:
      "Poți solicita suspendarea temporară a procesării datelor tale (butonul PAUSE LOGGING).",
  },
  {
    article: "Art. 22",
    titleRo: "Dreptul de a nu fi supus deciziilor automate",
    descRo:
      "Sistemul Jarvis folosește AI numai ca asistent; nicio decizie cu efecte juridice nu este luată automat.",
  },
  {
    article: "AI Act",
    titleRo: "Transparență sistem AI",
    descRo:
      "Jarvis Tutor este un sistem AI educațional. Orice conținut generat de AI este marcat explicit ca atare.",
  },
];

interface RightsSidebarProps {
  /** Called when user clicks the export action link. */
  onExport?: () => void;
  /** Called when user clicks the delete action link. */
  onDelete?: () => void;
}

export function RightsSidebar({ onExport, onDelete }: RightsSidebarProps) {
  return (
    <aside
      data-testid="rights-sidebar"
      className="border-2 border-border-strong bg-panel-dark-bg text-panel-dark-fg p-4 font-mono"
    >
      <div className="text-xs font-bold tracking-widest mb-3 uppercase text-panel-dark-fg">
        Drepturile tale (GDPR + AI Act)
      </div>

      <ul className="space-y-3">
        {RIGHTS.map((r) => (
          <li key={r.article} className="text-xs leading-relaxed">
            <span className="font-bold text-accent">{r.article}</span>
            {" — "}
            <span className="font-bold text-panel-dark-fg">{r.titleRo}</span>
            <p className="mt-0.5 text-panel-dark-fg/70 text-[11px]">
              {r.descRo}
            </p>
            {r.action === "export" && (
              <a
                href="/api/v1/me/export"
                download
                data-testid="gdpr-export-btn"
                onClick={onExport}
                className="mt-1 inline-block text-[10px] font-bold tracking-widest bg-accent text-page-fg px-3 py-1 hover:bg-accent-hover"
              >
                EXPORT DATE
              </a>
            )}
            {r.action === "delete" && (
              <button
                type="button"
                data-testid="gdpr-delete-btn"
                onClick={onDelete}
                className="mt-1 text-[10px] font-bold tracking-widest text-danger-text border border-danger-text px-3 py-1 hover:bg-danger-text hover:text-page-bg"
              >
                ȘTERGE CONT
              </button>
            )}
          </li>
        ))}
      </ul>
    </aside>
  );
}
