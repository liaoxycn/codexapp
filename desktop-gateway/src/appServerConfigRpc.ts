import type {
  AppServerModel,
  ConfigReadResult,
  ModelListResult,
} from "./appServerTypes.js";
import type { GatewayConfigOptionsPayload } from "./protocol.js";

const MODEL_LIST_PAGE_SIZE = 100;
const MODEL_LIST_MAX_PAGES = 5;

type RequestFn = <TParams extends object, TResult = unknown>(
  method: string,
  params: TParams
) => Promise<TResult>;

export async function readGatewayConfigOptions(
  request: RequestFn,
  cwd: string
): Promise<GatewayConfigOptionsPayload> {
  const [configResult, models] = await Promise.all([
    request("config/read", { includeLayers: false, cwd }) as Promise<ConfigReadResult>,
    listModels(request),
  ]);
  return buildGatewayConfigOptions(configResult.config ?? {}, models);
}

async function listModels(request: RequestFn): Promise<AppServerModel[]> {
  const models: AppServerModel[] = [];
  let cursor: string | null = null;
  for (let page = 0; page < MODEL_LIST_MAX_PAGES; page += 1) {
    const result = (await request("model/list", {
      cursor,
      limit: MODEL_LIST_PAGE_SIZE,
      includeHidden: false,
    })) as ModelListResult;
    models.push(...result.data);
    cursor = result.nextCursor ?? null;
    if (!cursor) {
      break;
    }
  }
  return models;
}

function buildGatewayConfigOptions(
  config: ConfigReadResult["config"],
  models: AppServerModel[]
): GatewayConfigOptionsPayload {
  const visibleModels = models.filter((model) => !model.hidden);
  const defaultModel =
    config.model ??
    visibleModels.find((model) => model.isDefault)?.model ??
    visibleModels[0]?.model;
  const defaultModelEntry = visibleModels.find((model) => model.model === defaultModel);
  const reasoningEfforts = defaultModelEntry?.supportedReasoningEfforts ?? [];

  return {
    models: visibleModels.map((model) => ({
      label: model.displayName || model.model || model.id,
      value: model.model || model.id,
      description: model.description,
    })),
    reasoningEfforts: reasoningEfforts.map((option) => ({
      label: option.reasoningEffort,
      value: option.reasoningEffort,
      description: option.description,
    })),
    sandboxModes: config.sandbox_mode
      ? [{ label: config.sandbox_mode, value: config.sandbox_mode }]
      : [],
    defaults: {
      model: defaultModel,
      reasoningEffort:
        config.model_reasoning_effort ??
        defaultModelEntry?.defaultReasoningEffort ??
        reasoningEfforts[0]?.reasoningEffort,
      sandboxMode: config.sandbox_mode ?? undefined,
    },
  };
}
