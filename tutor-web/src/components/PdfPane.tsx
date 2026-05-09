export function PdfPane({ url }: { url: string }) {
  return (
    <div data-testid="pdf-pane" className="h-full bg-zinc-50 border-r-4 border-black overflow-auto">
      <embed src={url} type="application/pdf" className="w-full h-full" />
    </div>
  );
}
