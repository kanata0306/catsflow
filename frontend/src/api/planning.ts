export type FinancialProfile = {
  userId: number;
  housingType: string | null;
  monthlyLivingCost: number | null;
  incomeStability: string | null;
  monthlyFamilySupport: number | null;
  priorities: string | null;
  updatedAt: string;
};

export type LongTermLiability = {
  id: number;
  name: string;
  type: string;
  balance: number;
  asOfDate: string;
  annualInterestRate: number | null;
  monthlyPayment: number | null;
  paymentDay: number | null;
  note: string | null;
  createdAt: string;
  updatedAt: string;
};

export type FinancialGoal = {
  id: number;
  type: "savings" | "asset_building";
  name: string;
  targetAmount: number;
  currentAmount: number;
  targetMonth: string | null;
  monthlyContribution: number | null;
  status: "active" | "paused" | "completed";
  createdAt: string;
  updatedAt: string;
};

export type LifePlanEvent = {
  id: number;
  title: string;
  category: string;
  eventMonth: string;
  estimatedAmount: number | null;
  entryType: "fact" | "assumption";
  note: string | null;
  createdAt: string;
  updatedAt: string;
};

export type ProfileInput = Omit<FinancialProfile, "userId" | "updatedAt">;
export type LiabilityInput = Omit<LongTermLiability, "id" | "createdAt" | "updatedAt">;
export type GoalInput = Omit<FinancialGoal, "id" | "createdAt" | "updatedAt">;
export type LifeEventInput = Omit<LifePlanEvent, "id" | "createdAt" | "updatedAt">;

type ItemResponse<T> = { item: T; message?: string };
type ListResponse<T> = { items: T[]; message?: string };

async function request<T>(token: string, url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    ...init,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(init?.body ? { "Content-Type": "application/json" } : {}),
      ...init?.headers,
    },
  });
  if (response.status === 204) return undefined as T;
  const body = await response.json() as { message?: string } & T;
  if (!response.ok) throw new Error(body.message || "保存できませんでした。入力内容を確認してください。");
  return body;
}

function jsonBody(value: unknown): string {
  return JSON.stringify(value);
}

export async function fetchPlanningData(token: string) {
  const [profile, liabilities, goals, lifeEvents] = await Promise.all([
    request<ItemResponse<FinancialProfile | null>>(token, "/api/planning/profile"),
    request<ListResponse<LongTermLiability>>(token, "/api/planning/liabilities"),
    request<ListResponse<FinancialGoal>>(token, "/api/planning/goals"),
    request<ListResponse<LifePlanEvent>>(token, "/api/planning/life-events"),
  ]);
  return { profile: profile.item, liabilities: liabilities.items, goals: goals.items, lifeEvents: lifeEvents.items };
}

export async function saveProfile(token: string, input: ProfileInput) {
  return request<ItemResponse<FinancialProfile>>(token, "/api/planning/profile", {
    method: "PUT",
    body: jsonBody(input),
  }).then((response) => response.item);
}

export async function saveLiability(token: string, input: LiabilityInput, id?: number) {
  const response = await request<ItemResponse<LongTermLiability>>(token,
    id ? `/api/planning/liabilities/${id}` : "/api/planning/liabilities",
    { method: id ? "PUT" : "POST", body: jsonBody(input) });
  return response.item;
}

export async function removeLiability(token: string, id: number) {
  return request<void>(token, `/api/planning/liabilities/${id}`, { method: "DELETE" });
}

export async function saveGoal(token: string, input: GoalInput, id?: number) {
  const response = await request<ItemResponse<FinancialGoal>>(token,
    id ? `/api/planning/goals/${id}` : "/api/planning/goals",
    { method: id ? "PUT" : "POST", body: jsonBody(input) });
  return response.item;
}

export async function removeGoal(token: string, id: number) {
  return request<void>(token, `/api/planning/goals/${id}`, { method: "DELETE" });
}

export async function saveLifeEvent(token: string, input: LifeEventInput, id?: number) {
  const response = await request<ItemResponse<LifePlanEvent>>(token,
    id ? `/api/planning/life-events/${id}` : "/api/planning/life-events",
    { method: id ? "PUT" : "POST", body: jsonBody(input) });
  return response.item;
}

export async function removeLifeEvent(token: string, id: number) {
  return request<void>(token, `/api/planning/life-events/${id}`, { method: "DELETE" });
}
