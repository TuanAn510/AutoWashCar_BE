CREATE TABLE users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  full_name VARCHAR(120) NOT NULL,
  phone VARCHAR(20) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(20) NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  UNIQUE KEY uk_users_phone (phone),
  CONSTRAINT chk_users_role CHECK (role IN ('ROLE_CUSTOMER', 'ROLE_ADMIN')),
  CONSTRAINT chk_users_phone_digits CHECK (phone REGEXP '^[0-9]{9,15}$')
);

CREATE TABLE vehicles (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id BIGINT NOT NULL,
  license_plate VARCHAR(20) NOT NULL,
  brand VARCHAR(80) NOT NULL,
  model VARCHAR(80) NOT NULL,
  color VARCHAR(40),
  manufacture_year INT,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_vehicles_customer FOREIGN KEY (customer_id) REFERENCES users(id),
  UNIQUE KEY uk_vehicles_license_plate (license_plate),
  KEY idx_vehicles_customer (customer_id),
  CONSTRAINT chk_vehicles_year CHECK (manufacture_year IS NULL OR manufacture_year BETWEEN 1980 AND 2100)
);

CREATE TABLE service_categories (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  description TEXT,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  UNIQUE KEY uk_service_categories_name (name)
);

CREATE TABLE services (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  category_id BIGINT NOT NULL,
  name VARCHAR(120) NOT NULL,
  description TEXT,
  price DECIMAL(12,2) NOT NULL,
  duration_minutes INT NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_services_category FOREIGN KEY (category_id) REFERENCES service_categories(id),
  UNIQUE KEY uk_services_category_name (category_id, name),
  KEY idx_services_active (is_active),
  CONSTRAINT chk_services_price CHECK (price >= 0),
  CONSTRAINT chk_services_duration CHECK (duration_minutes > 0)
);

CREATE TABLE membership_tiers (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(80) NOT NULL,
  min_points INT NOT NULL,
  discount_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
  booking_window_days INT NOT NULL,
  priority_level INT NOT NULL,
  description TEXT,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  UNIQUE KEY uk_membership_tiers_name (name),
  UNIQUE KEY uk_membership_tiers_min_points_unique (min_points),
  KEY idx_membership_tiers_min_points (min_points),
  CONSTRAINT chk_membership_tiers_points CHECK (min_points >= 0),
  CONSTRAINT chk_membership_tiers_discount CHECK (discount_percent BETWEEN 0 AND 100),
  CONSTRAINT chk_membership_tiers_booking_window CHECK (booking_window_days > 0),
  CONSTRAINT chk_membership_tiers_priority CHECK (priority_level >= 0)
);

CREATE TABLE loyalty_accounts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id BIGINT NOT NULL,
  current_points INT NOT NULL DEFAULT 0,
  lifetime_points INT NOT NULL DEFAULT 0,
  total_spending DECIMAL(14,2) NOT NULL DEFAULT 0,
  visit_count INT NOT NULL DEFAULT 0,
  membership_tier_id BIGINT,
  last_reviewed_at DATETIME(6),
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_loyalty_accounts_customer FOREIGN KEY (customer_id) REFERENCES users(id),
  CONSTRAINT fk_loyalty_accounts_tier FOREIGN KEY (membership_tier_id) REFERENCES membership_tiers(id),
  UNIQUE KEY uk_loyalty_accounts_customer (customer_id),
  CONSTRAINT chk_loyalty_accounts_points CHECK (current_points >= 0 AND lifetime_points >= 0),
  CONSTRAINT chk_loyalty_accounts_spending CHECK (total_spending >= 0),
  CONSTRAINT chk_loyalty_accounts_visits CHECK (visit_count >= 0)
);

CREATE TABLE promotions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(40) NOT NULL,
  title VARCHAR(160) NOT NULL,
  description TEXT,
  discount_type VARCHAR(30) NOT NULL,
  discount_value DECIMAL(12,2) NOT NULL,
  target_tier_id BIGINT,
  start_at DATETIME(6) NOT NULL,
  end_at DATETIME(6) NOT NULL,
  usage_limit INT,
  used_count INT NOT NULL DEFAULT 0,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_promotions_target_tier FOREIGN KEY (target_tier_id) REFERENCES membership_tiers(id),
  UNIQUE KEY uk_promotions_code (code),
  KEY idx_promotions_active_dates (is_active, start_at, end_at),
  CONSTRAINT chk_promotions_discount_type CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT')),
  CONSTRAINT chk_promotions_discount_value CHECK (discount_value >= 0),
  CONSTRAINT chk_promotions_dates CHECK (end_at > start_at),
  CONSTRAINT chk_promotions_usage CHECK (usage_limit IS NULL OR usage_limit > 0),
  CONSTRAINT chk_promotions_used_count CHECK (used_count >= 0)
);

CREATE TABLE rewards (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(160) NOT NULL,
  description TEXT,
  required_points INT NOT NULL,
  reward_type VARCHAR(30) NOT NULL,
  discount_amount DECIMAL(12,2),
  add_on_service_id BIGINT,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_rewards_add_on_service FOREIGN KEY (add_on_service_id) REFERENCES services(id),
  CONSTRAINT chk_rewards_required_points CHECK (required_points > 0),
  CONSTRAINT chk_rewards_type CHECK (reward_type IN ('DISCOUNT_CODE', 'FREE_WASH', 'ADD_ON')),
  CONSTRAINT chk_rewards_discount CHECK (discount_amount IS NULL OR discount_amount >= 0)
);

