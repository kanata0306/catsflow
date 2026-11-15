import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { ArrowLeft, Check, ChevronRight, Pencil, Plus, Trash2, X } from "lucide-react";
import {
  fetchPlanningData,
  removeGoal,
  removeLiability,
  removeLifeEvent,
  saveGoal,
  saveLiability,
  saveLifeEvent,
  saveProfile,
} from "../api/planning";
import type {
  FinancialGoal,
  FinancialProfile,
  GoalInput,
  LifeEventInput,
  LifePlanEvent,
  LiabilityInput,
  LongTermLiability,
  ProfileInput,
} from "../api/planning";

type PlanningData = {
  profile: FinancialProfile | null;
  liabilities: LongTermLiability[];
  goals: FinancialGoal[];
  lifeEvents: LifePlanEvent[];
};

type Props = {
  token: string;
  initialData: PlanningData;
  mode?: "goals" | "liabilities";
  disabled?: boolean;
  onDataChange: (data: PlanningData) => void;
};

const emptyProfile: ProfileInput = {
  housingType: "",
  monthlyLivingCost: null,
  incomeStability: "",
  monthlyFamilySupport: null,
  priorities: "",
};

const emptyLiability: LiabilityInput = {
  name: "",
  type: "student_loan",
  balance: 0,
  asOfDate: new Date().toISOString().slice(0, 10),
  annualInterestRate: null,
  monthlyPayment: null,
  paymentDay: null,
  note: "",
};

const emptyGoal: GoalInput = {
  type: "savings",
  name: "",
  targetAmount: 0,
  currentAmount: 0,
  targetMonth: new Date(new Date().getFullYear() + 1, new Date().getMonth()).toISOString().slice(0, 7),
  monthlyContribution: null,
  status: "active",
};

const emptyEvent: LifeEventInput = {
  title: "",
  category: "housing",
  eventMonth: new Date(new Date().getFullYear() + 1, new Date().getMonth()).toISOString().slice(0, 7),
  estimatedAmount: null,
  entryType: "fact",
  note: "",
};

const money = new Intl.NumberFormat("ja-JP", { style: "currency", currency: "JPY", maximumFractionDigits: 0 });

function errorText(error: unknown): string {
  return error instanceof Error ? error.message : "保存できませんでした。";
}

function optionalNumber(value: string): number | null {
  return value.trim() === "" ? null : Number(value);
}

function lifeCategoryLabel(category: string): string {
  const labels: Record<string, string> = {
    housing: "住居", vehicle: "車・移動", education: "教育", family_support: "家族への支援",
    career: "仕事・収入", large_purchase: "大きな支出", other: "その他",
  };
  return labels[category] ?? category;
}

function housingTypeLabel(value: string | null | undefined): string {
  const labels: Record<string, string> = {
    living_with_family: "実家・家族と同居", renting: "賃貸", owned: "持ち家", other: "その他",
  };
  return value ? labels[value] ?? value : "未設定";
}

function incomeStabilityLabel(value: string | null | undefined): string {
  const labels: Record<string, string> = {
    stable: "安定", variable: "変動あり", unstable: "不安定", unknown: "不明",
  };
  return value ? labels[value] ?? value : "未設定";
}

function profileInputFromData(data: FinancialProfile | null): ProfileInput {
  return data ? {
    housingType: data.housingType ?? "",
    monthlyLivingCost: data.monthlyLivingCost,
    incomeStability: data.incomeStability ?? "",
    monthlyFamilySupport: data.monthlyFamilySupport,
    priorities: data.priorities ?? "",
  } : emptyProfile;
}

