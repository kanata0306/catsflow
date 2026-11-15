export type MonthlyAssessment = {
  startMonth: string;
  endMonth: string;
  periodMonths: number;
  assessmentMode: "rule_based";
  aiStatus: "not_configured";
  externalDataSent: false;
  reviewComment: string;
  figures: {
    income: number;
    expense: number;
    balance: number;
    balanceRatePercent: number | null;
    transactionCount: number;
  };
  monthlyTrend: Array<{
    month: string;
    income: number;
    expense: number;
    balance: number;
    transactionCount: number;
  }>;
  latestAsset: {
    snapshotDate: string;
    totalAmount: number;
    cashAmount: number | null;
    stockAmount: number | null;
    investmentTrustAmount: number | null;
    pointAmount: number | null;
  } | null;
  liabilities: {
    count: number;
    asOfDate: string | null;
    totalBalance: number | null;
    netWorth: number | null;
  };
  activeGoals: Array<{
    id: number;
    name: string;
    type: string;
    currentAmount: number;
    targetAmount: number;
    progressPercent: number | null;
  }>;
  topCategories: Array<{ name: string; amount: number }>;
  findings: Array<{
    type: string;
    title: string;
    detail: string;
    evidence: Array<{ label: string; value: number }>;
  }>;
  dataQuality: string[];
};

export async function fetchMonthlyAssessment(token: string, month: string): Promise<MonthlyAssessment> {
  const response = await fetch(`/api/assessment/monthly?month=${encodeURIComponent(month)}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const result = await response.json() as MonthlyAssessment | { message?: string };
  if (!response.ok) {
    throw new Error("message" in result && result.message ? result.message : "直近6か月のレビューを取得できませんでした。");
  }
  return result as MonthlyAssessment;
}