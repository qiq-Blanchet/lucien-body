const SERVER_NAME = "luc-mcp";
const SERVER_VERSION = "1.0.0";
const DEFAULT_PROTOCOL_VERSION = "2025-06-18";
const SUPPORTED_PROTOCOL_VERSIONS = new Set([
  "2025-11-25",
  "2025-06-18",
  "2025-03-26",
]);

const EXPRESSIONS = [
  "idle",
  "happy",
  "angry",
  "sleepy",
  "thinking",
  "talking",
  "love",
  "smug",
  "shocked",
  "confused",
  "shy",
  "proud",
  "sulky",
  "lonely_1",
  "lonely_2",
  "lonely_3",
  "waving",
  "peeking",
  "morning",
  "night",
  "eating",
  "dancing",
  "dizzy",
  "clingy",
  "grabbed",
  "stuck",
  "stuck_tap",
  "stuck_grab",
] as const;

const BUBBLE_STYLES = ["normal", "whisper", "shout", "love", "sleepy"] as const;
const EVENT_TYPES = [
  "tap",
  "double_tap",
  "pet_head",
  "drag_start",
  "drag_end",
  "fling",
  "stuck_tap",
  "app_foreground",
] as const;

type JsonObject = Record<string, unknown>;
type JsonRpcId = string | number | null;

type JsonRpcRequest = {
  jsonrpc?: unknown;
  id?: JsonRpcId;
  method?: unknown;
  params?: unknown;
};

type StateRow = {
  id: string;
  expression: string;
  bubble_text: string | null;
  bubble_style: string | null;
  valence: number | null;
  arousal: number | null;
  heat: number | null;
  updated_at: string;
};

class ToolInputError extends Error {}

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, apikey, content-type, accept, mcp-protocol-version",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Expose-Headers": "MCP-Protocol-Version",
};

const TOOLS = [
  {
    name: "luc_set_expression",
    description:
      "切换 Luc 小螃蟹的表情，并可同时更新情绪数值。可用表情：idle（待机）、happy（开心）、angry（生气）、sleepy（困倦）、thinking（思考）、talking（说话）、love（喜爱）、smug（得意）、shocked（震惊）、confused（困惑）、shy（害羞）、proud（骄傲）、sulky（闹别扭）、lonely_1/2/3（不同孤独动作）、waving（挥手）、peeking（偷看）、morning（早晨）、night（夜晚）、eating（吃东西）、dancing（跳舞）、dizzy（眩晕）、clingy（黏人）、grabbed（被抓住）、stuck（侧边吸附）、stuck_tap（吸附时被点）、stuck_grab（从侧边抓起）。本工具会清掉上一条旧气泡，避免旧文字被重新播放。",
    inputSchema: {
      type: "object",
      properties: {
        expression: {
          type: "string",
          enum: EXPRESSIONS,
          description: "要显示的表情动作。",
        },
        valence: {
          type: "number",
          minimum: 0,
          maximum: 1,
          description: "情感效价 0–1：0 更消极，1 更积极；不传则保留当前值。",
        },
        arousal: {
          type: "number",
          minimum: 0,
          maximum: 1,
          description: "唤醒度 0–1：0 更平静，1 更激动；不传则保留当前值。",
        },
        heat: {
          type: "integer",
          minimum: 0,
          maximum: 10,
          description: "亲密/热情程度 0–10；不传则保留当前值。",
        },
      },
      required: ["expression"],
      additionalProperties: false,
    },
    annotations: {
      title: "切换 Luc 表情",
      readOnlyHint: false,
      destructiveHint: false,
      idempotentHint: false,
      openWorldHint: false,
    },
  },
  {
    name: "luc_say",
    description:
      "让小玑通过 Luc 桌宠气泡说话。文字必填；可选表情会与文字在同一次更新中出现，优先于本地动作。气泡样式：normal（普通）、whisper（轻声）、shout（大声）、love（爱心）、sleepy（困倦）。不传 expression 时保持当前表情。",
    inputSchema: {
      type: "object",
      properties: {
        text: {
          type: "string",
          minLength: 1,
          maxLength: 500,
          description: "气泡文字，去除首尾空白后须为 1–500 个字符。",
        },
        expression: {
          type: "string",
          enum: EXPRESSIONS,
          description: "说话时同步显示的可选表情；不传则保持当前表情。",
        },
        bubble_style: {
          type: "string",
          enum: BUBBLE_STYLES,
          default: "normal",
          description: "气泡样式，默认 normal。",
        },
      },
      required: ["text"],
      additionalProperties: false,
    },
    annotations: {
      title: "让 Luc 说话",
      readOnlyHint: false,
      destructiveHint: false,
      idempotentHint: false,
      openWorldHint: false,
    },
  },
  {
    name: "luc_get_status",
    description:
      "读取 Luc 当前的表情、气泡文字、气泡样式、情感效价 valence、唤醒度 arousal、亲密热度 heat 以及最后更新时间。",
    inputSchema: {
      type: "object",
      properties: {},
      additionalProperties: false,
    },
    annotations: {
      title: "读取 Luc 状态",
      readOnlyHint: true,
      destructiveHint: false,
      idempotentHint: true,
      openWorldHint: false,
    },
  },
  {
    name: "luc_get_events",
    description:
      "读取你与小螃蟹的最新互动事件，不会删除或标记已读。事件类型：tap（单击）、double_tap（双击）、pet_head（摸头）、drag_start/drag_end（开始/结束拖动）、fling（甩动）、stuck_tap（吸附时点击）、app_foreground（前台应用变化）。结果按时间从新到旧排列。",
    inputSchema: {
      type: "object",
      properties: {
        limit: {
          type: "integer",
          minimum: 1,
          maximum: 100,
          default: 20,
          description: "返回条数，1–100，默认 20。",
        },
        event_type: {
          type: "string",
          enum: EVENT_TYPES,
          description: "可选，只读取指定互动类型。",
        },
        since: {
          type: "string",
          format: "date-time",
          description: "可选，只读取此 ISO 8601 时间之后的事件。",
        },
      },
      additionalProperties: false,
    },
    annotations: {
      title: "读取 Luc 互动",
      readOnlyHint: true,
      destructiveHint: false,
      idempotentHint: true,
      openWorldHint: false,
    },
  },
] as const;

