import { SecurityRole } from "@/types/telemetry";

export const DEFAULT_AUTH_SECRET = "agriguard-edge-dev-key-2026";

/**
 * Computes an HMAC-SHA256 signature formatted as a 64-char hexadecimal string
 * using the standard browser / Edge Web Crypto API (crypto.subtle).
 */
export async function computeHmacSha256(secret: string, payload: string): Promise<string> {
  const encoder = new TextEncoder();
  const keyData = encoder.encode(secret);

  const cryptoKey = await crypto.subtle.importKey(
    "raw",
    keyData,
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );

  const signatureBuffer = await crypto.subtle.sign(
    "HMAC",
    cryptoKey,
    encoder.encode(payload)
  );

  const bytes = new Uint8Array(signatureBuffer);
  let hex = "";
  for (let i = 0; i < bytes.length; i++) {
    hex += bytes[i].toString(16).padStart(2, "0");
  }
  return hex;
}

/**
 * Generates IEC 62443 zero-trust signed request headers for mutating edge control commands.
 */
export async function buildSignedHeaders(
  role: SecurityRole,
  method: string,
  path: string,
  body: string,
  secret: string = DEFAULT_AUTH_SECRET
): Promise<Record<string, string>> {
  const timestamp = Date.now().toString();
  const normMethod = method.toUpperCase();
  const canonical = `${role}:${timestamp}:${normMethod}:${path}:${body}`;
  const signature = await computeHmacSha256(secret, canonical);

  return {
    "Content-Type": "application/json",
    "X-AgriGuard-Role": role,
    "X-AgriGuard-Timestamp": timestamp,
    "X-AgriGuard-Signature": signature,
  };
}