export default function PlanningEditor({ token, initialData, mode = "goals", disabled = false, onDataChange }: Props) {
  const [profile, setProfile] = useState<ProfileInput>(emptyProfile);
  const [liability, setLiability] = useState<LiabilityInput>(emptyLiability);
  const [goal, setGoal] = useState<GoalInput>(emptyGoal);
  const [event, setEvent] = useState<LifeEventInput>(emptyEvent);
  const [editingLiability, setEditingLiability] = useState<number | null>(null);
  const [editingGoal, setEditingGoal] = useState<number | null>(null);
  const [selectedGoalId, setSelectedGoalId] = useState<number | null>(null);
  const [addingGoal, setAddingGoal] = useState(false);
  const [editingEvent, setEditingEvent] = useState<number | null>(null);
  const [selectedEventId, setSelectedEventId] = useState<number | null>(null);
  const [addingEvent, setAddingEvent] = useState(false);
  const [editingProfile, setEditingProfile] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState(false);

  useEffect(() => {
    setProfile(profileInputFromData(initialData.profile));
  }, [initialData.profile]);

  const selectedGoal = initialData.goals.find((item) => item.id === selectedGoalId) ?? null;
  const selectedEvent = initialData.lifeEvents.find((item) => item.id === selectedEventId) ?? null;
  const isPlanningOverview = selectedGoalId === null && !addingGoal
    && selectedEventId === null && !addingEvent && editingEvent === null && !editingProfile;

  async function reload() {
    onDataChange(await fetchPlanningData(token));
  }

  async function runAction(action: () => Promise<void>, successMessage: string) {
    setBusy(true);
    setMessage("");
    setError(false);
    try {
      await action();
      await reload();
      setMessage(successMessage);
    } catch (actionError) {
      setMessage(errorText(actionError));
      setError(true);
    } finally {
      setBusy(false);
    }
  }

  function submitProfile(formEvent: FormEvent<HTMLFormElement>) {
    formEvent.preventDefault();
    void runAction(async () => {
      await saveProfile(token, profile);
      setEditingProfile(false);
    }, "生活プロフィールを保存しました。");
  }

  function submitLiability(formEvent: FormEvent<HTMLFormElement>) {
    formEvent.preventDefault();
    void runAction(async () => {
      await saveLiability(token, liability, editingLiability ?? undefined);
      setLiability(emptyLiability);
      setEditingLiability(null);
    }, editingLiability ? "長期負債を更新しました。" : "長期負債を追加しました。");
  }

  function submitGoal(formEvent: FormEvent<HTMLFormElement>) {
    formEvent.preventDefault();
    void runAction(async () => {
      await saveGoal(token, goal, editingGoal ?? undefined);
      setGoal(emptyGoal);
      setEditingGoal(null);
      setAddingGoal(false);
    }, editingGoal ? "目標を更新しました。" : "目標を追加しました。");
  }

  function submitEvent(formEvent: FormEvent<HTMLFormElement>) {
    formEvent.preventDefault();
    const wasEditing = editingEvent !== null;
    void runAction(async () => {
      await saveLifeEvent(token, event, editingEvent ?? undefined);
      setEvent(emptyEvent);
      setEditingEvent(null);
      setAddingEvent(false);
      if (!wasEditing) setSelectedEventId(null);
    }, editingEvent ? "ライフイベントを更新しました。" : "ライフイベントを追加しました。");
  }

  function editLiability(item: LongTermLiability) {
    setEditingLiability(item.id);
    setLiability({
      name: item.name,
      type: item.type,
      balance: item.balance,
      asOfDate: item.asOfDate,
      annualInterestRate: item.annualInterestRate,
      monthlyPayment: item.monthlyPayment,
      paymentDay: item.paymentDay,
      note: item.note ?? "",
    });
  }

  function editGoal(item: FinancialGoal) {
    setEditingGoal(item.id);
    setGoal({
      type: item.type,
      name: item.name,
      targetAmount: item.targetAmount,
      currentAmount: item.currentAmount,
      targetMonth: item.targetMonth ?? "",
      monthlyContribution: item.monthlyContribution,
      status: item.status,
    });
  }

  function startAddingGoal() {
    setGoal(emptyGoal);
    setEditingGoal(null);
    setAddingGoal(true);
  }

  function editEvent(item: LifePlanEvent) {
    setSelectedEventId(item.id);
    setAddingEvent(false);
    setEditingEvent(item.id);
    setEvent({
      title: item.title,
      category: item.category,
      eventMonth: item.eventMonth,
      estimatedAmount: item.estimatedAmount,
      entryType: item.entryType,
      note: item.note ?? "",
    });
  }

  function startAddingEvent() {
    setEvent(emptyEvent);
    setEditingEvent(null);
    setSelectedEventId(null);
    setAddingEvent(true);
  }

  return (
    <div className="planning-editor">
      {mode === "goals" && isPlanningOverview && (
        <section className="section-panel profile-summary">
          <div className="panel-heading"><h2>生活プロフィール</h2><button type="button" className="text-action" onClick={() => setEditingProfile(true)} disabled={disabled || busy}><Pencil size={14} />編集</button></div>
          <dl className="profile-summary-list">
            <div><dt>住居形態</dt><dd>{housingTypeLabel(initialData.profile?.housingType)}</dd></div>
            <div><dt>月間生活費</dt><dd>{initialData.profile?.monthlyLivingCost == null ? "未設定" : money.format(initialData.profile.monthlyLivingCost)}</dd></div>
            <div><dt>収入の安定性</dt><dd>{incomeStabilityLabel(initialData.profile?.incomeStability)}</dd></div>
            <div><dt>毎月の家族支援額</dt><dd>{initialData.profile?.monthlyFamilySupport == null ? "未設定" : money.format(initialData.profile.monthlyFamilySupport)}</dd></div>
            <div className="profile-priorities"><dt>優先事項</dt><dd>{initialData.profile?.priorities?.trim() || "未設定"}</dd></div>
          </dl>
        </section>
      )}

      {mode === "goals" && editingProfile && (
        <section className="section-panel profile-edit-page">
          <div className="panel-heading"><button type="button" className="detail-back" aria-label="目標一覧へ戻る" onClick={() => { setProfile(profileInputFromData(initialData.profile)); setEditingProfile(false); }}><ArrowLeft size={17} /></button><h2>生活プロフィールを編集</h2></div>
          <p className="quiet-note">未入力は「なし」や0円として扱いません。</p>
          <form className="planning-form" onSubmit={submitProfile}>
            <label>住居形態<select value={profile.housingType ?? ""} disabled={disabled || busy} onChange={(e) => setProfile({ ...profile, housingType: e.currentTarget.value })}><option value="">未設定</option><option value="living_with_family">実家・家族と同居</option><option value="renting">賃貸</option><option value="owned">持ち家</option><option value="other">その他</option></select></label>
            <label>月間生活費（円）<input type="number" min="0" step="1" placeholder="未設定" value={profile.monthlyLivingCost ?? ""} disabled={disabled || busy} onChange={(e) => setProfile({ ...profile, monthlyLivingCost: optionalNumber(e.currentTarget.value) })} /></label>
            <label>収入の安定性<select value={profile.incomeStability ?? ""} disabled={disabled || busy} onChange={(e) => setProfile({ ...profile, incomeStability: e.currentTarget.value })}><option value="">未設定</option><option value="stable">安定</option><option value="variable">変動あり</option><option value="unstable">不安定</option><option value="unknown">不明</option></select></label>
            <label>毎月の家族支援額（円）<input type="number" min="0" step="1" placeholder="未設定" value={profile.monthlyFamilySupport ?? ""} disabled={disabled || busy} onChange={(e) => setProfile({ ...profile, monthlyFamilySupport: optionalNumber(e.currentTarget.value) })} /></label>
            <label className="span-all">優先事項<textarea maxLength={1000} rows={2} placeholder="例：生活防衛資金を確保してから積立を増やす" value={profile.priorities ?? ""} disabled={disabled || busy} onChange={(e) => setProfile({ ...profile, priorities: e.currentTarget.value })} /></label>
            <div className="form-actions span-all"><button className="primary-action" type="submit" disabled={disabled || busy}>{busy ? "保存中..." : "保存する"}<Check size={15} /></button><button className="cancel-action" type="button" onClick={() => { setProfile(profileInputFromData(initialData.profile)); setEditingProfile(false); }} disabled={busy}><X size={14} />キャンセル</button></div>
          </form>
        </section>
      )}

      {mode === "liabilities" && (
      <section className="planning-section">
        <div className="planning-section-heading"><h2>長期負債・返済</h2><span className="data-label">本人入力</span></div>
        <p className="quiet-note">奨学金・住宅ローン等。カード残高など短期債務は登録対象外です。</p>
        <div className="planning-record-list">
          {initialData.liabilities.length === 0 && <p className="empty-inline">長期負債はまだ登録されていません。</p>}
          {initialData.liabilities.map((item) => <article className="planning-record" key={item.id}><div className="planning-record-main"><strong>{item.name}</strong><span>基準日 {item.asOfDate}・年利 {item.annualInterestRate == null ? "未設定" : `${item.annualInterestRate}%`}</span><b>残高 {money.format(item.balance)}{item.monthlyPayment != null && <small> ・月返済 {money.format(item.monthlyPayment)}</small>}</b></div><div className="record-actions"><button type="button" aria-label={`${item.name}を編集`} onClick={() => editLiability(item)} disabled={disabled || busy}><Pencil size={14} /></button><button type="button" aria-label={`${item.name}を削除`} onClick={() => void runAction(async () => { await removeLiability(token, item.id); }, "長期負債を削除しました。")} disabled={disabled || busy}><Trash2 size={14} /></button></div></article>)}
        </div>
        <details className="editor-details" open={editingLiability !== null}>
          <summary><Plus size={15} />{editingLiability ? "長期負債を編集" : "長期負債を追加"}</summary>
          <form className="planning-form" onSubmit={submitLiability}>
            <label>負債名<input required maxLength={120} value={liability.name} disabled={disabled || busy} onChange={(e) => setLiability({ ...liability, name: e.currentTarget.value })} placeholder="例：奨学金" /></label>
            <label>種類<select value={liability.type} disabled={disabled || busy} onChange={(e) => setLiability({ ...liability, type: e.currentTarget.value })}><option value="student_loan">奨学金</option><option value="mortgage">住宅ローン</option><option value="car_loan">自動車ローン</option><option value="other">その他の長期負債</option></select></label>
            <label>現在残高（円）<input required type="number" min="0" step="1" value={liability.balance} disabled={disabled || busy} onChange={(e) => setLiability({ ...liability, balance: Number(e.currentTarget.value) })} /></label>
            <label>残高の基準日<input required type="date" value={liability.asOfDate} disabled={disabled || busy} onChange={(e) => setLiability({ ...liability, asOfDate: e.currentTarget.value })} /></label>
            <label>年利（%・不明なら空欄）<input type="number" min="0" step="0.0001" placeholder="未設定" value={liability.annualInterestRate ?? ""} disabled={disabled || busy} onChange={(e) => setLiability({ ...liability, annualInterestRate: optionalNumber(e.currentTarget.value) })} /></label>
            <label>月々の返済額（円）<input type="number" min="0" step="1" placeholder="未設定" value={liability.monthlyPayment ?? ""} disabled={disabled || busy} onChange={(e) => setLiability({ ...liability, monthlyPayment: optionalNumber(e.currentTarget.value) })} /></label>
            <label>返済日（任意）<input type="number" min="1" max="31" placeholder="1〜31" value={liability.paymentDay ?? ""} disabled={disabled || busy} onChange={(e) => setLiability({ ...liability, paymentDay: optionalNumber(e.currentTarget.value) })} /></label>
            <label>メモ<textarea maxLength={1000} rows={2} value={liability.note ?? ""} disabled={disabled || busy} onChange={(e) => setLiability({ ...liability, note: e.currentTarget.value })} /></label>
            <div className="form-actions span-all"><button className="primary-action" type="submit" disabled={disabled || busy}>{editingLiability ? "更新する" : "登録する"}</button>{editingLiability && <button className="cancel-action" type="button" onClick={() => { setEditingLiability(null); setLiability(emptyLiability); }}><X size={14} />キャンセル</button>}</div>
          </form>
        </details>
      </section>
      )}

      {mode === "goals" && <>
      {selectedGoal && (
        <section className="section-panel goal-detail">
          <div className="goal-detail-heading">
            <button type="button" className="detail-back" aria-label="目標一覧へ戻る" onClick={() => { setSelectedGoalId(null); setEditingGoal(null); }}><ArrowLeft size={17} /></button>
            <div><span className={`goal-type ${selectedGoal.type}`}>{selectedGoal.type === "savings" ? "貯金" : "資産形成"}</span><h2>{selectedGoal.name}</h2></div>
          </div>
          {editingGoal === selectedGoal.id ? (
            <GoalFields goal={goal} onChange={setGoal} onSubmit={submitGoal} onCancel={() => { setEditingGoal(null); setGoal(emptyGoal); }} disabled={disabled} busy={busy} submitLabel="更新する" />
          ) : (
            <>
              <div className="goal-detail-progress"><div className="goal-record-progress"><div style={{ width: `${Math.min(100, selectedGoal.targetAmount > 0 ? selectedGoal.currentAmount / selectedGoal.targetAmount * 100 : 0)}%` }} /></div><strong>{Math.round(Math.min(100, selectedGoal.targetAmount > 0 ? selectedGoal.currentAmount / selectedGoal.targetAmount * 100 : 0))}%</strong></div>
              <dl className="goal-detail-grid">
                <div><dt>現在額</dt><dd>{money.format(selectedGoal.currentAmount)}</dd></div>
                <div><dt>目標金額</dt><dd>{money.format(selectedGoal.targetAmount)}</dd></div>
                <div><dt>目標期限</dt><dd>{selectedGoal.targetMonth ?? "未設定"}</dd></div>
                <div><dt>月々の積立予定額</dt><dd>{selectedGoal.monthlyContribution == null ? "未設定" : money.format(selectedGoal.monthlyContribution)}</dd></div>
                <div><dt>状態</dt><dd>{selectedGoal.status === "active" ? "進行中" : selectedGoal.status === "paused" ? "一時停止" : "達成"}</dd></div>
              </dl>
              <div className="form-actions goal-detail-actions">
                <button className="primary-action" type="button" onClick={() => editGoal(selectedGoal)} disabled={disabled || busy}><Pencil size={15} />編集</button>
                <button className="cancel-action" type="button" onClick={() => void runAction(async () => { await removeGoal(token, selectedGoal.id); setSelectedGoalId(null); }, "目標を削除しました。")} disabled={disabled || busy}><Trash2 size={15} />削除</button>
              </div>
            </>
          )}
        </section>
      )}

      {isPlanningOverview && <>
      <section className="section-panel goal-groups asset-goals-card">
        <div className="panel-heading"><h2>資産形成目標</h2></div>
        <div className="goal-list">
          {initialData.goals.length === 0 && <p className="empty-inline">目標はまだありません。</p>}
          {initialData.goals.map((item) => {
            const progress = Math.min(100, item.targetAmount > 0 ? item.currentAmount / item.targetAmount * 100 : 0);
            return <button className="goal-card-button" type="button" key={item.id} onClick={() => { setSelectedGoalId(item.id); setEditingGoal(null); }}>
              <span className="goal-card-main"><span className={`goal-type ${item.type}`}>{item.type === "savings" ? "貯金" : "資産形成"}</span><strong>{item.name}</strong><span>現在 {money.format(item.currentAmount)} / 目標 {money.format(item.targetAmount)}・{Math.round(progress)}%</span><span className="goal-record-progress"><span style={{ width: `${progress}%` }} /></span><small>期限 {item.targetMonth ?? "未設定"}・月額 {item.monthlyContribution == null ? "未設定" : money.format(item.monthlyContribution)}</small></span>
              <ChevronRight size={17} aria-hidden="true" />
            </button>;
          })}
        </div>
        <button className="goal-add-button" type="button" onClick={startAddingGoal} disabled={disabled || busy}><Plus size={16} />目標を追加</button>
      </section>

      <section className="section-panel life-events-card">
        <div className="panel-heading"><h2>ライフイベント</h2><span className="data-label">事実・仮定を区別</span></div>
        <div className="life-event-cards">
          {initialData.lifeEvents.length === 0 && <p className="empty-inline">ライフイベントはまだ登録されていません。</p>}
          {initialData.lifeEvents.map((item) => <button className="life-event-button" type="button" key={item.id} onClick={() => { setSelectedEventId(item.id); setEditingEvent(null); }}>
            <span className="life-event-button-main"><span className={`event-type ${item.entryType}`}>{item.entryType === "fact" ? "入力した事実" : "仮定・予測"}</span><strong>{item.title}</strong><small>{item.eventMonth}・{lifeCategoryLabel(item.category)}・{item.estimatedAmount == null ? "金額未設定" : money.format(item.estimatedAmount)}</small></span>
            <ChevronRight size={17} aria-hidden="true" />
          </button>)}
        </div>
        <button className="goal-add-button" type="button" onClick={startAddingEvent} disabled={disabled || busy}><Plus size={16} />ライフイベントを追加</button>
      </section>
      </>}
      {mode === "goals" && addingGoal && (
        <section className="section-panel goal-add-form">
          <div className="panel-heading"><button type="button" className="detail-back" aria-label="目標一覧へ戻る" onClick={() => { setAddingGoal(false); setGoal(emptyGoal); }}><ArrowLeft size={17} /></button><h2>資産形成目標を追加</h2></div>
          <GoalFields goal={goal} onChange={setGoal} onSubmit={submitGoal} onCancel={() => { setAddingGoal(false); setGoal(emptyGoal); }} disabled={disabled} busy={busy} submitLabel="登録する" />
        </section>
      )}
      {mode === "goals" && selectedEvent && (
        <section className="section-panel event-detail">
          <div className="goal-detail-heading">
            <button type="button" className="detail-back" aria-label="目標一覧へ戻る" onClick={() => { setSelectedEventId(null); setEditingEvent(null); }}><ArrowLeft size={17} /></button>
            <div><span className={`event-type ${selectedEvent.entryType}`}>{selectedEvent.entryType === "fact" ? "入力した事実" : "仮定・予測"}</span><h2>{selectedEvent.title}</h2></div>
          </div>
          {editingEvent === selectedEvent.id ? (
            <LifeEventFields event={event} onChange={setEvent} onSubmit={submitEvent} onCancel={() => { setEditingEvent(null); setEvent(emptyEvent); }} disabled={disabled} busy={busy} submitLabel="更新する" />
          ) : (
            <>
              <dl className="goal-detail-grid">
                <div><dt>予定年月</dt><dd>{selectedEvent.eventMonth}</dd></div>
                <div><dt>カテゴリ</dt><dd>{lifeCategoryLabel(selectedEvent.category)}</dd></div>
                <div><dt>金額の見込み</dt><dd>{selectedEvent.estimatedAmount == null ? "未設定" : money.format(selectedEvent.estimatedAmount)}</dd></div>
                <div className="profile-priorities"><dt>メモ</dt><dd>{selectedEvent.note?.trim() || "未設定"}</dd></div>
              </dl>
              <div className="form-actions goal-detail-actions">
                <button className="primary-action" type="button" onClick={() => editEvent(selectedEvent)} disabled={disabled || busy}><Pencil size={15} />編集</button>
                <button className="cancel-action" type="button" onClick={() => void runAction(async () => { await removeLifeEvent(token, selectedEvent.id); setSelectedEventId(null); }, "ライフイベントを削除しました。")} disabled={disabled || busy}><Trash2 size={15} />削除</button>
              </div>
            </>
          )}
        </section>
      )}
      {mode === "goals" && addingEvent && (
        <section className="section-panel event-add-page">
          <div className="panel-heading"><button type="button" className="detail-back" aria-label="目標一覧へ戻る" onClick={() => { setAddingEvent(false); setEvent(emptyEvent); }}><ArrowLeft size={17} /></button><h2>ライフイベントを追加</h2></div>
          <LifeEventFields event={event} onChange={setEvent} onSubmit={submitEvent} onCancel={() => { setAddingEvent(false); setEvent(emptyEvent); }} disabled={disabled} busy={busy} submitLabel="登録する" />
        </section>
      )}
      </>}
      {message && <p className={`planning-message ${error ? "error" : "success"}`} role="status">{message}</p>}
    </div>
  );
}

