const MODEL = 'gemini-3.8-flash';
const API_URL = 'https://generativelanguage.googleapis.com/v1beta/interactions';

function imagePayload(dataUrl) {
  if (!dataUrl || !dataUrl.startsWith('data:')) return null;
  const match = dataUrl.match(/^data:([^;,]+);base64,(.+)$/s);
  if (!match) return null;
  return { type: 'image', mime_type: match[1], data: match[2] };
}

function extractText(response) {
  if (typeof response.output_text === 'string') return response.output_text;
  const chunks = [];
  for (const step of response.steps || []) {
    if (step.type === 'model_output') {
      for (const part of step.content || []) {
        if (part.type === 'text' && part.text) chunks.push(part.text);
      }
    }
  }
  return chunks.join('\n');
}

export async function suggestPageTags({ apiKey, imageUrl, fileName }) {
  if (!apiKey?.trim()) throw new Error('Add your Gemini API key in Settings first.');
  const image = imagePayload(imageUrl);
  const prompt = [
    'Analyze this coloring-page image and its original filename.',
    `Filename: ${fileName || 'Unknown'}`,
    'Suggest 3 to 8 concise, useful subject tags based on what is visibly depicted and the filename.',
    'Prioritize concrete subjects (animals, flowers, people, objects, places), themes, and art style when clearly relevant.',
    'Do not guess hidden context. Avoid generic tags such as image, picture, coloring page, or art.',
    'Return only valid JSON in this exact shape: {"tags":["tag one","tag two"]}.',
    'Use lowercase tags, no hashtags, no duplicates.'
  ].join('\n');
  const input = [{ type: 'text', text: prompt }];
  if (image) input.push(image);

  const response = await fetch(API_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'x-goog-api-key': apiKey.trim(),
      'Api-Revision': '2026-05-20',
    },
    body: JSON.stringify({ model: MODEL, input }),
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    const detail = payload.error?.message || `Gemini request failed (${response.status}).`;
    throw new Error(detail);
  }

  const output = extractText(payload);
  const jsonText = output.match(/\{[\s\S]*\}/)?.[0];
  if (!jsonText) throw new Error('Gemini returned an unexpected response. Please try again.');
  let parsed;
  try { parsed = JSON.parse(jsonText); }
  catch { throw new Error('Gemini returned invalid tag data. Please try again.'); }
  const tags = [...new Set((Array.isArray(parsed.tags) ? parsed.tags : [])
    .map(tag => String(tag).trim().toLowerCase().replace(/^#/, ''))
    .filter(tag => tag && tag.length <= 40)
    .slice(0, 8))];
  if (!tags.length) throw new Error('Gemini could not identify any useful subject tags.');
  return tags;
}

export async function scanAndTagPage(page, apiKey) {
  if (!page?.imageUrl) throw new Error('This page has no locally stored image to scan.');
  const suggestedTags = await suggestPageTags({
    apiKey,
    imageUrl: page.imageUrl,
    fileName: page.title,
  });
  const { db } = await import('./db');
  const mergedTags = [...new Set([...(page.tags || []), ...suggestedTags])];
  await db.transaction('rw', db.pages, db.tags, async () => {
    await db.pages.update(page.id, {
      tags: mergedTags,
      aiSuggestedTags: suggestedTags,
      aiScannedAt: Date.now(),
      updatedAt: Date.now(),
    });
    for (const name of suggestedTags) {
      const exists = await db.tags.where('name').equals(name).first();
      if (!exists) await db.tags.add({ name, createdAt: Date.now() });
    }
  });
  return suggestedTags;
}
