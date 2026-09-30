/**
 * RFC 4122 v4-shaped id for Idempotency-Key headers. It only has to be
 * unique per booking attempt, not unguessable, so Math.random is enough and
 * works on every JS engine (Hermes has no crypto.randomUUID).
 */
export function uuid(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}
