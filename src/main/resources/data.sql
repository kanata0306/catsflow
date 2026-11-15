INSERT INTO users (id, name, email) VALUES
(1, '田中 太郎', 'tanaka@example.com'),
(2, '田中 次郎', 'tanaka2@example.com');
ALTER TABLE users ALTER COLUMN id RESTART WITH 3;

INSERT INTO categories (id, user_id, name, type) VALUES
(1, 1, '給与', 'income'),
(2, 1, '食費', 'expense'),
(3, 1, '交通費', 'expense'),
(4, 1, '外食', 'expense'),
(5, 1, '水道光熱費', 'expense'),
(6, 2, '給与', 'income'),
(7, 2, '外食', 'expense');
ALTER TABLE categories ALTER COLUMN id RESTART WITH 8;

INSERT INTO accounts (id, user_id, name, type) VALUES
(1, 1, 'みずほ銀行', '銀行口座'),
(2, 1, '現金', '現金'),
(3, 2, '現金', '現金');
ALTER TABLE accounts ALTER COLUMN id RESTART WITH 4;

INSERT INTO transactions (transaction_date, amount, type, memo, user_id, category_id, account_id) VALUES
('2024-01-01', 250000, 'income', '1月給与', 1, 1, 1),
('2024-01-05', -3500, 'expense', 'スーパーで買い物', 1, 2, 1),
('2024-01-10', -800, 'expense', '電車代', 1, 3, 1),
('2024-01-15', -2500, 'expense', '外食', 1, 4, 1),
('2024-01-20', -12000, 'expense', '電気・ガス・水道料金', 1, 5, 1),
('2024-01-25', 50000, 'income', '副業収入', 2, 6, 3),
('2024-01-27', -600, 'expense', 'カフェでコーヒー', 2, 7, 3);

-- Development-only form samples. Select the corresponding seeded user to view them.
INSERT INTO financial_profiles (user_id, housing_type, monthly_living_cost, income_stability,
	monthly_family_support, priorities) VALUES
(1, 'living_with_family', 45000, 'stable', 20000, '生活防衛資金を確保してから積立を増やす'),
(2, 'renting', 92000, 'variable', NULL, '');

INSERT INTO long_term_liabilities (user_id, name, liability_type, balance, as_of_date,
	annual_interest_rate, monthly_payment, payment_day, note) VALUES
(1, '奨学金', 'student_loan', 820000, CURRENT_DATE, 0.6500, 12000, 27, '開発用サンプル'),
(2, '住宅ローン', 'mortgage', 18500000, CURRENT_DATE, 1.2000, 72000, 25, '開発用サンプル');

INSERT INTO financial_goals (user_id, goal_type, name, target_amount, current_amount,
	target_month, monthly_contribution, status) VALUES
(1, 'savings', '生活防衛資金', 600000, 240000, '2027-12', 30000, 'active'),
(1, 'asset_building', '将来資金', 3000000, 380000, '2030-03', 25000, 'active'),
(2, 'savings', '引越し資金', 500000, 125000, '2027-06', 25000, 'active');

INSERT INTO life_plan_events (user_id, title, category, event_month, estimated_amount,
	entry_type, note) VALUES
(1, '住み替えを検討', 'housing', '2027-10', 300000, 'fact', '本人入力の開発用サンプル'),
(1, '車の買い替え想定', 'large_purchase', '2029-04', 1200000, 'assumption', '仮定の開発用サンプル'),
(2, '資格取得', 'education', '2027-03', 180000, 'fact', '本人入力の開発用サンプル');