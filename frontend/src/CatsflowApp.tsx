import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import {
  ArrowDownLeft,
  ArrowRight,
  ArrowUpRight,
  CalendarDays,
  Check,
  CircleHelp,
  CloudUpload,
  Home,
  Landmark,
  LineChart,
  ListFilter,
  PiggyBank,
  Sparkles,
  Target,
  TrendingUp,
  Wallet,
} from "lucide-react";
import liff from "@line/liff";
import { version as appVersion } from "../package.json";
import { fetchMonthlyAssessment } from "./api/assessment";
import type { MonthlyAssessment } from "./api/assessment";
import { AssetApiError, fetchAssetSnapshots, uploadCsv } from "./api/assets";
import type { AssetSnapshot } from "./api/assets";
import { fetchPlanningData } from "./api/planning";
import type { FinancialGoal, FinancialProfile, LifePlanEvent, LongTermLiability } from "./api/planning";
import PlanningEditor from "./components/PlanningEditor";
import { sampleGoals, sampleHousehold, sampleLifeEvents } from "./data/sampleData";

type Screen = "home" | "assets" | "household" | "goals";
type LoadState = "loading" | "authenticated" | "ready" | "error" | "unlinked";
type DataLoadState = "idle" | "loading" | "ready" | "error";
type ChartRange = "1m" | "3m" | "6m" | "12m" | "all";
type PlanningData = {
  profile: FinancialProfile | null;
  liabilities: LongTermLiability[];
  goals: FinancialGoal[];
  lifeEvents: LifePlanEvent[];
};

const previewPlanningData: PlanningData = {
  profile: null,
  liabilities: [],
  goals: sampleGoals.map((goal, index) => ({
    id: -(index + 1),
    type: goal.type === "貯金" ? "savings" : "asset_building",
    name: `${goal.name}（サンプル）`,
    targetAmount: goal.target,
    currentAmount: goal.current,
    targetMonth: goal.deadline,
    monthlyContribution: goal.monthly,
    status: "active",
    createdAt: "",
    updatedAt: "",
  })),
  lifeEvents: sampleLifeEvents.map((event, index) => ({
    id: -(index + 1),
    title: `${event.title}（サンプル）`,
    category: event.category === "大きな支出" ? "large_purchase" : "housing",
    eventMonth: `${event.year}-04`,
    estimatedAmount: event.amount,
    entryType: index === 0 ? "fact" : "assumption",
    note: "画面確認用。保存されていません。",
    createdAt: "",
    updatedAt: "",
  })),
};

const currency = new Intl.NumberFormat("ja-JP", {
  style: "currency",
  currency: "JPY",
  maximumFractionDigits: 0,
});
const visualPreview = import.meta.env.DEV && new URLSearchParams(window.location.search).get("preview") === "1";

const navItems = [
  { id: "home", label: "ホーム", icon: Home },
  { id: "assets", label: "資産/負債", icon: Wallet },
  { id: "household", label: "家計", icon: ArrowDownLeft },
  { id: "goals", label: "目標", icon: Target },
] as const;

const chartRanges: Array<{ value: ChartRange; label: string; months: number | null }> = [
  { value: "1m", label: "1ヶ月", months: 1 },
  { value: "3m", label: "3ヶ月", months: 3 },
  { value: "6m", label: "6ヶ月", months: 6 },
  { value: "12m", label: "1年", months: 12 },
  { value: "all", label: "全期間", months: null },
];

const pageMeta: Record<Screen, { title: string }> = {
  home: { title: "ホーム" },
  assets: { title: "資産/負債" },
  household: { title: "家計" },
  goals: { title: "目標" },
};

let liffInitialization: Promise<void> | undefined;

async function initializeLiff(): Promise<void> {
  if (!liffInitialization) {
    liffInitialization = (async () => {
      const response = await fetch("/api/line/config");
      if (!response.ok) throw new Error("LINE設定を取得できませんでした。");
      const config = (await response.json()) as { liffId: string };
      if (!config.liffId) throw new Error("LIFF IDが未設定です。LINEログイン設定を確認してください。");
      await liff.init({ liffId: config.liffId });
    })();
  }
  await liffInitialization;
}