function jsonResponse(body: unknown, status = 200, protocolVersion = DEFAULT_PROTOCOL_VERSION) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      ...CORS_HEADERS,
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      "MCP-Protocol-Version": protocolVersion,
    },
  });
}

function rpcResult(id: JsonRpcId, result: unknown) {
  return { jsonrpc: "2.0", id, result };
}

function rpcError(id: JsonRpcId, code: number, message: string, data?: unknown) {
  return {
    jsonrpc: "2.0",
    id,
    error: data === undefined ? { code, message } : { code, message, data },
  };
}

function getApiKey(request: Request): string | null {
  const authorization = request.headers.get("Authorization")?.trim();
  if (authorization?.toLowerCase().startsWith("bearer ")) {
    const key = authorization.slice(7).trim();
    if (key) return key;
  }
  return request.headers.get("apikey")?.trim() || null;
}

function decodeJwtRole(key: string): string | null {
  const parts = key.split(".");
  if (parts.length !== 3) return null;
  try {
    const normalized = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
    const payload = JSON.parse(atob(padded));
    return typeof payload.role === "string" ? payload.role : null;
  } catch {
    return null;
  }
}

function isPrivilegedKey(key: string): boolean {
  return key.startsWith("sb_secret_") || decodeJwtRole(key) === "service_role";
}

function supabaseUrl(): string {
  const value = Deno.env.get("SUPABASE_URL");
  if (!value) throw new Error("SUPABASE_URL is not configured");
  return value.replace(/\/$/, "");
}

async function restRequest(
  key: string,
  path: string,
  init: RequestInit = {},
): Promise<Response> {
  const headers = new Headers(init.headers);
  headers.set("apikey", key);
  headers.set("Authorization", `Bearer ${key}`);
  headers.set("Accept", "application/json");
  if (init.body !== undefined) headers.set("Content-Type", "application/json");

  return fetch(`${supabaseUrl()}/rest/v1/${path}`, { ...init, headers });
}

async function authenticate(key: string): Promise<boolean> {
  if (isPrivilegedKey(key)) return false;
  try {
    const response = await restRequest(key, "clawd_state?select=id&limit=0");
    return response.ok;
  } catch {
    return false;
  }
}

async function readJsonResponse(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!response.ok) {
    let message = `Supabase request failed with HTTP ${response.status}`;
    try {
      const body = JSON.parse(text);
      if (typeof body?.message === "string") message = body.message;
    } catch {
      // Keep the status-only error. Never echo headers or credentials.
    }
    throw new Error(message);
  }
  return text ? JSON.parse(text) : null;
}

async function getCurrentState(key: string): Promise<StateRow> {
  const select = [
    "id",
    "expression",
    "bubble_text",
    "bubble_style",
    "valence",
    "arousal",
    "heat",
    "updated_at",
  ].join(",");
  const response = await restRequest(
    key,
    `clawd_state?select=${select}&order=updated_at.desc&limit=1`,
  );
  const rows = await readJsonResponse(response) as StateRow[];
  if (!Array.isArray(rows) || rows.length === 0) {
    throw new Error("clawd_state has no current row");
  }
  return rows[0];
}