function GoalFields({ goal, onChange, onSubmit, onCancel, disabled, busy, submitLabel }: {
  goal: GoalInput;
  onChange: (goal: GoalInput) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onCancel: () => void;
  disabled: boolean;
  busy: boolean;
  submitLabel: string;
}) {
  return (
    <form className="planning-form" onSubmit={onSubmit}>
      <label>目標の種類<select value={goal.type} disabled={disabled || busy} onChange={(event) => onChange({ ...goal, type: event.currentTarget.value as GoalInput["type"] })}><option value="savings">貯金</option><option value="asset_building">資産形成</option></select></label>
      <label>目標名<input required maxLength={120} value={goal.name} disabled={disabled || busy} onChange={(event) => onChange({ ...goal, name: event.currentTarget.value })} placeholder="例：生活防衛資金" /></label>
      <label>目標金額（円）<input required type="number" min="1" step="1" value={goal.targetAmount || ""} disabled={disabled || busy} onChange={(event) => onChange({ ...goal, targetAmount: Number(event.currentTarget.value) })} /></label>
      <label>現在額（円）<input required type="number" min="0" step="1" value={goal.currentAmount} disabled={disabled || busy} onChange={(event) => onChange({ ...goal, currentAmount: Number(event.currentTarget.value) })} /></label>
      <label>目標期限<input type="month" required value={goal.targetMonth ?? ""} disabled={disabled || busy} onChange={(event) => onChange({ ...goal, targetMonth: event.currentTarget.value })} /></label>
      <label>月々の積立予定額（円）<input type="number" min="0" step="1" placeholder="未設定" value={goal.monthlyContribution ?? ""} disabled={disabled || busy} onChange={(event) => onChange({ ...goal, monthlyContribution: optionalNumber(event.currentTarget.value) })} /></label>
      <label>状態<select value={goal.status} disabled={disabled || busy} onChange={(event) => onChange({ ...goal, status: event.currentTarget.value as GoalInput["status"] })}><option value="active">進行中</option><option value="paused">一時停止</option><option value="completed">達成</option></select></label>
      <div className="form-actions span-all">
        <button className="primary-action" type="submit" disabled={disabled || busy}>{busy ? "保存中..." : submitLabel}<Check size={15} /></button>
        <button className="cancel-action" type="button" onClick={onCancel} disabled={busy}><X size={14} />キャンセル</button>
      </div>
    </form>
  );
}

