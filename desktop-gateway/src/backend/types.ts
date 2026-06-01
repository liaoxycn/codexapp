import type {
  GatewayChipPayload,
  GatewayMessagePayload,
  GatewayThreadPayload
} from "../protocol.js";

export interface ThreadRecord {
  summary: GatewayThreadPayload;
  messages: GatewayMessagePayload[];
  chips: GatewayChipPayload[];
  pendingApproval?: string | null;
  slashCommands: string[];
  cwd: string;
  permissionSummary: string;
  sessionConfig?: {
    permissionMode?: string;
    provider?: string;
    model?: string;
    reasoningEffort?: string;
  };
  isGenerating: boolean;
  pendingTimer?: NodeJS.Timeout;
}
