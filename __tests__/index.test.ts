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
    features: { crop: true, trim: false, mute: false },
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