function withTimeout<T>(promise: Promise<T>, timeoutMs: number, message: string): Promise<T> {
  let timeoutId: number | undefined;
  const timeout = new Promise<never>((_, reject) => {
    timeoutId = window.setTimeout(() => reject(new Error(message)), timeoutMs);
  });
  return Promise.race([promise, timeout]).finally(() => window.clearTimeout(timeoutId));
}

function amount(value: number | null | undefined): string {
  return value == null ? "未取得" : currency.format(value);
}

function DataLabel({ children, type = "sample" }: { children: string; type?: "sample" | "stored" | "unavailable" }) {
  return <span className={`data-label ${type}`}>{type === "stored" && <i />} {children}</span>;
}

function compactAmount(value: number): string {
  if (value >= 100_000_000) return `${(value / 100_000_000).toFixed(1)}億`;
  if (value >= 10_000) return `${(value / 10_000).toFixed(value >= 1_000_000 ? 0 : 1)}万`;
  return value.toLocaleString("ja-JP");
}

function AssetChart({ snapshots, range, onRangeChange }: {
  snapshots: AssetSnapshot[];
  range: ChartRange;
  onRangeChange: (range: ChartRange) => void;
}) {
  const rangeMonths = chartRanges.find((item) => item.value === range)?.months ?? null;
  const latestDate = snapshots[0] ? new Date(`${snapshots[0].snapshotDate}T00:00:00`) : null;
  const cutoffDate = latestDate && rangeMonths != null
    ? new Date(latestDate.getFullYear(), latestDate.getMonth() - rangeMonths + 1, 1)
    : null;
  const filteredSnapshots = snapshots.filter((snapshot) => {
    if (!cutoffDate) return true;
    return new Date(`${snapshot.snapshotDate}T00:00:00`) >= cutoffDate;
  });
  const points = [...filteredSnapshots].reverse();
  const canStack = points.length > 1 && points.every((snapshot) =>
    snapshot.cashAmount != null && snapshot.stockAmount != null
    && snapshot.investmentTrustAmount != null && snapshot.pointAmount != null);
  const totalValues = points.map((snapshot) => snapshot.totalAmount);
  const maxStack = canStack ? Math.max(...points.map((snapshot) =>
    (snapshot.cashAmount ?? 0) + (snapshot.stockAmount ?? 0)
      + (snapshot.investmentTrustAmount ?? 0) + (snapshot.pointAmount ?? 0))) : 0;
  const maxValue = Math.max(...totalValues, maxStack, 1);
  const left = 72;
  const right = 744;
  const top = 14;
  const bottom = 164;
  const xFor = (index: number) => left + index * (right - left) / Math.max(points.length - 1, 1);
  const yFor = (value: number) => bottom - value / maxValue * (bottom - top);
  const totalPointString = totalValues.map((value, index) => `${xFor(index)},${yFor(value)}`).join(" ");
  const assetSeries: Array<{ key: "cashAmount" | "stockAmount" | "investmentTrustAmount" | "pointAmount"; label: string }> = [
    { key: "cashAmount", label: "現金・預金" },
    { key: "stockAmount", label: "株式" },
    { key: "investmentTrustAmount", label: "投資信託" },
    { key: "pointAmount", label: "ポイント" },
  ];
  let stackBase = points.map(() => 0);
  const stackedAreas = canStack ? assetSeries.map((series) => {
    const upper = points.map((snapshot, index) => stackBase[index] + (snapshot[series.key] ?? 0));
    const polygon = [
      ...upper.map((value, index) => `${xFor(index)},${yFor(value)}`),
      ...stackBase.map((value, index) => `${xFor(index)},${yFor(value)}`).reverse(),
    ].join(" ");
    stackBase = upper;
    return { ...series, polygon };
  }) : [];
  const tickValues = [0, maxValue / 2, maxValue];
  const xLabelIndexes = points.length > 2 ? [...new Set([0, Math.floor((points.length - 1) / 2), points.length - 1])] : points.map((_, index) => index);

  return (
    <div className="asset-chart-block">
      <div className="chart-toolbar">
        <div className="chart-range-control" role="group" aria-label="資産推移の表示期間">
          {chartRanges.map((item) => <button key={item.value} type="button" className={range === item.value ? "selected" : ""} aria-pressed={range === item.value} onClick={() => onRangeChange(item.value)}>{item.label}</button>)}
        </div>
        <span className="chart-mode-label">{canStack ? "資産区分の積上" : "総資産"}</span>
      </div>
      {points.length < 2 ? (
        <div className="chart-placeholder">{points.length === 0 ? "選択期間内の資産データはありません" : "推移の表示には異なる基準日のデータが2件以上必要です"}</div>
      ) : (
        <>
          <div className="asset-chart-scroll">
            <svg className="asset-chart" viewBox="0 0 760 208" role="img" aria-label={`${rangeMonths ?? "全"}期間の資産推移${canStack ? "・資産区分積上グラフ" : "・総資産グラフ"}`}>
              {tickValues.map((value) => <g key={value}><line x1={left} x2={right} y1={yFor(value)} y2={yFor(value)} className="chart-grid" /><text x={left - 8} y={yFor(value) + 4} textAnchor="end" className="chart-axis-label">{compactAmount(value)}</text></g>)}
              {canStack && stackedAreas.map((area) => <polygon key={area.key} points={area.polygon} className={`chart-area area-${area.key}`} />)}
              <polyline points={totalPointString} className="chart-line" />
              {xLabelIndexes.map((index) => <g key={points[index].snapshotDate}><line x1={xFor(index)} x2={xFor(index)} y1={bottom} y2={bottom + 4} className="chart-tick" /><text x={xFor(index)} y={bottom + 18} textAnchor={index === 0 ? "start" : index === points.length - 1 ? "end" : "middle"} className="chart-axis-label">{points[index].snapshotDate.slice(5)}</text></g>)}
            </svg>
          </div>
          {canStack ? <div className="chart-legend">{assetSeries.map((series) => <span key={series.key}><i className={`legend-${series.key}`} />{series.label}</span>)}</div> : <p className="inline-help">資産区分の内訳が不足しているため、総資産のみ表示しています。</p>}
        </>
      )}
    </div>
  );
}

