jest.mock('../src/NativePhotoVideoEditor', () => ({
  __esModule: true,
  default: {
    openEditor: jest.fn(),
    cancelExport: jest.fn(),
    isAvailable: jest.fn(() => true),
    onExportProgress: jest.fn(),
    onEditorEvent: jest.fn(),
  },
}));

import NativePhotoVideoEditor from '../src/NativePhotoVideoEditor';
import {
  addExportProgressListener,
  cancelExport,
  openEditor,
  openPhotoEditor,
  openVideoEditor,
  PhotoVideoEditorError,
} from '../src';

const mockNativeOpenEditor = jest.mocked(NativePhotoVideoEditor.openEditor);
const mockNativeCancelExport = jest.mocked(NativePhotoVideoEditor.cancelExport);
const mockOnExportProgress = jest.mocked(
  NativePhotoVideoEditor.onExportProgress
);
const mockProgressRemove = jest.fn();

beforeEach(() => {
  jest.clearAllMocks();
  mockOnExportProgress.mockReturnValue({ remove: mockProgressRemove });
});

test('normalizes and forwards a valid photo request', async () => {
  mockNativeOpenEditor.mockResolvedValue(
    JSON.stringify({
      uri: 'file:///photo.jpg',
      type: 'photo',
      cancelled: false,
    })
  );
  await expect(
    openEditor({ source: { uri: ' file:///photo.jpg ', type: 'photo' } })
  ).resolves.toEqual({
    uri: 'file:///photo.jpg',
    type: 'photo',
    cancelled: false,
  });
  expect(JSON.parse(mockNativeOpenEditor.mock.calls[0]![0])).toMatchObject({
    features: { crop: true, trim: false },
    export: { quality: 'high', preserveMetadata: true },
    saveToGallery: false,
  });
});

test('accepts a valid video request', async () => {
  mockNativeOpenEditor.mockResolvedValue(
    JSON.stringify({
      uri: 'file:///video.mp4',
      type: 'video',
      cancelled: false,
    })
  );
  await openEditor({
    source: { uri: 'file:///video.mp4', type: 'video' },
    export: { frameRate: 30, videoFormat: 'mp4' },
  });
  expect(mockNativeOpenEditor).toHaveBeenCalledTimes(1);
});

test('routes focused photo and video APIs through the native editor', async () => {
  mockNativeOpenEditor.mockResolvedValue(
    JSON.stringify({ uri: 'file:///x', type: 'photo', cancelled: false })
  );
  await openPhotoEditor({ source: { uri: 'file:///x', type: 'photo' } });
  mockNativeOpenEditor.mockResolvedValue(
    JSON.stringify({ uri: 'file:///x.mp4', type: 'video', cancelled: false })
  );
  await openVideoEditor({ source: { uri: 'file:///x.mp4', type: 'video' } });
  expect(mockNativeOpenEditor).toHaveBeenCalledTimes(2);
});

test.each([
  [{ source: { uri: '', type: 'photo' } }, 'E_INVALID_URI'],
  [{}, 'E_INVALID_OPTIONS'],
  [{ source: { uri: 'file:///x', type: 'audio' } }, 'E_UNSUPPORTED_MEDIA_TYPE'],
  [
    { source: { uri: 'file:///x', type: 'video' }, export: { frameRate: 0 } },
    'E_INVALID_OPTIONS',
  ],
])('rejects invalid input %#', async (value, code) => {
  await expect(openEditor(value as never)).rejects.toMatchObject({ code });
});

test('normalizes native errors', async () => {
  mockNativeOpenEditor.mockRejectedValue({
    code: 'E_PERMISSION_DENIED',
    message: 'Permission denied.',
  });
  await expect(
    openEditor({ source: { uri: 'file:///x', type: 'photo' } })
  ).rejects.toBeInstanceOf(PhotoVideoEditorError);
});

test('forwards cancellation and cleans up listeners', async () => {
  mockNativeCancelExport.mockResolvedValue(false);
  await expect(cancelExport('job-1')).resolves.toBe(false);
  expect(mockNativeCancelExport).toHaveBeenCalledWith('job-1');
  const subscription = addExportProgressListener(jest.fn());
  subscription.remove();
  expect(mockProgressRemove).toHaveBeenCalledTimes(1);
});

test.each(['photo', 'video'] as const)(
  'forwards an initial sticker for %s',
  async (type) => {
    mockNativeOpenEditor.mockResolvedValue(JSON.stringify({ cancelled: true }));
    await openEditor({
      source: { uri: 'file:///media', type },
      initialStickerIds: ['brand'],
      stickerAssets: [{ id: 'brand', uri: 'https://example.com/brand.png' }],
    });
    expect(JSON.parse(mockNativeOpenEditor.mock.calls[0]![0])).toMatchObject({
      initialStickerIds: ['brand'],
      stickerAssets: [{ id: 'brand', uri: 'https://example.com/brand.png' }],
    });
  }
);

test.each([
  {
    initialStickerIds: ['brand', 'brand'],
    stickerAssets: [{ id: 'brand', uri: '/a.png' }],
  },
  { initialStickerIds: [''] },
  { initialStickerIds: ['missing'] },
  { initialStickerIds: ['brand'], stickerAssets: [{ id: 'brand', uri: '' }] },
  {
    initialStickerIds: ['brand'],
    stickerAssets: [
      { id: 'brand', uri: '/a.png' },
      { id: 'brand', uri: '/b.png' },
    ],
  },
])(
  'rejects an invalid initial sticker before opening native UI',
  async (options) => {
    await expect(
      openEditor({ source: { uri: '/photo.jpg', type: 'photo' }, ...options })
    ).rejects.toMatchObject({ code: 'E_INVALID_OPTIONS' });
    expect(mockNativeOpenEditor).not.toHaveBeenCalled();
  }
);

test.each([{ ids: [] }, { ids: ['a'] }, { ids: ['a', 'b'] }])(
  'accepts ordered initialStickerIds %j',
  async ({ ids }) => {
    mockNativeOpenEditor.mockResolvedValue(JSON.stringify({ cancelled: true }));
    await openEditor({
      source: { uri: '/photo.jpg', type: 'photo' },
      initialStickerIds: ids,
      stickerAssets: [
        { id: 'a', uri: '/a.png' },
        { id: 'b', uri: '/b.png' },
      ],
    });
    expect(
      JSON.parse(mockNativeOpenEditor.mock.calls[0]![0]).initialStickerIds
    ).toEqual(ids);
  }
);

test.each([
  { ids: 'brand' },
  { ids: null },
  { ids: [1] },
  { ids: ['a', 'missing'] },
])('rejects malformed initialStickerIds %j', async ({ ids }) => {
  await expect(
    openEditor({
      source: { uri: '/photo.jpg', type: 'photo' },
      // @ts-expect-error Deliberately test untyped caller input.
      initialStickerIds: ids,
      stickerAssets: [{ id: 'a', uri: '/a.png' }],
    })
  ).rejects.toMatchObject({ code: 'E_INVALID_OPTIONS' });
  expect(mockNativeOpenEditor).not.toHaveBeenCalled();
});
