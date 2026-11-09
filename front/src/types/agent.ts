/** Agent SSE 事件类型 */
export type AgentEventType = 'text' | 'tool_call' | 'tool_result' | 'confirm' | 'error' | 'done';

export interface AgentTextEvent {
  type: 'text';
  content: string;
}

export interface AgentToolCallEvent {
  type: 'tool_call';
  tool: string;
  args: Record<string, unknown>;
}

export interface AgentToolResultEvent {
  type: 'tool_result';
  tool: string;
  result: string;
}

export interface AgentConfirmEvent {
  type: 'confirm';
  confirmId: string;
  tool: string;
  args: Record<string, unknown>;
  description: string;
  riskLevel: 'low' | 'medium' | 'high';
}

export interface AgentErrorEvent {
  type: 'error';
  message: string;
}

export interface AgentDoneEvent {
  type: 'done';
}

export type AgentEvent =
  | AgentTextEvent
  | AgentToolCallEvent
  | AgentToolResultEvent
  | AgentConfirmEvent
  | AgentErrorEvent
  | AgentDoneEvent;

/** Agent 工具信息 */
export interface AgentTool {
  toolName: string;
  description: string;
  readOnly: boolean;
  group: string;
  enabled: boolean;
}

/** AI 权限覆盖配置 */
export interface AiToolPermissionItem {
  toolName: string;
  description: string;
  readOnly: boolean;
  group: string;
  enabled: boolean;
}

export interface AiToolPermissionVO {
  userId: number;
  username: string;
  permissions: AiToolPermissionItem[];
}

/** Agent 聊天请求 */
export interface AgentChatRequestPayload {
  sessionId: string;
  message: string;
  context?: {
    enterpriseId?: number;
    chainId?: number;
  };
}

/** Agent 确认请求 */
export interface AgentConfirmPayload {
  sessionId: string;
  confirmId: string;
  approved: boolean;
}

/** Agent SSE 回调 */
export interface AgentStreamCallbacks {
  onText: (content: string) => void;
  onToolCall: (tool: string, args: Record<string, unknown>) => void;
  onToolResult: (tool: string, result: string) => void;
  onConfirm: (event: AgentConfirmEvent) => void;
  onError: (message: string) => void;
  onDone: () => void;
}

/** Agent SSE 流控制句柄 */
export interface AgentStreamHandle {
  abort: () => void;
}
