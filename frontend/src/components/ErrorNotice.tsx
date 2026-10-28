import { ApiError } from '../api/client';

/** Hiện thông điệp lỗi từ backend (thông điệp RC* là tiếng Việt, dùng được trực tiếp). */
export function ErrorNotice({ error }: { error: unknown }) {
  if (!error) return null;
  const message = error instanceof ApiError ? error.message : 'Có lỗi không mong muốn. Tải lại trang rồi thử lại.';
  const details = error instanceof ApiError ? error.details : [];
  return (
    <div className="notice" role="alert">
      {message}
      {details && details.length > 0 && (
        <ul style={{ margin: '0.35rem 0 0', paddingLeft: '1.1rem' }}>
          {details.map((d) => (
            <li key={d.field}>
              {d.field}: {d.message}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
