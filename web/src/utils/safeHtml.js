import DOMPurify from 'dompurify'

export const escapeHtml = (value) => String(value == null ? '' : value)
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;')
  .replace(/'/g, '&#39;')

export const sanitizeHtml = (value) => {
  const html = String(value == null ? '' : value)
  if (!DOMPurify || typeof DOMPurify.sanitize !== 'function') {
    return escapeHtml(html)
  }
  return DOMPurify.sanitize(html, {
    USE_PROFILES: { html: true },
    ADD_ATTR: ['target', 'rel'],
    FORBID_TAGS: ['form', 'input', 'textarea', 'select', 'option', 'iframe', 'object', 'embed', 'base', 'meta'],
    FORBID_ATTR: ['srcdoc']
  })
}

export const setSafeHtml = (element, value) => {
  if (element) element.innerHTML = sanitizeHtml(value)
}

export default sanitizeHtml
