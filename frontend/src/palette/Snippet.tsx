/** Search snippet: the server escapes the text and adds only <mark>; any other tag is neutralised again here. */
export default function Snippet({ html }: { html: string }) {
  if (!html) return null
  return <span className="snippet" dangerouslySetInnerHTML={{ __html: html.replace(/<(?!\/?mark>)/g, '&lt;') }} />
}
