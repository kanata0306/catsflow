import { useEffect, useRef, useState } from "react";
import type { FormEvent, ReactNode } from "react";
import {
  ArrowDownLeft,
  ArrowLeftRight,
  ArrowRight,
  ArrowUpRight,
  BadgeJapaneseYen,
  CalendarDays,
  Check,
  CircleHelp,
  CloudUpload,
  CreditCard,
  FileClock,
  Flag,
  HeartHandshake,
  Home,
  Landmark,
  LineChart,
  ListChecks,
  LockKeyhole,
  Menu,
  PiggyBank,
  Plus,
  Settings2,
  ShieldCheck,
  Sparkles,
  Target,
  TrendingUp,
  Wallet,
} from "lucide-react";
import liff from "@line/liff";
import { fetchAssetSnapshots, uploadCsv } from "./api/assets";
import { AssetApiError } from "./api/assets";
import type { AssetSnapshot } from "./api/assets";
import { sampleGoals, sampleHousehold, sampleInvestments, sampleLifeEvents } from "./data/sampleData";

type Screen = "home" | "household" | "assets" | "goals" | "lifePlan" | "menu";
type LoadState = "loading" | "ready" | "error" | "unlinked";

const currency = new Intl.NumberFormat("ja-JP", {
  style: "currency",
  currency: "JPY",
  maximumFractionDigits: 0,
});
const visualPreview = import.meta.env.DEV && new URLSearchParams(window.location.search).get("preview") === "1";

const navItems = [
  { id: "home", label: "ホーム", icon: Home },
  { id: "household", label: "家計", icon: ArrowLeftRight },
  { id: "assets", label: "資産", icon: Wallet },
  { id: "goals", label: "目標", icon: Target },
  { id: "menu", label: "メニュー", icon: Menu },
] as const;

