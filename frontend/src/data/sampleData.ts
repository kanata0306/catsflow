export const sampleHousehold = {
  income: 320000,
  expense: 238400,
  budget: 250000,
  savingsRate: 25.5,
  categories: [
    { name: "住居費", amount: 78000, color: "green" },
    { name: "食費", amount: 54200, color: "blue" },
    { name: "日用品", amount: 28600, color: "amber" },
    { name: "交通・通信", amount: 22400, color: "slate" },
    { name: "その他", amount: 55200, color: "rose" },
  ],
  fixedCosts: [
    { name: "住居費", amount: 78000, note: "毎月" },
    { name: "通信費", amount: 6800, note: "毎月" },
    { name: "返済", amount: null, note: "未登録" },
  ],
};

export const sampleGoals = [
  {
    type: "貯金",
    name: "生活防衛資金",
    current: 420000,
    target: 600000,
    deadline: "2027-03",
    monthly: 30000,
    color: "green",
  },
  {
    type: "資産形成",
    name: "将来のための積立",
    current: 210000,
    target: 500000,
    deadline: "2028-03",
    monthly: 20000,
    color: "blue",
  },
];

export const sampleInvestments = [
  { name: "全世界株式インデックス", monthly: 20000, status: "積立中" },
  { name: "国内株式インデックス", monthly: 10000, status: "積立中" },
];

export const sampleLifeEvents = [
  { year: "2027", title: "住み替え", category: "住居", amount: 300000, source: "入力例" },
  { year: "2028", title: "車の購入", category: "大きな支出", amount: 1200000, source: "入力例" },
];