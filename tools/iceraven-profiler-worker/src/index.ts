export interface Env {
  PROFILES: R2Bucket;
  PROFILE_UPLOAD_LIMIT: RateLimit;
  MAX_PROFILE_BYTES?: string;
  PROFILE_TTL_DAYS?: string;
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function encodeJson(value: unknown): string {
  return base64Url(new TextEncoder().encode(JSON.stringify(value)));
}


function acceptsProfilerApi(request: Request): boolean {
  return request.headers.get("Accept")?.includes("application/vnd.firefox-profiler+json") ?? false;
}

function profileId(): string {
  return crypto.randomUUID().replace(/-/g, "");
}

function cors(headers: Headers): Headers {
  headers.set("Access-Control-Allow-Origin", "https://profiler.firefox.com");
  headers.set("Vary", "Origin");
  return headers;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const maxBytes = Number(env.MAX_PROFILE_BYTES ?? 52_428_800);
    const ttlDays = Number(env.PROFILE_TTL_DAYS ?? 30);

    if (request.method === "OPTIONS") {
      return new Response(null, { headers: cors(new Headers({ "Access-Control-Allow-Methods": "GET, POST, DELETE, OPTIONS" })) });
    }

    if (url.pathname === "/compressed-store" && request.method === "POST") {
      if (!acceptsProfilerApi(request)) return new Response("Unsupported profiler API version", { status: 406 });
      const client = request.headers.get("CF-Connecting-IP") ?? "unknown";
      const { success } = await env.PROFILE_UPLOAD_LIMIT.limit({ key: client });
      if (!success) return new Response("Too many profile uploads", { status: 429 });
      const data = new Uint8Array(await request.arrayBuffer());
      if (data.byteLength < 2 || data.byteLength > maxBytes || data[0] !== 0x1f || data[1] !== 0x8b) {
        return new Response("Expected a gzipped profile within the size limit", { status: 400 });
      }
      const id = profileId();
      await env.PROFILES.put(`profiles/${id}.json.gz`, data, {
        httpMetadata: { contentType: "application/json", contentEncoding: "gzip" },
        customMetadata: { expiresAt: String(Date.now() + ttlDays * 86_400_000) },
      });
      // Fenix only reads profileToken from the JWT response.
      return new Response(`${encodeJson({ alg: "none", typ: "JWT" })}.${encodeJson({ profileToken: id })}.`, { status: 200, headers: cors(new Headers({ "Content-Type": "text/plain; charset=utf-8" })) });
    }

    const match = /^\/profile\/([a-f0-9]{32})$/.exec(url.pathname);
    if (match && request.method === "GET") {
      const object = await env.PROFILES.get(`profiles/${match[1]}.json.gz`);
      if (!object) return new Response("Profile not found", { status: 404 });
      const expiresAt = Number(object.customMetadata?.expiresAt ?? 0);
      if (expiresAt && expiresAt < Date.now()) {
        await env.PROFILES.delete(`profiles/${match[1]}.json.gz`);
        return new Response("Profile expired", { status: 404 });
      }
      const headers = cors(new Headers());
      object.writeHttpMetadata(headers);
      headers.set("Cache-Control", "private, no-store");
      return new Response(object.body, { headers });
    }

    return new Response("Not found", { status: 404 });
  },
};