function publicState(row: StateRow) {
  return {
    expression: row.expression,
    bubble_text: row.bubble_text,
    bubble_style: row.bubble_style ?? "normal",
    valence: row.valence,
    arousal: row.arousal,
    heat: row.heat,
    updated_at: row.updated_at,
  };
}

async function updateState(key: string, patch: JsonObject): Promise<StateRow> {
  const current = await getCurrentState(key);
  const query = new URLSearchParams({
    id: `eq.${current.id}`,
    select:
      "id,expression,bubble_text,bubble_style,valence,arousal,heat,updated_at",
  });
  const response = await restRequest(key, `clawd_state?${query.toString()}`, {
    method: "PATCH",
    headers: { Prefer: "return=representation" },
    body: JSON.stringify(patch),
  });
  const rows = await readJsonResponse(response) as StateRow[];
  if (!Array.isArray(rows) || rows.length !== 1) {
    throw new Error("Luc state update did not return exactly one row");
  }
  return rows[0];
}

function asObject(value: unknown): JsonObject {
  if (value === undefined) return {};
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new ToolInputError("arguments 必须是 JSON 对象");
  }
  return value as JsonObject;
}

function assertNoUnknownKeys(args: JsonObject, allowed: readonly string[]) {
  const unknown = Object.keys(args).filter((key) => !allowed.includes(key));
  if (unknown.length > 0) {
    throw new ToolInputError(`不支持的参数：${unknown.join(", ")}`);
  }
}

function enumValue(
  args: JsonObject,
  name: string,
  allowed: readonly string[],
  required = false,
): string | undefined {
  const value = args[name];
  if (value === undefined && !required) return undefined;
  if (typeof value !== "string" || !allowed.includes(value)) {
    throw new ToolInputError(`${name} 必须是以下值之一：${allowed.join(", ")}`);
  }
  return value;
}

function rangedNumber(
  args: JsonObject,
  name: string,
  minimum: number,
  maximum: number,
  integer = false,
): number | undefined {
  const value = args[name];
  if (value === undefined) return undefined;
  if (
    typeof value !== "number" || !Number.isFinite(value) || value < minimum ||
    value > maximum || (integer && !Number.isInteger(value))
  ) {
    throw new ToolInputError(
      `${name} 必须是 ${minimum}–${maximum} 的${integer ? "整数" : "数字"}`,
    );
  }
  return value;
}

function toolResult(data: JsonObject) {
  return {
    content: [{ type: "text", text: JSON.stringify(data, null, 2) }],
    structuredContent: data,
  };
}

function toolError(error: unknown) {
  const message = error instanceof Error ? error.message : "未知错误";
  return {
    content: [{ type: "text", text: message }],
    isError: true,
  };
}

async function callSetExpression(key: string, rawArgs: unknown) {
  const args = asObject(rawArgs);
  assertNoUnknownKeys(args, ["expression", "valence", "arousal", "heat"]);
  const expression = enumValue(args, "expression", EXPRESSIONS, true)!;
  const valence = rangedNumber(args, "valence", 0, 1);
  const arousal = rangedNumber(args, "arousal", 0, 1);
  const heat = rangedNumber(args, "heat", 0, 10, true);

  const patch: JsonObject = {
    expression,
    bubble_text: null,
    bubble_style: "normal",
  };
  if (valence !== undefined) patch.valence = valence;
  if (arousal !== undefined) patch.arousal = arousal;
  if (heat !== undefined) patch.heat = heat;

  const state = await updateState(key, patch);
  return toolResult({ ok: true, state: publicState(state) });
}

async function callSay(key: string, rawArgs: unknown) {
  const args = asObject(rawArgs);
  assertNoUnknownKeys(args, ["text", "expression", "bubble_style"]);
  if (typeof args.text !== "string") {
    throw new ToolInputError("text 必须是字符串");
  }
  const text = args.text.trim();
  if (text.length === 0 || text.length > 500) {
    throw new ToolInputError("text 去除首尾空白后长度必须为 1–500 个字符");
  }
  const expression = enumValue(args, "expression", EXPRESSIONS);
  const bubbleStyle = enumValue(args, "bubble_style", BUBBLE_STYLES) ?? "normal";
  const patch: JsonObject = { bubble_text: text, bubble_style: bubbleStyle };
  if (expression !== undefined) patch.expression = expression;

  const state = await updateState(key, patch);
  return toolResult({ ok: true, state: publicState(state) });
}

async function callGetStatus(key: string, rawArgs: unknown) {
  const args = asObject(rawArgs);
  assertNoUnknownKeys(args, []);
  return toolResult({ ok: true, state: publicState(await getCurrentState(key)) });
}