function App() {
  const [activeScreen, setActiveScreen] = useState<Screen>("home");
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [authMessage, setAuthMessage] = useState("");
  const [idToken, setIdToken] = useState("");
  const [snapshots, setSnapshots] = useState<AssetSnapshot[]>([]);
  const [chartRange, setChartRange] = useState<ChartRange>("6m");
  const [assetLoadState, setAssetLoadState] = useState<DataLoadState>("idle");
  const [assetError, setAssetError] = useState("");
  const [assetRetry, setAssetRetry] = useState(0);
  const [monthlyReview, setMonthlyReview] = useState<MonthlyAssessment | null>(null);
  const [reviewError, setReviewError] = useState("");
  const [reviewLoading, setReviewLoading] = useState(false);
  const [reviewRevision, setReviewRevision] = useState(0);
  const [planningData, setPlanningData] = useState<PlanningData>({ profile: null, liabilities: [], goals: [], lifeEvents: [] });
  const [planningError, setPlanningError] = useState("");
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [importMessage, setImportMessage] = useState("");
  const [importStatus, setImportStatus] = useState<"error" | "success" | "">("");
  const [targetMonth, setTargetMonth] = useState(() => new Date().toISOString().slice(0, 7));
  const [selectedCategories, setSelectedCategories] = useState<string[]>([]);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    let active = true;
    if (visualPreview) {
      setLoadState("ready");
      setAssetLoadState("ready");
      setPlanningData(previewPlanningData);
      setPlanningError("画面確認用のサンプル表示中です。入力操作は無効です。");
      return () => { active = false; };
    }

    async function initialize() {
      try {
        await withTimeout(initializeLiff(), 15000, "LINE認証の初期化がタイムアウトしました。再試行してください。");
        if (!liff.isLoggedIn()) {
          liff.login({ redirectUri: window.location.href });
          return;
        }
        const token = liff.getIDToken();
        if (!token) throw new Error("LINE認証を取得できません。LIFFのopenid権限を確認してください。");
        if (!active) return;
        setIdToken(token);
        setLoadState("authenticated");
      } catch (error) {
        if (!active) return;
        setAuthMessage(error instanceof Error ? error.message : "接続に失敗しました。");
        setLoadState("error");
      }
    }

    void initialize();
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (!idToken || visualPreview) return;
    let active = true;
    setAssetLoadState("loading");
    setAssetError("");
    withTimeout(fetchAssetSnapshots(idToken), 12000, "資産データ取得がタイムアウトしました。再試行してください。")
      .then((records) => {
        if (!active) return;
        setSnapshots(records);
        setAssetLoadState("ready");
        setLoadState("ready");
      })
      .catch((error: unknown) => {
        if (!active) return;
        const message = error instanceof Error ? error.message : "資産データを取得できませんでした。";
        setAssetError(message);
        setAssetLoadState("error");
        if (error instanceof AssetApiError && error.status === 401) {
          setAuthMessage("LINEアカウントとCatsflowを連携してください。");
          setLoadState("unlinked");
        } else {
          setAuthMessage(message);
          setLoadState("error");
        }
      });
    return () => { active = false; };
  }, [idToken, assetRetry]);

  useEffect(() => {
    if (!idToken || visualPreview) return;
    let active = true;
    withTimeout(fetchPlanningData(idToken), 12000, "入力データ取得がタイムアウトしました。")
      .then((records) => { if (active) setPlanningData(records); })
      .catch((error: unknown) => {
        if (active) setPlanningError(error instanceof Error ? error.message : "入力データを取得できません。");
      });
    return () => { active = false; };
  }, [idToken]);

  useEffect(() => {
    if (!idToken || visualPreview) return;
    let active = true;
    setReviewLoading(true);
    setReviewError("");
    fetchMonthlyAssessment(idToken, targetMonth)
      .then((review) => {
        if (active) setMonthlyReview(review);
      })
      .catch((error: unknown) => {
        if (active) setReviewError(error instanceof Error ? error.message : "6か月のレビューを取得できません。");
      })
      .finally(() => {
        if (active) setReviewLoading(false);
      });
    return () => { active = false; };
  }, [idToken, targetMonth, reviewRevision]);

  const latest = snapshots[0];
  const meta = pageMeta[activeScreen];

  function navigate(screen: Screen) {
    setActiveScreen(screen);
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function refreshSnapshots() {
    return fetchAssetSnapshots(idToken).then(setSnapshots);
  }

  async function handleImport(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedFile) {
      setImportMessage("先にCSVファイルを選択してください。");
      setImportStatus("error");
      return;
    }
    setBusy(true);
    setImportMessage("CSVを検証・取り込み中です。");
    setImportStatus("");
    try {
      const result = await uploadCsv(idToken, selectedFile);
      const counts = [`新規 ${result.importedCount}件`];
      if (result.updatedCount) counts.push(`更新 ${result.updatedCount}件`);
      if (result.skippedDuplicateCount) counts.push(`重複スキップ ${result.skippedDuplicateCount}件`);
      if (result.skippedExcludedCount) counts.push(`集計対象外 ${result.skippedExcludedCount}件`);
      setImportMessage(`${result.type === "assets" ? "資産推移" : "取引"}CSVを取り込みました。${counts.join("、")}`);
      setImportStatus("success");
      if (result.type === "assets") await refreshSnapshots();
      if (result.type === "transactions") setReviewRevision((revision) => revision + 1);
      event.currentTarget.reset();
      setSelectedFile(null);
    } catch (error) {
      setImportMessage(error instanceof Error ? error.message : "CSVの取り込みに失敗しました。");
      setImportStatus("error");
    } finally {
      setBusy(false);
    }
  }

  function toggleCategory(category: string) {
    setSelectedCategories((current) => current.includes(category)
      ? current.filter((value) => value !== category)
      : [...current, category]);
  }

  const visibleCategories = selectedCategories.length === 0
    ? sampleHousehold.categories
    : sampleHousehold.categories.filter((category) => selectedCategories.includes(category.name));
  const totalAssets = latest?.totalAmount;
  const chartDataLabel = visualPreview ? "未接続" : "保存データ";
  const planningViewData = visualPreview ? previewPlanningData : planningData;
  const totalLiabilities = planningData.liabilities.reduce((total, item) => total + item.balance, 0);
  const liabilitiesShareAssetDate = planningData.liabilities.length > 0
    && latest != null
    && planningData.liabilities.every((item) => item.asOfDate === latest.snapshotDate);
  const netWorth = liabilitiesShareAssetDate && latest
    ? latest.totalAmount - totalLiabilities
    : null;

  return (
    <div className="catsflow-shell">
      <header className="app-header">
        <div className="header-inner">
          <a className="brand" href="#home" onClick={(event) => { event.preventDefault(); navigate("home"); }}>
            <span className="brand-mark" aria-hidden="true">C</span><span>Catsflow</span>
            <span className="app-version">v{appVersion}</span>
          </a>
          <span className="connection-state">
            {visualPreview ? "画面確認" : loadState === "loading" ? "LINE認証中" : loadState === "authenticated" ? "LINE認証済み・データ確認中" : loadState === "ready" ? <><i />LINE連携済み</> : loadState === "unlinked" ? "Catsflow未連携" : idToken ? "LINE認証済み・データ接続エラー" : "LINE接続エラー"}
          </span>
        </div>
      </header>

      <main className="screen-main">
        <div className="screen-heading">
          <div><h1>{meta.title}</h1></div>
          <span className="month-label"><CalendarDays size={15} />{new Intl.DateTimeFormat("ja-JP", { year: "numeric", month: "long" }).format(new Date(`${targetMonth}-01`))}</span>
        </div>

        {loadState === "error" && <div className="inline-alert error"><CircleHelp size={17} /><span>{authMessage}</span><button type="button" className="retry-button" onClick={() => idToken ? setAssetRetry((retry) => retry + 1) : window.location.reload()}>再試行</button></div>}
        {loadState === "unlinked" && <div className="inline-alert"><CircleHelp size={17} /><span>{authMessage} <a href="/line/login">LINE連携へ</a></span></div>}

        {activeScreen === "home" && (
          <div className="screen-stack">
            <section className="home-review section-panel">
              <div className="home-review-heading">
                <span className="review-icon"><Sparkles size={17} /></span>
                <h2>直近6か月の家計レビュー</h2>
                <DataLabel type="stored">数値ルール・外部送信なし</DataLabel>
              </div>
              {visualPreview ? (
                <div className="review-empty"><span>評価データは未接続です</span></div>
              ) : reviewLoading ? (
                <div className="review-empty"><span>データを集計しています</span></div>
              ) : reviewError ? (
                <div className="review-empty"><span>レビューを取得できません</span><p>{reviewError}</p></div>
              ) : monthlyReview ? (
                <>
                  <p className="review-period">対象期間 {monthlyReview.startMonth}〜{monthlyReview.endMonth}</p>
                  <div className="review-facts">
                    <div><span>収入</span><strong>{amount(monthlyReview.figures.income)}</strong></div>
                    <div><span>支出</span><strong>{amount(monthlyReview.figures.expense)}</strong></div>
                    <div><span>収支</span><strong>{amount(monthlyReview.figures.balance)}</strong></div>
                  </div>
                  <div className="review-comment"><p>{monthlyReview.reviewComment}</p></div>
                  {monthlyReview.dataQuality.length > 0 && <div className="review-data-quality"><strong>データ上の注意</strong>{monthlyReview.dataQuality.map((note) => <span key={note}>{note}</span>)}</div>}
                </>
              ) : <div className="review-empty"><span>レビューを表示できません</span></div>}
            </section>

            <section className="home-assets section-panel">
              <div className="panel-heading"><h2>資産推移</h2><DataLabel type={visualPreview ? "unavailable" : "stored"}>{chartDataLabel}</DataLabel></div>
              <div className="home-total"><span>最新の資産総額</span><strong>{totalAssets == null ? "未取得" : amount(totalAssets)}</strong><small>{latest ? `基準日 ${latest.snapshotDate}` : "資産推移データなし"}</small></div>
              {assetLoadState === "loading" && !visualPreview && <p className="inline-help">資産データを読み込んでいます。</p>}
              {assetLoadState === "error" && <p className="inline-help error-text">{assetError}</p>}
              <div className="chart-wrap"><AssetChart snapshots={snapshots} range={chartRange} onRangeChange={setChartRange} /></div>
              {snapshots.length > 0 && <div className="snapshot-caption"><span>{snapshots.length}件の基準日データ</span><span>ポイントを含む資産総額</span></div>}
            </section>
          </div>
        )}

        {activeScreen === "assets" && (
          <div className="screen-stack">
            <section className="balance-sheet section-panel" aria-label="資産と負債の一覧">
              <article className="balance-assets">
                <div className="balance-heading"><Wallet size={17} /><h2>総資産</h2></div>
                <strong className="balance-total">{totalAssets == null ? "未取得" : amount(totalAssets)}</strong>
                <small className="balance-date">{latest ? `基準日 ${latest.snapshotDate}` : "基準日データなし"}</small>
                <div className="balance-breakdown">
                  <Breakdown icon={Landmark} label="預金・現金" value={latest?.cashAmount} />
                  <Breakdown icon={LineChart} label="株式" value={latest?.stockAmount} />
                  <Breakdown icon={TrendingUp} label="投資信託" value={latest?.investmentTrustAmount} />
                  <Breakdown icon={PiggyBank} label="ポイント" value={latest?.pointAmount} />
                </div>
              </article>
              <div className="balance-right">
                <article className="balance-result liabilities-result">
                  <div className="balance-heading"><Landmark size={17} /><h2>総負債</h2></div>
                  <strong className="balance-total">{planningData.liabilities.length === 0 ? "未登録" : amount(totalLiabilities)}</strong>
                  <small className="balance-date">{planningData.liabilities.length === 0
                    ? "負債の登録がありません"
                    : `残高基準日 ${[...new Set(planningData.liabilities.map((item) => item.asOfDate))].join("・")}`}</small>
                </article>
                <article className="balance-result net-worth-result">
                  <div className="balance-heading"><LineChart size={17} /><h2>純資産</h2></div>
                  <strong className="balance-total">{netWorth == null ? "算出不可" : amount(netWorth)}</strong>
                  <small className="balance-date">{netWorth == null
                    ? "資産・負債の基準日を揃えてください"
                    : `基準日 ${latest?.snapshotDate}`}</small>
                </article>
              </div>
            </section>

            <section className="section-panel">
              <div className="panel-heading"><h2>資産推移</h2><DataLabel type={visualPreview ? "unavailable" : "stored"}>{chartDataLabel}</DataLabel></div>
              <div className="chart-wrap"><AssetChart snapshots={snapshots} range={chartRange} onRangeChange={setChartRange} /></div>
              {snapshots.length > 1 && <div className="chart-dates"><span>{[...snapshots].reverse()[0]?.snapshotDate}</span><span>{latest?.snapshotDate}</span></div>}
              <p className="quiet-note">ポイントを含む基準日付きスナップショットです。過去残高には加算しません。</p>
            </section>

            {planningError && <p className="planning-load-note">{planningError}</p>}
            {loadState === "ready" && <PlanningEditor token={idToken} initialData={planningViewData} mode="liabilities" disabled={visualPreview || Boolean(planningError)} onDataChange={setPlanningData} />}
            {loadState === "loading" && <p className="quiet-note">負債情報を読み込んでいます。</p>}

            <section className="section-panel" id="csv-import">
              <div className="panel-heading"><h2>CSV取り込み</h2><DataLabel type="unavailable">方式検討中</DataLabel></div>
              <p className="quiet-note">現在のCSV取込機能です。MoneyForward MEの正式な取込方式は設計検討中です。</p>
              <form className="upload-form" onSubmit={handleImport}>
                <input ref={fileInputRef} id="csv-file" className="file-input-hidden" type="file" accept=".csv,text/csv,application/vnd.ms-excel" tabIndex={-1} aria-hidden="true" disabled={loadState !== "ready" || busy || visualPreview} onChange={(event) => setSelectedFile(event.currentTarget.files?.[0] ?? null)} />
                <div className="file-select-row"><button className="secondary-action" type="button" disabled={loadState !== "ready" || busy || visualPreview} onClick={() => fileInputRef.current?.click()}><CloudUpload size={16} />ファイルを選択</button><span className="selected-file">{selectedFile?.name ?? "未選択"}</span></div>
                <button className="primary-action" type="submit" disabled={loadState !== "ready" || busy || visualPreview}>{busy ? "取り込み中..." : "取り込む"}<ArrowRight size={16} /></button>
                {visualPreview && <p className="inline-help">画面確認中は認証と取込操作を停止しています。</p>}
                {loadState === "unlinked" && <p className="inline-help">LINEアカウント連携後に利用できます。</p>}
                {importMessage && <p className={`import-message ${importStatus}`} role="status">{importMessage}</p>}
              </form>
            </section>
          </div>
        )}

        {activeScreen === "household" && (
          <div className="screen-stack">
            <section className="section-panel filter-panel">
              <div className="panel-heading"><h2>対象期間</h2><DataLabel>複数選択</DataLabel></div>
              <label className="field-label" htmlFor="target-month">年月</label>
              <input id="target-month" className="month-input" type="month" value={targetMonth} onChange={(event) => setTargetMonth(event.currentTarget.value)} />
              <div className="filter-divider" />
              <span className="field-label">表示カテゴリを複数選択</span>
              <div className="multi-select-list">
                {sampleHousehold.categories.map((category) => {
                  const checked = selectedCategories.includes(category.name);
                  return <button type="button" key={category.name} className={`multi-select-chip ${checked ? "selected" : ""}`} aria-pressed={checked} onClick={() => toggleCategory(category.name)}><span className="check-box">{checked && <Check size={12} />}</span>{category.name}</button>;
                })}
              </div>
              <p className="inline-help">選択UIのみのサンプルです。家計データAPIとCSV取込方式は確認・検討中です。</p>
            </section>

            <section className="section-panel">
              <div className="panel-heading"><h2>月間収支</h2><DataLabel>画面確認用サンプル</DataLabel></div>
              <p className="quiet-note">選択した年月にかかわらず固定のサンプル値です。実際の取引データではありません。</p>
              <div className="household-totals">
                <Metric label="収入" value={amount(sampleHousehold.income)} icon={ArrowDownLeft} tone="blue" />
                <Metric label="支出" value={amount(sampleHousehold.expense)} icon={ArrowUpRight} tone="amber" />
                <Metric label="収支" value={amount(sampleHousehold.income - sampleHousehold.expense)} icon={ArrowRight} />
              </div>
            </section>

            <section className="section-panel">
              <div className="panel-heading"><h2>カテゴリ別支出</h2><DataLabel>サンプル</DataLabel></div>
              <div className="breakdown-list">
                {visibleCategories.length === 0 ? <p className="quiet-note">カテゴリが選択されていません。</p> : visibleCategories.map((category) => <div className="breakdown-row" key={category.name}><span className={`category-swatch ${category.color}`} /><span className="breakdown-name">{category.name}</span><strong>{amount(category.amount)}</strong></div>)}
              </div>
            </section>

            <section className="section-panel">
              <div className="panel-heading"><h2>固定費・返済</h2><DataLabel>サンプル</DataLabel></div>
              <div className="breakdown-list">{sampleHousehold.fixedCosts.map((item) => <div className="breakdown-row" key={item.name}><span className="breakdown-icon"><ListFilter size={15} /></span><span className="breakdown-name">{item.name}<small>{item.note}</small></span><strong>{amount(item.amount)}</strong></div>)}</div>
            </section>
          </div>
        )}

        {activeScreen === "goals" && (
          <div className="screen-stack">
            {planningError && !visualPreview && <p className="planning-load-note">{planningError}</p>}
            {loadState === "ready" && <PlanningEditor token={idToken} initialData={planningViewData} mode="goals" disabled={visualPreview || Boolean(planningError)} onDataChange={setPlanningData} />}
            {loadState === "loading" && <p className="quiet-note">保存済みの入力情報を読み込んでいます。</p>}
          </div>
        )}
      </main>

      <nav className="bottom-nav" aria-label="メインナビゲーション">
        <div className="bottom-nav-inner">
          {navItems.map(({ id, label, icon: Icon }) => <button key={id} type="button" className={`nav-item ${activeScreen === id ? "active" : ""}`} aria-current={activeScreen === id ? "page" : undefined} onClick={() => navigate(id)}><Icon size={20} strokeWidth={activeScreen === id ? 2.2 : 1.8} /><span>{label}</span></button>)}
        </div>
      </nav>
    </div>
  );
}

function Metric({ label, value, icon: Icon, tone = "green", detail }: { label: string; value: string; icon: typeof Wallet; tone?: string; detail?: string }) {
  return <article className={`metric-card tone-${tone}`}><div className="metric-label"><span>{label}</span><Icon size={16} aria-hidden="true" /></div><strong>{value}</strong>{detail && <small>{detail}</small>}</article>;
}

function Breakdown({ icon: Icon, label, value }: { icon: typeof Wallet; label: string; value: number | null | undefined }) {
  return <div className="breakdown-row"><span className="breakdown-icon"><Icon size={15} /></span><span className="breakdown-name">{label}</span><strong>{value == null ? "未取得" : amount(value)}</strong></div>;
}

export default App;