export const openDocumentFieldValue = (filePayload) => {
  const content = String(filePayload?.base64Content || "").trim();
  if (!content) return;

  const binary = window.atob(content);
  const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0));
  const blob = new Blob([bytes], {
    type:
      String(filePayload?.fileType || "").trim() || "application/octet-stream",
  });
  const objectUrl = URL.createObjectURL(blob);
  try {
    window.open(objectUrl, "_blank", "noopener,noreferrer");
  } finally {
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
  }
};
