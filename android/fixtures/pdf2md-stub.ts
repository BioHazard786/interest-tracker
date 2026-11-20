// Golden-run stand-in for @opendocsg/pdf2md: the "PDF" bytes are already the markdown text.
export default async function pdf2md(buffer: ArrayBuffer | Uint8Array): Promise<string> {
  return new TextDecoder().decode(buffer)
}