function LifeEventFields({ event, onChange, onSubmit, onCancel, disabled, busy, submitLabel }: {
  event: LifeEventInput;
  onChange: (event: LifeEventInput) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onCancel: () => void;
  disabled: boolean;
  busy: boolean;
  submitLabel: string;
}) {
  return (
    <form className="planning-form" onSubmit={onSubmit}>
      <label>イベント名<input required maxLength={120} value={event.title} disabled={disabled || busy} onChange={(change) => onChange({ ...event, title: change.currentTarget.value })} placeholder="例：転居" /></label>
      <label>カテゴリ<select value={event.category} disabled={disabled || busy} onChange={(change) => onChange({ ...event, category: change.currentTarget.value })}><option value="housing">住居</option><option value="vehicle">車・移動</option><option value="education">教育</option><option value="family_support">家族への支援</option><option value="career">仕事・収入</option><option value="large_purchase">大きな支出</option><option value="other">その他</option></select></label>
      <label>予定年月<input required type="month" value={event.eventMonth} disabled={disabled || busy} onChange={(change) => onChange({ ...event, eventMonth: change.currentTarget.value })} /></label>
      <label>金額の見込み（円）<input type="number" min="0" step="1" placeholder="未設定" value={event.estimatedAmount ?? ""} disabled={disabled || busy} onChange={(change) => onChange({ ...event, estimatedAmount: optionalNumber(change.currentTarget.value) })} /></label>
      <label>情報の区分<select value={event.entryType} disabled={disabled || busy} onChange={(change) => onChange({ ...event, entryType: change.currentTarget.value as LifeEventInput["entryType"] })}><option value="fact">本人が入力した事実・予定</option><option value="assumption">仮定・予測</option></select></label>
      <label>メモ<textarea maxLength={1000} rows={2} value={event.note ?? ""} disabled={disabled || busy} onChange={(change) => onChange({ ...event, note: change.currentTarget.value })} /></label>
      <div className="form-actions span-all">
        <button className="primary-action" type="submit" disabled={disabled || busy}>{busy ? "保存中..." : submitLabel}<Check size={15} /></button>
        <button className="cancel-action" type="button" onClick={onCancel} disabled={busy}><X size={14} />キャンセル</button>
      </div>
    </form>
  );
}
