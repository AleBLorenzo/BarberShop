/**
 * Avatar with image or fallback initials.
 * @param {{src?: string, name?: string, alt?: string, size?: 'sm'|'md'|'lg'|'xl'}} props
 */
export default function Avatar({ src, name = '', alt, size = 'md' }) {
  const initials = name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((n) => n[0])
    .join('')
    .toUpperCase();

  return (
    <span className={`avatar avatar-${size}`} aria-label={alt || name}>
      {src ? (
        <img src={src} alt={alt || name} onError={(e) => { e.currentTarget.style.display = 'none'; }} />
      ) : (
        initials || '?'
      )}
    </span>
  );
}
