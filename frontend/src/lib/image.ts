const MAX_INPUT_BYTES = 20 * 1024 * 1024;

/**
 * Crops an image file to a centred square and scales it to {@code size} px as a JPEG, in the
 * browser. Uploads stay small (tens of KB instead of a multi-MB phone photo), and the server
 * re-encodes anyway, so this is about speed, not trust.
 */
export async function squareJpeg(file: File, size = 400): Promise<Blob> {
  if (!file.type.startsWith('image/')) throw new Error('Choose an image file.');
  if (file.size > MAX_INPUT_BYTES) throw new Error('That image is over 20 MB.');

  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(file);
  } catch {
    throw new Error("This browser can't read that image. Try a JPEG or PNG.");
  }

  const side = Math.min(bitmap.width, bitmap.height);
  const canvas = document.createElement('canvas');
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('Could not process the image.');
  ctx.fillStyle = '#fff'; // JPEG has no transparency
  ctx.fillRect(0, 0, size, size);
  ctx.imageSmoothingQuality = 'high';
  ctx.drawImage(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side, 0, 0, size, size);
  bitmap.close();

  return new Promise((resolve, reject) =>
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('Could not process the image.'))), 'image/jpeg', 0.9),
  );
}