CREATE TABLE reward_redemptions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id BIGINT NOT NULL,
  reward_id BIGINT NOT NULL,
  code VARCHAR(40) NOT NULL,
  points_used INT NOT NULL,
  status VARCHAR(30) NOT NULL,
  redeemed_at DATETIME(6) NOT NULL,
  used_at DATETIME(6),
  expires_at DATETIME(6),
  CONSTRAINT fk_reward_redemptions_customer FOREIGN KEY (customer_id) REFERENCES users(id),
  CONSTRAINT fk_reward_redemptions_reward FOREIGN KEY (reward_id) REFERENCES rewards(id),
  UNIQUE KEY uk_reward_redemptions_code (code),
  KEY idx_reward_redemptions_customer (customer_id, redeemed_at),
  CONSTRAINT chk_reward_redemptions_points CHECK (points_used > 0),
  CONSTRAINT chk_reward_redemptions_status CHECK (status IN ('AVAILABLE', 'USED', 'EXPIRED'))
);

CREATE TABLE bookings (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id BIGINT NOT NULL,
  vehicle_id BIGINT NOT NULL,
  scheduled_at DATETIME(6) NOT NULL,
  status VARCHAR(30) NOT NULL,
  subtotal_amount DECIMAL(12,2) NOT NULL,
  discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  final_amount DECIMAL(12,2) NOT NULL,
  earned_points INT NOT NULL DEFAULT 0,
  promotion_id BIGINT,
  reward_redemption_id BIGINT,
  note TEXT,
  completed_at DATETIME(6),
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_bookings_customer FOREIGN KEY (customer_id) REFERENCES users(id),
  CONSTRAINT fk_bookings_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(id),
  CONSTRAINT fk_bookings_promotion FOREIGN KEY (promotion_id) REFERENCES promotions(id),
  CONSTRAINT fk_bookings_redemption FOREIGN KEY (reward_redemption_id) REFERENCES reward_redemptions(id),
  UNIQUE KEY uk_bookings_scheduled_at (scheduled_at),
  KEY idx_bookings_customer_schedule (customer_id, scheduled_at),
  KEY idx_bookings_vehicle_schedule (vehicle_id, scheduled_at),
  KEY idx_bookings_schedule_status (scheduled_at, status),
  CONSTRAINT chk_bookings_status CHECK (status IN ('PENDING', 'CONFIRMED', 'IN_QUEUE', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
  CONSTRAINT chk_bookings_amounts CHECK (subtotal_amount >= 0 AND discount_amount >= 0 AND final_amount >= 0),
  CONSTRAINT chk_bookings_discount_lte_subtotal CHECK (discount_amount <= subtotal_amount),
  CONSTRAINT chk_bookings_points CHECK (earned_points >= 0)
);

CREATE TABLE booking_services (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  booking_id BIGINT NOT NULL,
  service_id BIGINT NOT NULL,
  service_name VARCHAR(120) NOT NULL,
  price DECIMAL(12,2) NOT NULL,
  duration_minutes INT NOT NULL,
  CONSTRAINT fk_booking_services_booking FOREIGN KEY (booking_id) REFERENCES bookings(id),
  CONSTRAINT fk_booking_services_service FOREIGN KEY (service_id) REFERENCES services(id),
  UNIQUE KEY uk_booking_services_booking_service (booking_id, service_id),
  CONSTRAINT chk_booking_services_price CHECK (price >= 0),
  CONSTRAINT chk_booking_services_duration CHECK (duration_minutes > 0)
);

CREATE TABLE loyalty_transactions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id BIGINT NOT NULL,
  booking_id BIGINT,
  redemption_id BIGINT,
  type VARCHAR(30) NOT NULL,
  points INT NOT NULL,
  description TEXT,
  expires_at DATETIME(6),
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_loyalty_transactions_customer FOREIGN KEY (customer_id) REFERENCES users(id),
  CONSTRAINT fk_loyalty_transactions_booking FOREIGN KEY (booking_id) REFERENCES bookings(id),
  CONSTRAINT fk_loyalty_transactions_redemption FOREIGN KEY (redemption_id) REFERENCES reward_redemptions(id),
  KEY idx_loyalty_transactions_customer_created (customer_id, created_at),
  CONSTRAINT chk_loyalty_transactions_type CHECK (type IN ('EARN', 'REDEEM', 'EXPIRE', 'ADJUST'))
);

CREATE TABLE survey_event_logs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_key VARCHAR(80) NOT NULL,
  user_id BIGINT,
  booking_id BIGINT,
  event_type VARCHAR(40) NOT NULL,
  page VARCHAR(120),
  action VARCHAR(120),
  metadata_json TEXT,
  ip_address VARCHAR(64),
  user_agent VARCHAR(500),
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_survey_event_logs_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_survey_event_logs_booking FOREIGN KEY (booking_id) REFERENCES bookings(id),
  KEY idx_survey_event_logs_session_created (session_key, created_at),
  KEY idx_survey_event_logs_event_created (event_type, created_at),
  CONSTRAINT chk_survey_event_logs_event_type CHECK (event_type IN ('PAGE_VIEW', 'FORM_START', 'FORM_SUBMIT', 'BOOKING_CREATED', 'LOGIN', 'REGISTER', 'CLICK'))
);