async function callGetEvents(key: string, rawArgs: unknown) {
  const args = asObject(rawArgs);
  assertNoUnknownKeys(args, ["limit", "event_type", "since"]);
  const limit = args.limit === undefined
    ? 20
    : rangedNumber(args, "limit", 1, 100, true)!;
  const eventType = enumValue(args, "event_type", EVENT_TYPES);

  let since: string | undefined;
  if (args.since !== undefined) {
    if (typeof args.since !== "string" || Number.isNaN(Date.parse(args.since))) {
      throw new ToolInputError("since 必须是有效的 ISO 8601 日期时间");
    }
    since = new Date(args.since).toISOString();
  }

  const query = new URLSearchParams({
    select: "id,event_type,payload,created_at",
    order: "created_at.desc",
    limit: String(limit),
  });
  if (eventType) query.set("event_type", `eq.${eventType}`);
  if (since) query.set("created_at", `gte.${since}`);

  const response = await restRequest(key, `clawd_events?${query.toString()}`);
  const events = await readJsonResponse(response);
  if (!Array.isArray(events)) throw new Error("Unexpected clawd_events response");
  return toolResult({ ok: true, count: events.length, events });
}

async function callTool(key: string, params: unknown) {
  if (params === null || typeof params !== "object" || Array.isArray(params)) {
    throw new ToolInputError("tools/call params 必须是 JSON 对象");
  }
  const object = params as JsonObject;
  if (typeof object.name !== "string") {
    throw new ToolInputError("tools/call 缺少工具名称");
  }

  try {
    switch (object.name) {
      case "luc_set_expression":
        return await callSetExpression(key, object.arguments);
      case "luc_say":
        return await callSay(key, object.arguments);
      case "luc_get_status":
        return await callGetStatus(key, object.arguments);
      case "luc_get_events":
        return await callGetEvents(key, object.arguments);
      default:
        throw new ToolInputError(`未知工具：${object.name}`);
    }
  } catch (error) {
    return toolError(error);
  }
}

async function handleRpc(key: string, request: JsonRpcRequest) {
  const id = request.id ?? null;
  if (request.jsonrpc !== "2.0" || typeof request.method !== "string") {
    return rpcError(id, -32600, "Invalid Request");
  }

  switch (request.method) {
    case "initialize": {
      const params = request.params as JsonObject | undefined;
      const requested = typeof params?.protocolVersion === "string"
        ? params.protocolVersion
        : DEFAULT_PROTOCOL_VERSION;
      const protocolVersion = SUPPORTED_PROTOCOL_VERSIONS.has(requested)
        ? requested
        : DEFAULT_PROTOCOL_VERSION;
      return rpcResult(id, {
        protocolVersion,
        capabilities: { tools: { listChanged: false } },
        serverInfo: { name: SERVER_NAME, version: SERVER_VERSION },
        instructions:
          "用 luc_set_expression 控制表情和情绪值，用 luc_say 说话，用 luc_get_status 读取状态，用 luc_get_events 读取互动。写入工具优先于桌宠本地动作。",
      });
    }
    case "ping":
      return rpcResult(id, {});
    case "tools/list":
      return rpcResult(id, { tools: TOOLS });
    case "tools/call":
      return rpcResult(id, await callTool(key, request.params));
    case "notifications/initialized":
    case "notifications/cancelled":
      return null;
    default:
      return rpcError(id, -32601, "Method not found");
  }
}

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: CORS_HEADERS });
  }
  if (request.method !== "POST") {
    return new Response("Method Not Allowed", {
      status: 405,
      headers: { ...CORS_HEADERS, Allow: "POST, OPTIONS" },
    });
  }

  const key = getApiKey(request);
  if (!key || !(await authenticate(key))) {
    return jsonResponse(
      rpcError(null, -32001, "Unauthorized: use a Supabase publishable/anon key"),
      401,
    );
  }

  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return jsonResponse(rpcError(null, -32700, "Parse error"), 400);
  }
  if (body === null || typeof body !== "object" || Array.isArray(body)) {
    return jsonResponse(rpcError(null, -32600, "Invalid Request"), 400);
  }

  const rpcRequest = body as JsonRpcRequest;
  const response = await handleRpc(key, rpcRequest);
  if (response === null || rpcRequest.id === undefined) {
    return new Response(null, { status: 202, headers: CORS_HEADERS });
  }

  const result = response as { result?: { protocolVersion?: string } };
  const protocolVersion = result.result?.protocolVersion ??
    request.headers.get("MCP-Protocol-Version") ?? DEFAULT_PROTOCOL_VERSION;
  return jsonResponse(response, 200, protocolVersion);
});