const pageMeta: Record<Screen, { title: string; eyebrow: string; description: string }> = {
  home: { title: "ホーム", eyebrow: "OVERVIEW", description: "今月のお金の状況" },
  household: { title: "家計", eyebrow: "HOUSEHOLD", description: "月ごとの収支と予算" },
  assets: { title: "資産", eyebrow: "ASSETS", description: "資産の記録と推移" },
  goals: { title: "目標", eyebrow: "GOALS", description: "貯金・資産形成の目標" },
  lifePlan: { title: "ライフプラン", eyebrow: "LIFE PLAN", description: "将来の予定と前提" },
  menu: { title: "メニュー", eyebrow: "SETTINGS", description: "データとアカウント" },
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

function amount(value: number | null | undefined): string {
  return value == null ? "未登録" : currency.format(value);
}

function SampleTag() {
  return <span className="data-tag sample-tag">画面確認用サンプル</span>;
}

function RealTag() {
  return <span className="data-tag real-tag"><span className="real-dot" />保存データ</span>;
}

function ApiUnavailableTag() {
  return <span className="data-tag unavailable-tag">API未接続</span>;
}

function SampleNotice() {
  return (
    <div className="sample-notice" role="note">
      <span className="sample-notice-mark">S</span>
      <p>金額は画面確認用のサンプルです。実際のユーザーデータではありません。</p>
    </div>
  );
}

function SectionHeading({ title, note, action }: { title: string; note?: string; action?: ReactNode }) {
  return (
    <div className="section-heading">
      <div>
        <h2>{title}</h2>
        {note && <p className="section-note">{note}</p>}
      </div>
      {action}
    </div>
  );
}

function Metric({
  label,
  value,
  icon: Icon,
  tone = "green",
  detail,
}: {
  label: string;
  value: string;
  icon: typeof Wallet;
  tone?: string;
  detail?: string;
}) {
  return (
    <article className={`metric-card tone-${tone}`}>
      <div className="metric-topline"><span>{label}</span><Icon size={18} strokeWidth={1.8} aria-hidden="true" /></div>
      <strong className="metric-value">{value}</strong>
      {detail && <span className="metric-detail">{detail}</span>}
    </article>
  );
}

function ProgressBar({ value, tone = "green" }: { value: number; tone?: string }) {
  return (
    <div className={`progress-track tone-${tone}`} role="progressbar" aria-valuenow={Math.min(value, 100)} aria-valuemin={0} aria-valuemax={100}>
      <span style={{ width: `${Math.min(value, 100)}%` }} />
    </div>
  );
}

function TrendChart({ snapshots }: { snapshots: AssetSnapshot[] }) {
  const points = [...snapshots].reverse();
  const values = points.map((snapshot) => snapshot.totalAmount);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const span = max - min || 1;
  const coords = values.map((value, index) => ({
    x: 24 + (index * 672) / Math.max(values.length - 1, 1),
    y: 145 - ((value - min) / span) * 112,
  }));

  if (snapshots.length < 2) {
    return <div className="chart-empty">{snapshots.length === 0 ? "資産推移CSVを取り込むと表示されます。" : "推移の表示には異なる基準日のデータが2件以上必要です。"}</div>;
  }

  return (
    <svg className="trend-chart" viewBox="0 0 720 180" role="img" aria-label="保存済み資産総額の推移">
      <defs>
        <linearGradient id="asset-area" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="#237453" stopOpacity="0.16" />
          <stop offset="100%" stopColor="#237453" stopOpacity="0" />
        </linearGradient>
      </defs>
      {[38, 92, 146].map((y) => <line key={y} x1="12" x2="708" y1={y} y2={y} className="chart-grid" />)}
      <polygon points={`24,160 ${coords.map((point) => `${point.x},${point.y}`).join(" ")} 696,160`} fill="url(#asset-area)" />
      <polyline points={coords.map((point) => `${point.x},${point.y}`).join(" ")} className="chart-line" />
      {[coords[0], coords[coords.length - 1]].map((point) => (
        <circle key={`${point.x}-${point.y}`} cx={point.x} cy={point.y} r="4.5" className="chart-point" />
      ))}
    </svg>
  );
}

function App() {
  const [idToken, setIdToken] = useState("");
  const [snapshots, setSnapshots] = useState<AssetSnapshot[]>([]);
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [authMessage, setAuthMessage] = useState("");
  const [activeScreen, setActiveScreen] = useState<Screen>("home");
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [importMessage, setImportMessage] = useState("");
  const [importStatus, setImportStatus] = useState<"error" | "success" | "">("");
  const [targetMonth, setTargetMonth] = useState(() => new Date().toISOString().slice(0, 7));
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    let active = true;

    if (visualPreview) {
      setLoadState("ready");
      return () => { active = false; };
    }

    async function initialize() {
      try {
        await initializeLiff();
        if (!liff.isLoggedIn()) {
          liff.login({ redirectUri: window.location.href });
          return;
        }

        const token = liff.getIDToken();
        if (!token) throw new Error("LINE認証を取得できません。LIFFのopenid権限を確認してください。");
        if (!active) return;
        setIdToken(token);

        try {
          const records = await fetchAssetSnapshots(token);
          if (!active) return;
          setSnapshots(records);
          setLoadState("ready");
        } catch (error) {
          if (!active) return;
          if (error instanceof AssetApiError && error.status === 401) {
            setLoadState("unlinked");
            setAuthMessage("LINEアカウントとCatsflowを連携してください。");
            return;
          }
          throw error;
        }
      } catch (error) {
        if (!active) return;
        setAuthMessage(error instanceof Error ? error.message : "接続に失敗しました。");
        setLoadState("error");
      }
    }

    void initialize();
    return () => { active = false; };
  }, []);

  const latest = snapshots[0];
  const assetDataTag = visualPreview ? <ApiUnavailableTag /> : <RealTag />;
  const page = pageMeta[activeScreen];

  function navigate(screen: Screen) {
    setActiveScreen(screen);
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function goToCsvImport() {
    navigate("assets");
    window.setTimeout(() => document.getElementById("csv-import")?.scrollIntoView({ behavior: "smooth", block: "center" }), 120);
  }

  async function refreshSnapshots() {
    const records = await fetchAssetSnapshots(idToken);
    setSnapshots(records);
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
      event.currentTarget.reset();
      setSelectedFile(null);
    } catch (error) {
      setImportMessage(error instanceof Error ? error.message : "CSVの取り込みに失敗しました。");
      setImportStatus("error");
    } finally {
      setBusy(false);
    }
  }

  const sampleNotice = <SampleNotice />;

  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="header-inner">
          <a className="brand" href="#home" onClick={(event) => { event.preventDefault(); navigate("home"); }}>
            <span className="brand-mark" aria-hidden="true">C</span>
            <span>Catsflow</span>
          </a>
          <div className="header-status">
            {visualPreview ? "UIプレビュー" : loadState === "ready" ? <><span className="connection-dot" />LINE連携済み</> : loadState === "loading" ? "LINEを確認中" : "未連携"}
          </div>
        </div>
      </header>

      <main className="app-main">
        <div className="page-topline">
          <div>
            <p className="eyebrow">{page.eyebrow}</p>
            <h1>{page.title}</h1>
          </div>
          <span className="page-date"><CalendarDays size={15} aria-hidden="true" />{new Intl.DateTimeFormat("ja-JP", { year: "numeric", month: "long" }).format(new Date(`${targetMonth}-01`))}</span>
        </div>

        {loadState === "error" && <div className="global-alert error-alert"><CircleHelp size={18} />{authMessage}</div>}
        {loadState === "unlinked" && <div className="global-alert"><ShieldCheck size={18} /><span>{authMessage || "LINEアカウントとCatsflowを連携してください。"} <a href="/line/login">連携する</a></span></div>}

        {activeScreen === "home" && (
          <div className="screen-content">
            {sampleNotice}
            <section className="overview-section">
              <SectionHeading title="今月の家計" note="画面構成確認用のサンプル値" action={<SampleTag />} />
              <div className="metric-grid four-grid">
                <Metric label="収入" value={amount(sampleHousehold.income)} icon={ArrowDownLeft} tone="blue" />
                <Metric label="支出" value={amount(sampleHousehold.expense)} icon={ArrowUpRight} tone="amber" />
                <Metric label="収支" value={amount(sampleHousehold.income - sampleHousehold.expense)} icon={ArrowLeftRight} tone="green" />
                <Metric label="貯蓄率" value={`${sampleHousehold.savingsRate}%`} icon={PiggyBank} tone="rose" />
              </div>
            </section>

            <section className="overview-section">
              <SectionHeading title="資産の現在地" note="取り込み済みの資産推移データ" action={assetDataTag} />
              <div className="metric-grid two-grid">
                <Metric label="総資産" value={latest ? amount(latest.totalAmount) : "未取得"} icon={Wallet} detail={latest ? `基準日 ${latest.snapshotDate}` : "資産推移CSVを取り込んでください"} />
                <Metric label="純資産" value="算出不可" icon={LineChart} tone="blue" detail="負債データは未登録です" />
              </div>
            </section>

            <section className="overview-section">
              <SectionHeading title="目標の進捗" note="表示サンプル" action={<SampleTag />} />
              <div className="goal-summary-row">
                <div className="goal-summary-icon"><Flag size={19} /></div>
                <div className="goal-summary-main"><strong>{sampleGoals[0].name}</strong><span>{amount(sampleGoals[0].current)} / {amount(sampleGoals[0].target)}</span><ProgressBar value={sampleGoals[0].current / sampleGoals[0].target * 100} /></div>
                <b>{Math.round(sampleGoals[0].current / sampleGoals[0].target * 100)}%</b>
              </div>
            </section>

            <section className="overview-section">
              <SectionHeading title="投資積立" note="登録画面のUIサンプル" action={<SampleTag />} />
              <div className="investment-strip">
                <div className="investment-icon"><TrendingUp size={19} /></div>
                <div><strong>{sampleInvestments.length}件の積立計画</strong><span>月額予定 {amount(sampleInvestments.reduce((sum, item) => sum + item.monthly, 0))}</span></div>
                <span className="status-pill">計画値</span>
              </div>
            </section>

            <section className="review-preview">
              <div className="review-title"><span className="ai-mark"><Sparkles size={16} /></span><div><h2>月次レビュー</h2><span className="sample-inline">画面確認用</span></div></div>
              <p>評価データとAI機能は未実装です。ここには月ごとの収支・資産の要約を表示する予定です。</p>
            </section>

            <section className="overview-section shortcuts-section">
              <SectionHeading title="ショートカット" />
              <div className="shortcut-grid">
                <Shortcut icon={ArrowLeftRight} label="家計を確認" onClick={() => navigate("household")} />
                <Shortcut icon={Wallet} label="資産を見る" onClick={() => navigate("assets")} />
                <Shortcut icon={Target} label="目標を見る" onClick={() => navigate("goals")} />
                <Shortcut icon={CloudUpload} label="CSV取り込み" onClick={goToCsvImport} />
              </div>
            </section>
          </div>
        )}

        {activeScreen === "household" && (
          <div className="screen-content">
            {sampleNotice}
            <section className="month-control-row">
              <label htmlFor="target-month">対象年月</label>
              <input id="target-month" className="month-input" type="month" value={targetMonth} onChange={(event) => setTargetMonth(event.currentTarget.value)} />
            </section>
            <SectionHeading title="月間サマリー" note="年月切替UI・数値は固定サンプル" action={<SampleTag />} />
            <div className="metric-grid three-grid">
              <Metric label="収入" value={amount(sampleHousehold.income)} icon={ArrowDownLeft} tone="blue" />
              <Metric label="支出" value={amount(sampleHousehold.expense)} icon={ArrowUpRight} tone="amber" />
              <Metric label="収支" value={amount(sampleHousehold.income - sampleHousehold.expense)} icon={ArrowLeftRight} />
            </div>
            <section className="content-section">
              <SectionHeading title="予算と実績" note="全体予算のサンプル" action={<SampleTag />} />
              <div className="budget-panel">
                <div className="budget-amounts"><div><span>予算</span><strong>{amount(sampleHousehold.budget)}</strong></div><div><span>支出実績</span><strong>{amount(sampleHousehold.expense)}</strong></div><div><span>残額</span><strong>{amount(sampleHousehold.budget - sampleHousehold.expense)}</strong></div></div>
                <ProgressBar value={sampleHousehold.expense / sampleHousehold.budget * 100} tone="blue" />
                <div className="budget-foot"><span>予算使用率</span><strong>{Math.round(sampleHousehold.expense / sampleHousehold.budget * 100)}%</strong></div>
              </div>
            </section>
            <section className="content-section">
              <SectionHeading title="カテゴリ別支出" note="実データAPI未実装・サンプル表示" action={<SampleTag />} />
              <div className="category-chart-panel">
                <div className="category-bars" aria-label="カテゴリ別支出サンプル">
                  {sampleHousehold.categories.map((category) => <div key={category.name} className="category-bar-column"><div className={`category-bar bar-${category.color}`} style={{ height: `${Math.max(category.amount / 800, 8)}%` }} /><span>{category.name}</span></div>)}
                </div>
                <div className="category-list">
                  {sampleHousehold.categories.map((category) => <div className="category-row" key={category.name}><span className={`category-dot dot-${category.color}`} /><span className="category-name">{category.name}</span><strong>{amount(category.amount)}</strong></div>)}
                </div>
              </div>
            </section>
            <section className="content-section">
              <SectionHeading title="固定費・返済" note="登録内容のサンプル" action={<SampleTag />} />
              <div className="list-panel">{sampleHousehold.fixedCosts.map((item) => <div className="list-row" key={item.name}><div className="row-icon"><CreditCard size={16} /></div><div className="row-copy"><strong>{item.name}</strong><span>{item.note}</span></div><b>{amount(item.amount)}</b></div>)}</div>
            </section>
            <button type="button" className="secondary-action wide-action" onClick={goToCsvImport}><CloudUpload size={17} />CSV取り込みへ</button>
          </div>
        )}

        {activeScreen === "assets" && (
          <div className="screen-content">
            <SectionHeading title="残高サマリー" note="CSVから保存された最新データ" action={assetDataTag} />
            <div className="metric-grid three-grid">
              <Metric label="総資産" value={latest ? amount(latest.totalAmount) : "未取得"} icon={Wallet} detail={latest ? `基準日 ${latest.snapshotDate}` : "資産推移CSVを取り込んでください"} />
              <Metric label="総負債" value="未登録" icon={Landmark} tone="amber" detail="負債データの登録機能は未実装" />
              <Metric label="純資産" value="算出不可" icon={LineChart} tone="blue" detail="負債データが未登録です" />
            </div>
            <section className="content-section">
              <SectionHeading title="資産推移" note="資産推移CSVの基準日ごとの値" action={assetDataTag} />
              <div className="chart-panel"><TrendChart snapshots={snapshots} />{snapshots.length > 1 && <div className="chart-endpoints"><span>{[...snapshots].reverse()[0]?.snapshotDate}</span><span>{snapshots[0]?.snapshotDate}</span></div>}</div>
            </section>
            <section className="content-section">
              <SectionHeading title="最新の内訳" note={latest ? `基準日 ${latest.snapshotDate}` : "保存済みデータなし"} action={assetDataTag} />
              <div className="list-panel breakdown-list">
                <Breakdown icon={Landmark} label="預金・現金" value={latest?.cashAmount} />
                <Breakdown icon={LineChart} label="株式" value={latest?.stockAmount} />
                <Breakdown icon={TrendingUp} label="投資信託" value={latest?.investmentTrustAmount} />
                <Breakdown icon={BadgeJapaneseYen} label="ポイント" value={latest?.pointAmount} />
              </div>
            </section>
            <section className="content-section">
              <SectionHeading title="投資積立計画" note="画面確認用サンプル・実績ではありません" action={<SampleTag />} />
              <div className="list-panel">{sampleInvestments.map((item) => <div className="list-row" key={item.name}><div className="row-icon investment-row-icon"><TrendingUp size={16} /></div><div className="row-copy"><strong>{item.name}</strong><span>{item.status}・計画値</span></div><b>{amount(item.monthly)}<small> / 月</small></b></div>)}</div>
            </section>
            <section className="content-section">
              <SectionHeading title="負債・返済" note="負債API・登録機能は未実装" />
              <div className="empty-panel"><Landmark size={20} /><div><strong>負債データは未登録です</strong><span>残高・返済情報を入力する機能は今後追加します。</span></div></div>
            </section>
            <section className="content-section" id="csv-import">
              <SectionHeading title="CSVデータ取り込み" note="MoneyForward MEのCSVに対応" />
              <form className="upload-panel" onSubmit={handleImport}>
                <p className="upload-copy">入出金CSVまたは資産推移CSVを選択できます。</p>
                <div className="file-picker">
                  <span className="file-label">CSVファイル</span>
                  <input ref={fileInputRef} id="csv-file" name="file" className="file-input-hidden" type="file" accept=".csv,text/csv,application/vnd.ms-excel" tabIndex={-1} aria-hidden="true" disabled={loadState !== "ready" || busy || visualPreview} onChange={(event) => setSelectedFile(event.currentTarget.files?.[0] ?? null)} />
                  <button className="file-select-button" type="button" disabled={loadState !== "ready" || busy || visualPreview} onClick={() => fileInputRef.current?.click()}><CloudUpload size={17} />ファイルを選択</button>
                  <span className="selected-file" aria-live="polite">{selectedFile?.name ?? "ファイルが選択されていません"}</span>
                </div>
                <button className="primary-action" type="submit" disabled={loadState !== "ready" || busy || visualPreview}>{busy ? "取り込み中..." : "取り込む"}<ArrowRight size={17} /></button>
                {visualPreview && <p className="status">UIプレビュー中のため、認証とCSV操作は停止しています。</p>}
                {loadState !== "ready" && <p className="status error">{loadState === "loading" ? "LINE連携を確認しています。" : "LINEアカウントの連携が必要です。"}</p>}
                {importMessage && <p className={`status ${importStatus}`} role="status" aria-live="polite">{importMessage}</p>}
              </form>
            </section>
            <section className="content-section">
              <SectionHeading title="取込履歴" note={`${snapshots.length}件の資産スナップショット`} action={assetDataTag} />
              <div className="table-panel"><table><thead><tr><th>基準日</th><th>資産総額</th><th>預金・現金</th><th>株式</th><th>投資信託</th></tr></thead><tbody>{snapshots.length === 0 ? <tr><td className="empty" colSpan={5}>資産推移CSVを取り込むと履歴が表示されます。</td></tr> : snapshots.map((snapshot) => <tr key={snapshot.snapshotDate}><td>{snapshot.snapshotDate}</td><td>{amount(snapshot.totalAmount)}</td><td>{amount(snapshot.cashAmount)}</td><td>{amount(snapshot.stockAmount)}</td><td>{amount(snapshot.investmentTrustAmount)}</td></tr>)}</tbody></table></div>
            </section>
          </div>
        )}

        {activeScreen === "goals" && (
          <div className="screen-content">
            {sampleNotice}
            <SectionHeading title="目標一覧" note="目標保存API未実装・以下は画面確認用サンプル" action={<SampleTag />} />
            <div className="goal-list">{sampleGoals.map((goal) => {
              const progress = Math.round(goal.current / goal.target * 100);
              const remainingMonths = Math.max(1, (Number(goal.deadline.slice(0, 4)) - new Date().getFullYear()) * 12 + Number(goal.deadline.slice(5, 7)) - new Date().getMonth() - 1);
              const neededMonthly = Math.ceil((goal.target - goal.current) / remainingMonths);
              return <article className="goal-card" key={goal.name}><div className="goal-card-top"><span className={`goal-type type-${goal.color}`}>{goal.type}</span><span className="sample-inline">サンプル</span></div><h2>{goal.name}</h2><div className="goal-progress-line"><strong>{progress}%</strong><span>達成</span></div><ProgressBar value={progress} tone={goal.color} /><div className="goal-values"><div><span>達成金額</span><strong>{amount(goal.current)}</strong></div><div><span>目標金額</span><strong>{amount(goal.target)}</strong></div></div><div className="goal-footer"><span><CalendarDays size={14} />期限 {goal.deadline}</span><span>必要額 {amount(neededMonthly)} / 月</span></div></article>;
            })}</div>
            <div className="empty-panel future-panel"><Plus size={19} /><div><strong>目標の追加・編集</strong><span>登録機能は未実装です。現在のサンプルは保存されません。</span></div><span className="coming-soon">準備中</span></div>
          </div>
        )}

        {activeScreen === "lifePlan" && (
          <div className="screen-content">
            {sampleNotice}
            <section className="fact-estimate-grid">
              <article className="fact-panel"><div className="fact-heading"><span className="fact-icon"><Check size={16} /></span><div><h2>入力した事実</h2><span>ユーザー入力</span></div></div><p>ライフイベントはまだ登録されていません。</p></article>
              <article className="estimate-panel"><div className="fact-heading"><span className="estimate-icon"><Sparkles size={16} /></span><div><h2>将来の試算</h2><span>仮定・予測</span></div></div><p>シミュレーション機能は未実装です。実績データとは分離して表示する構成です。</p></article>
            </section>
            <section className="content-section">
              <SectionHeading title="予定しているイベント" note="入力例です。実際の計画として保存されていません。" action={<SampleTag />} />
              <div className="timeline">{sampleLifeEvents.map((event) => <article className="timeline-item" key={event.title}><div className="timeline-year">{event.year}</div><div className="timeline-mark" /><div className="timeline-content"><div className="timeline-title"><div><strong>{event.title}</strong><span>{event.category}・入力例</span></div><b>{amount(event.amount)}</b></div></div></article>)}</div>
            </section>
            <section className="content-section">
              <SectionHeading title="将来の資産推移" note="予測ロジック未実装" />
              <div className="empty-panel"><LineChart size={20} /><div><strong>シミュレーションはまだありません</strong><span>収入・支出の見込みやイベント条件を登録後に表示する予定です。</span></div></div>
            </section>
            <button className="secondary-action wide-action" type="button" disabled><Plus size={17} />ライフイベント登録は準備中</button>
          </div>
        )}

        {activeScreen === "menu" && (
          <div className="screen-content menu-content">
            <section className="menu-group">
              <SectionHeading title="データ" />
              <MenuRow icon={CloudUpload} title="CSVデータ取り込み" detail="入出金・資産推移CSV" onClick={goToCsvImport} />
              <MenuRow icon={ListChecks} title="データ管理" detail="取引の修正・削除は未実装" disabled />
              <MenuRow icon={FileClock} title="月次評価履歴" detail="評価履歴の保存は未実装" disabled />
            </section>
            <section className="menu-group">
              <SectionHeading title="計画と設定" />
              <MenuRow icon={HeartHandshake} title="ライフプラン" detail="予定イベントと将来の前提" onClick={() => navigate("lifePlan")} />
              <MenuRow icon={Sparkles} title="AI設定" detail="AI機能は未実装" disabled />
              <MenuRow icon={Settings2} title="ライフプラン設定" detail="設定機能は未実装" disabled />
            </section>
            <section className="menu-group">
              <SectionHeading title="アカウント" />
              <MenuRow icon={ShieldCheck} title="LINE連携" detail={visualPreview ? "プレビュー・未認証" : loadState === "ready" ? "連携済み" : loadState === "loading" ? "確認中" : "未連携"} disabled={visualPreview || loadState === "ready"} onClick={() => { window.location.href = "/line/login"; }} />
              <MenuRow icon={LockKeyhole} title="プライバシー" detail="データの利用について" disabled />
              <MenuRow icon={CircleHelp} title="ヘルプ" detail="お問い合わせ・使い方" disabled />
            </section>
            <p className="menu-version">Catsflow Mini App</p>
          </div>
        )}
      </main>

      <nav className="bottom-nav" aria-label="メインナビゲーション">
        <div className="bottom-nav-inner">
          {navItems.map(({ id, label, icon: Icon }) => {
            const selected = id === activeScreen || (id === "menu" && activeScreen === "lifePlan");
            return <button key={id} type="button" className={`nav-item ${selected ? "active" : ""}`} aria-current={selected ? "page" : undefined} onClick={() => navigate(id)}><Icon size={21} strokeWidth={selected ? 2.2 : 1.8} /><span>{label}</span></button>;
          })}
        </div>
      </nav>
    </div>
  );
}

function Shortcut({ icon: Icon, label, onClick }: { icon: typeof Wallet; label: string; onClick: () => void }) {
  return <button type="button" className="shortcut-button" onClick={onClick}><span><Icon size={18} /></span><b>{label}</b><ArrowRight size={15} /></button>;
}

function Breakdown({ icon: Icon, label, value }: { icon: typeof Wallet; label: string; value: number | null | undefined }) {
  return <div className="list-row"><div className="row-icon"><Icon size={16} /></div><div className="row-copy"><strong>{label}</strong></div><b>{value == null ? "未取得" : amount(value)}</b></div>;
}

function MenuRow({ icon: Icon, title, detail, onClick, disabled = false }: { icon: typeof Wallet; title: string; detail: string; onClick?: () => void; disabled?: boolean }) {
  return <button type="button" className={`menu-row ${disabled ? "menu-row-disabled" : ""}`} onClick={onClick} disabled={disabled}><span className="menu-icon"><Icon size={18} /></span><span className="menu-row-copy"><strong>{title}</strong><small>{detail}</small></span>{disabled ? <span className="coming-soon">準備中</span> : <ArrowRight size={17} className="menu-arrow" />}</button>;
}

export default App;