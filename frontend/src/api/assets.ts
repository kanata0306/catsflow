export type AssetSnapshot = {
  snapshotDate: string;
  totalAmount: number;
  cashAmount: number | null;
  stockAmount: number | null;
  investmentTrustAmount: number | null;
  pointAmount: number | null;
};

export type CsvImportResult = {
  ok: boolean;
  message: string;
  type: "assets" | "transactions";
  importedCount: number;
  updatedCount: number;
  skippedDuplicateCount: number;
  skippedExcludedCount: number;
  errors?: string[];
};

type AssetSnapshotResponse = {
  message?: string;
  snapshots?: AssetSnapshot[];
};

export class AssetApiError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
    this.name = "AssetApiError";
  }
}

export async function fetchAssetSnapshots(idToken: string): Promise<AssetSnapshot[]> {
  const response = await fetch("/api/assets/snapshots", {
    headers: { Authorization: `Bearer ${idToken}` },
  });
  const result = (await response.json()) as AssetSnapshotResponse;
  if (!response.ok) {
    throw new AssetApiError(result.message || "資産データを取得できませんでした。", response.status);
  }
  return result.snapshots ?? [];
}

export async function uploadCsv(idToken: string, file: File): Promise<CsvImportResult> {
  const body = new FormData();
  body.append("file", file);
  const response = await fetch("/api/import/csv", {
    method: "POST",
    headers: { Authorization: `Bearer ${idToken}` },
    body,
  });
  const result = (await response.json()) as CsvImportResult;
  if (!response.ok) {
    const details = result.errors?.length ? ` ${result.errors.join(" / ")}` : "";
    throw new Error(`${result.message || "CSVを取り込めませんでした。"}${details}`);
  }
  return result;
}