import { PdfPane } from "./PdfPane";
import { ChatPane } from "./ChatPane";

export function TutorWorkspace({ pdfUrl, taskId }: { pdfUrl: string; taskId: string }) {
  return (
    <div className="grid grid-cols-2 h-dvh">
      <PdfPane url={pdfUrl} />
      <ChatPane taskId={taskId} />
    </div>
  );
}
