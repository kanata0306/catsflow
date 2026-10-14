INSERT INTO users (id, name, email) VALUES
(1, '田中 太郎', 'tanaka@example.com'),
(2, '田中 次郎', 'tanaka2@example.com');

INSERT INTO categories (id, user_id, name, type) VALUES
(1, 1, '給与', 'income'),
(2, 1, '食費', 'expense'),
(3, 1, '交通費', 'expense'),
(4, 1, '外食', 'expense'),
(5, 1, '水道光熱費', 'expense'),
(6, 2, '給与', 'income'),
(7, 2, '外食', 'expense');

INSERT INTO accounts (id, user_id, name, type) VALUES
(1, 1, 'みずほ銀行', '銀行口座'),
(2, 1, '現金', '現金'),
(3, 2, '現金', '現金');

INSERT INTO transactions (transaction_date, amount, type, memo, user_id, category_id, account_id) VALUES
('2024-01-01', 250000, 'income', '1月給与', 1, 1, 1),
('2024-01-05', -3500, 'expense', 'スーパーで買い物', 1, 2, 1),
('2024-01-10', -800, 'expense', '電車代', 1, 3, 1),
('2024-01-15', -2500, 'expense', '外食', 1, 4, 1),
('2024-01-20', -12000, 'expense', '電気・ガス・水道料金', 1, 5, 1),
('2024-01-25', 50000, 'income', '副業収入', 2, 6, 3),
('2024-01-27', -600, 'expense', 'カフェでコーヒー', 2, 7, 3);